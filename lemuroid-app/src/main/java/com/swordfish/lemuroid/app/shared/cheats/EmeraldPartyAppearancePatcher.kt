package com.swordfish.lemuroid.app.shared.cheats

import java.security.SecureRandom

/**
 * Edit ONLY one on-hand Pokémon, keeping species, gender, nature, IV/EV and other records intact.
 * Every result is copy-on-write and checked by the Emerald save parser.
 */
object EmeraldPartyAppearancePatcher {
    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int) = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
    private fun put16(b: ByteArray, p: Int, n: Int) { b[p] = n.toByte(); b[p + 1] = (n ushr 8).toByte() }
    private fun put32(b: ByteArray, p: Int, n: Long) { repeat(4) { b[p + it] = (n ushr (it * 8)).toByte() } }
    private val orders = buildList<List<Int>> {
        fun each(prefix: List<Int>, remain: List<Int>) {
            if (remain.isEmpty()) add(prefix) else remain.forEach { each(prefix + it, remain - it) }
        }
        each(emptyList(), listOf(0, 1, 2, 3))
    }

    private fun genderKey(pid: Long, ratio: Int): Int = when (ratio) {
        0 -> 0
        254 -> 1
        255 -> 2
        else -> if ((pid and 255).toInt() < ratio) 1 else 0
    }

    fun preview(
        input: ByteArray,
        position: Int,
        shiny: Boolean? = null,
        abilitySlot: Int? = null,
        genderRatio: Int,
        ability1: Int,
        ability2: Int,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val before = EmeraldSaveInspector.inspect(input)
        require(position in 1..before.partyCount) { "请选择队内精灵" }
        require(shiny != null || abilitySlot != null) { "未指定修改内容" }
        require(abilitySlot == null || abilitySlot in 0..1) { "特性槽位必须为1或2" }
        require(ability1 != 0) { "ROM的特性数据不匹配" }
        require(abilitySlot != 1 || (ability2 != 0 && ability2 != ability1)) {
            "该精灵没有第二种不同的特性"
        }
        require(genderRatio in 0..255)
        val section = (0 until 14).map { before.activeSlot * 57344 + it * 4096 }
            .first { u16(input, it + 0xff4) == 1 }
        val start = section + 0x238 + (position - 1) * 100
        val oldPid = u32(input, start)
        val trainer = u32(input, start + 4)
        val oldKey = oldPid xor trainer
        var pid = oldPid
        if (shiny != null && shiny != EmeraldQuickSavePatcher.shinyFor(trainer, oldPid)) {
            val species = before.partySpecies[position - 1]
            require(species != 201 && species != 327) {
                "未知图腾及晃晃斑的形态/花纹与PID相关，暂不支持闪光切换"
            }
            val tidXor = ((trainer and 65535) xor (trainer ushr 16)).toInt()
            val nature = oldPid % 25
            val order = oldPid % 24
            val gender = genderKey(oldPid, genderRatio)
            var result: Long? = null
            for (attempt in 0 until 600_000) {
                val low = random.nextInt(65536)
                if (genderKey(low.toLong(), genderRatio) != gender) continue
                val high = if (shiny) low xor tidXor xor random.nextInt(8)
                    else random.nextInt(65536)
                val candidate = (high.toLong() shl 16) or low.toLong()
                if (candidate % 25 != nature || candidate % 24 != order) continue
                if (EmeraldQuickSavePatcher.shinyFor(trainer, candidate) != shiny) continue
                result = candidate
                break
            }
            pid = requireNotNull(result) { "找不到同时保持原性格、性别和数据排列的闪光编码，请重试" }
        }

        val plain = ByteArray(48)
        for (word in 0 until 12) put32(plain, word * 4, u32(input, start + 32 + word * 4) xor oldKey)
        val misc = orders[(oldPid % 24).toInt()].indexOf(3) * 12 + 4
        val bits = u32(plain, misc)
        if (abilitySlot != null) {
            val newBits = (bits and 0x7fffffffL) or (abilitySlot.toLong() shl 31)
            put32(plain, misc, newBits)
        }
        val output = input.copyOf()
        put32(output, start, pid)
        val newKey = pid xor trainer
        for (word in 0 until 12) put32(output, start + 32 + word * 4, u32(plain, word * 4) xor newKey)
        put16(output, start + 28, (0 until 24).sumOf { u16(plain, it * 2) } and 65535)
        var sum = 0L
        for (i in 0 until 3968 step 4) sum = (sum + u32(output, section + i)) and 0xffffffffL
        put16(output, section + 0xff6, ((sum + (sum ushr 16)) and 65535).toInt())
        val after = EmeraldSaveInspector.inspect(output)
        check(after.partyCount == before.partyCount && after.partySpecies == before.partySpecies)
        check(after.partyLevels == before.partyLevels)
        check(after.partyIvs == before.partyIvs && after.partyEvs == before.partyEvs)
        if (shiny != null) check(EmeraldQuickSavePatcher.shinyFor(trainer, pid) == shiny)
        if (abilitySlot != null) check(((u32(plain, misc) ushr 31) and 1).toInt() == abilitySlot)
        check(pid % 25 == oldPid % 25 && pid % 24 == oldPid % 24)
        check(genderKey(pid, genderRatio) == genderKey(oldPid, genderRatio))
        return output
    }
}
