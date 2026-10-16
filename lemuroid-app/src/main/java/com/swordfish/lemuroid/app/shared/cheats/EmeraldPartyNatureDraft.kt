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
