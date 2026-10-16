package com.swordfish.lemuroid.app.shared.cheats

/**
 * Builds a copy of a save with the PID nature of exactly one party Pokémon changed.
 * This deliberately does NOT expose a UI yet: a party stat recalculation step is
 * required before it can safely be applied in-game.
 */
object EmeraldPartyNatureDraft {
    private fun u16(b: ByteArray, p: Int): Int = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int): Long = (u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)) and 0xffffffffL
    private fun put32(b: ByteArray, p: Int, value: Long) {
        repeat(4) { b[p + it] = (value ushr (8 * it)).toByte() }
    }

    /**
     * A new PID must keep modulo 24 (same encrypted substructure order), modulo 2
     * (same ability bit), and the original shiny status. Returns null if no
     * candidate can be found safely within the bounded search.
     */
    fun matchingPid(oldPid: Long, trainer: Long, nature: Int): Long? {
        require(oldPid in 0..0xffffffffL && trainer in 0..0xffffffffL && nature in 0..24)
        if (oldPid % 25 == nature.toLong()) return oldPid
        val shiny = EmeraldQuickSavePatcher.shinyFor(trainer, oldPid)
        val tidXor = (trainer.toInt() and 65535) xor (trainer.toInt() ushr 16)
        // Iterate low halves deterministically. Shiny candidates have just eight
        // possible high halves per low half; ordinary candidates use a dispersed high.
        for (low in 0..65535) {
            val start = if (shiny) 0 else 0
            val end = if (shiny) 7 else 63
            for (variant in start..end) {
                val high = if (shiny) low xor tidXor xor variant else (low * 40503 + variant * 1009) and 65535
                val pid = (high.toLong() shl 16) or low.toLong()
                if (pid % 24 == oldPid % 24 && pid % 25 == nature.toLong() &&
                    EmeraldQuickSavePatcher.shinyFor(trainer, pid) == shiny) return pid
            }
        }
        return null
    }

    /**
     * Offline prototype only. Re-key the same encrypted 48 bytes when PID changes.
     * Since PID modulo 24 is unchanged, its substructure layout stays intact.
     * This DOES NOT recalculate derived in-party stats: never wire to the live
     * save editor until runtime stat refresh is verified.
     */
    fun previewPatchedSave(input: ByteArray, position: Int, nature: Int): ByteArray {
        val save = EmeraldSaveInspector.inspect(input)
        require(position in 1..save.partyCount) { "只能编辑当前队伍成员" }
        val base = (0 until 14).map { save.activeSlot * 57344 + it * 4096 }
            .first { u16(input, it + 0xff4) == 1 }
        val start = base + 0x238 + (position - 1) * 100
        val oldPid = u32(input, start)
        val trainer = u32(input, start + 4)
        val newPid = previewPid(input, position, nature)
        val output = input.copyOf()
        if (newPid == oldPid) return output
        val oldKey = oldPid xor trainer
        val newKey = newPid xor trainer
        for (word in 0 until 12) {
            val at = start + 32 + word * 4
            put32(output, at, u32(input, at) xor oldKey xor newKey)
        }
        put32(output, start, newPid)
        // Section 1 checksum follows the exact verified Emerald-format layout.
        var sum = 0L
        for (offset in 0 until 3968 step 4) sum = (sum + u32(output, base + offset)) and 0xffffffffL
        val checksum = ((sum + (sum ushr 16)) and 65535).toInt()
        output[base + 0xff6] = checksum.toByte()
        output[base + 0xff7] = (checksum ushr 8).toByte()
        val verified = EmeraldSaveInspector.inspect(output)
        check(verified.partyCount == save.partyCount && verified.sequence == save.sequence)
        check(verified.partyIvs == save.partyIvs && verified.partyEvs == save.partyEvs)
        for (i in 0 until save.partyCount) {
            check(verified.partySpecies[i] == save.partySpecies[i])
        }
        return output
    }

    fun previewPid(input: ByteArray, position: Int, nature: Int): Long {
        val inspected = EmeraldSaveInspector.inspect(input)
        require(position in 1..inspected.partyCount) { "队伍位置无效" }
        require(nature in 0..24) { "性格必须在25种范围内" }
        val start = (0 until 14).map { inspected.activeSlot * 57344 + it * 4096 }
            .first { u16(input, it + 0xff4) == 1 } + 0x238 + (position - 1) * 100
        val oldPid = u32(input, start)
        val trainer = u32(input, start + 4)
        return requireNotNull(matchingPid(oldPid, trainer, nature)) { "找不到保持闪光状态的安全性格编码" }
    }
}
