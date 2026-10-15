package com.swordfish.lemuroid.app.shared.cheats

import java.security.SecureRandom

/** Copy-on-write boxed gifts. Does not change the party, wallet, or existing box records. */
object EmeraldQuickSavePatcher {
    data class Resources(val money: Long, val masterBalls: Int, val hasBallSpace: Boolean)
    data class Gift(val bytes: ByteArray, val box: Int, val position: Int)
    private val lengths = intArrayOf(3884,3968,3968,3968,3848,3968,3968,3968,3968,3968,3968,3968,3968,2000)
    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int) = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
    private fun put16(b: ByteArray, p: Int, n: Int) { b[p] = n.toByte(); b[p + 1] = (n ushr 8).toByte() }
    private fun put32(b: ByteArray, p: Int, n: Long) { repeat(4) { b[p + it] = (n ushr (it * 8)).toByte() } }
    private fun sections(b: ByteArray): IntArray {
        val slot = EmeraldSaveInspector.inspect(b).activeSlot
        val result = IntArray(14)
        repeat(14) { val p = slot * 57344 + it * 4096; result[u16(b, p + 0xFF4)] = p }
        return result
    }
    fun resources(b: ByteArray): Resources {
        val offsets = sections(b)
        val key = u32(b, offsets[0] + 0xAC)
        val money = u32(b, offsets[1] + 0x490) xor key
        require(money <= 999999) { "金钱结构不匹配，已停止操作" }
        var balls = 0
        var empty = false
        repeat(16) {
            val p = offsets[1] + 0x650 + it * 4
            val id = u16(b, p)
            val quantity = u16(b, p + 2) xor (key.toInt() and 65535)
            require(id in 0..12 && (id == 0 || quantity in 1..99)) { "精灵球口袋结构不匹配" }
            if (id == 0) empty = true
            if (id == 1) balls += quantity
        }
        return Resources(money, balls, empty || balls > 0)
    }
    private val permutations = buildList<List<Int>> {
        fun permute(prefix: List<Int>, rest: List<Int>) {
            if (rest.isEmpty()) add(prefix) else rest.forEach { permute(prefix + it, rest - it) }
        }
        permute(emptyList(), listOf(0,1,2,3))
    }
    /** Generation III: nature = PID % 25; shiny when TID xor SID xor PID halves < 8. */
    fun shinyFor(trainer: Long, personality: Long): Boolean {
        require(trainer in 0..0xFFFFFFFFL && personality in 0..0xFFFFFFFFL)
        val value = (trainer.toInt() and 0xFFFF) xor
            ((trainer.toInt() ushr 16) and 0xFFFF) xor
            (personality.toInt() and 0xFFFF) xor
            ((personality.toInt() ushr 16) and 0xFFFF)
        return value < 8
    }

    /** Generate a valid nature/shiny PID for the save owner's trainer ID; never edit existing team data. */
    fun personalityFor(
        input: ByteArray,
        nature: Int?,
        shiny: Boolean,
        random: SecureRandom = SecureRandom(),
    ): Long {
        require(nature == null || nature in 0..24) { "性格编号必须在0～24范围" }
        val trainer = u32(input, sections(input)[0] + 0xA)
        repeat(10_000) {
            val candidate = if (shiny) {
                val high = random.nextInt(65536)
                val target = random.nextInt(8)
                val low = high xor (trainer.toInt() and 0xFFFF) xor
                    ((trainer.toInt() ushr 16) and 0xFFFF) xor target
                (high.toLong() shl 16) or low.toLong()
            } else {
                random.nextInt().toLong() and 0xFFFFFFFFL
            }
            if ((nature == null || candidate % 25 == nature.toLong()) &&
                shinyFor(trainer, candidate) == shiny
            ) {
                return candidate
            }
        }
        error("无法生成符合性格与闪光要求的精灵，请重试")
    }

    fun addToFirstEmptyBox(
        input: ByteArray,
        species: Int,
        nickname: ByteArray,
        pp: Int,
        personality: Long,
        experience: Long = 0,
        ivs: List<Int> = List(6) { 31 },
        evs: List<Int> = List(6) { 0 },
    ): Gift {
        require(species in 1..251 || species in 277..411) { "不支持的精灵内部编号" }
        require(nickname.size == 11 && nickname[0] != 0xFF.toByte()) { "精灵名称读取失败" }
        require(pp in 1..64 && personality in 0..0xFFFFFFFFL)
        require(experience in 0..0xFFFFFFFFL) { "经验值超出存档格式范围" }
        require(ivs.size == 6 && ivs.all { it in 0..31 }) { "IV必须为6项，每项0～31" }
        require(evs.size == 6 && evs.all { it in 0..255 } && evs.sum() <= 510) {
            "EV必须为6项，每项0～255，总和不超过510"
        }
        val offsets = sections(input)
        val storage = ByteArray(33744)
        var copied = 0
        for (id in 5..13) {
            input.copyInto(storage, copied, offsets[id], offsets[id] + lengths[id])
            copied += lengths[id]
        }
        require((storage[0].toInt() and 255) in 0..13) { "电脑盒子结构不匹配" }
        val index = (0 until 420).firstOrNull { slot ->
            (0 until 80).all { storage[4 + slot * 80 + it] == 0.toByte() }
        } ?: error("电脑盒子已满，未替换任何精灵")
        val record = ByteArray(80)
        val trainer = u32(input, offsets[0] + 0xA)
        put32(record, 0, personality)
        put32(record, 4, trainer)
        nickname.copyInto(record, 8, 0, 10)
        val partyCount = EmeraldSaveInspector.inspect(input).partyCount
        record[18] = if (partyCount > 0) input[offsets[1] + 0x238 + 18] else 2
        record[19] = 2 // hasSpecies; not an egg or bad egg
        input.copyInto(record, 20, offsets[0], offsets[0] + 7)
        val plain = ByteArray(48)
        val order = permutations[(personality % 24).toInt()]
        val growth = order.indexOf(0) * 12
        val attacks = order.indexOf(1) * 12
        val misc = order.indexOf(3) * 12
        val effort = order.indexOf(2) * 12
        put16(plain, growth, species)
        put32(plain, growth + 4, experience) // level is derived from experience on withdrawal
        plain[growth + 9] = 70
        put16(plain, attacks, 33) // Tackle; PP comes from this ROM's move table
        plain[attacks + 8] = pp.toByte()
        repeat(6) { plain[effort + it] = evs[it].toByte() }
        plain[misc + 1] = 0xFF.toByte()
        put16(plain, misc + 2, 1 or (3 shl 7) or (4 shl 11))
        val packedIvs = ivs.foldIndexed(0L) { index, packed, iv ->
            packed or (iv.toLong() shl (index * 5))
        }
        put32(plain, misc + 4, packedIvs) // keep egg and ability flags cleared
        put16(record, 28, (0 until 24).sumOf { u16(plain, it * 2) } and 65535)
        repeat(12) { put32(record, 32 + it * 4, u32(plain, it * 4) xor personality xor trainer) }
        record.copyInto(storage, 4 + index * 80)
        val output = input.copyOf()
        copied = 0
        for (id in 5..13) {
            val p = offsets[id]
            storage.copyInto(output, p, copied, copied + lengths[id])
            copied += lengths[id]
            var sum = 0L
            for (i in 0 until lengths[id] step 4) sum = (sum + u32(output, p + i)) and 0xFFFFFFFFL
            put16(output, p + 0xFF6, ((sum + (sum ushr 16)) and 65535).toInt())
        }
        EmeraldSaveInspector.inspect(output)
        return Gift(output, index / 30 + 1, index % 30 + 1)
    }
}
