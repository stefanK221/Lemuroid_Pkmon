package com.swordfish.lemuroid.app.shared.cheats

/**
 * Read-only parser for 128 KiB Pokémon Emerald-style battery saves.
 * Does not modify save bytes or assume that arbitrary ROM hacks are compatible.
 */
object EmeraldSaveInspector {
    private const val SAVE_SIZE = 131072
    private const val SLOT_SIZE = 57344
    private const val SECTION_SIZE = 4096
    private const val SIGNATURE = 0x08012025L
    private val lengths = intArrayOf(
        3884, 3968, 3968, 3968, 3848, 3968, 3968,
        3968, 3968, 3968, 3968, 3968, 3968, 2000,
    )
    data class Inspection(val sequence: Long, val partyCount: Int, val partyIvs: List<List<Int>>)

    private fun uint16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)

    private fun uint32(b: ByteArray, i: Int): Long =
        (uint16(b, i).toLong() or (uint16(b, i + 2).toLong() shl 16)) and 0xFFFFFFFFL

    private fun sectionChecksum(bytes: ByteArray, begin: Int, count: Int): Int {
        var sum = 0L
        var i = begin
        while (i < begin + count) {
            sum = (sum + uint32(bytes, i)) and 0xFFFFFFFFL
            i += 4
        }
        return ((sum + (sum ushr 16)) and 65535).toInt()
    }

    fun inspect(bytes: ByteArray): Inspection {
        require(bytes.size == SAVE_SIZE) { "Expected a 128 KiB GBA save" }
        val candidates = (0..1).mapNotNull { slot ->
            val offsets = IntArray(14) { -1 }
            var seq: Long? = null
            var good = true
            for (physical in 0 until 14) {
                val start = slot * SLOT_SIZE + physical * SECTION_SIZE
                val id = uint16(bytes, start + 0xFF4)
                val sequence = uint32(bytes, start + 0xFFC)
                if (id !in 0..13 || (id in 0..13 && offsets[id] >= 0) ||
                    uint32(bytes, start + 0xFF8) != SIGNATURE ||
                    (seq != null && seq != sequence)
                ) {
                    good = false
                    break
                }
                if (sectionChecksum(bytes, start, lengths[id]) != uint16(bytes, start + 0xFF6)) {
                    good = false
                    break
                }
                offsets[id] = start
                seq = sequence
            }
            if (good && offsets.all { it >= 0 } && seq != null) Pair(seq, offsets) else null
        }
        require(candidates.isNotEmpty()) { "No valid Emerald save slot found" }
        val (sequence, offsets) = candidates.maxBy { it.first }
        val section1 = offsets[1]
        val count = uint32(bytes, section1 + 0x234).toInt()
        require(count in 0..6) { "Invalid party size" }
        val ivs = (0 until count).map { partyIndex ->
            val start = section1 + 0x238 + 100 * partyIndex
            val personality = uint32(bytes, start)
            val key = personality xor uint32(bytes, start + 4)
            val plain = ByteArray(48)
            for (word in 0 until 12) {
                val decrypted = uint32(bytes, start + 32 + 4 * word) xor key
                val off = 4 * word
                for (j in 0 until 4) plain[off + j] = (decrypted ushr (8 * j)).toByte()
            }
            var check = 0
            for (word in 0 until 24) check = (check + uint16(plain, 2 * word)) and 65535
            require(check == uint16(bytes, start + 28)) { "Party Pokémon checksum mismatch" }
            // Gen III's 24 PID-dependent block permutations (lexicographic order).
            val permutations = mutableListOf<List<Int>>()
            fun generate(prefix: List<Int>, remaining: List<Int>) {
                if (remaining.isEmpty()) permutations.add(prefix)
                else remaining.forEach { generate(prefix + it, remaining - it) }
            }
            generate(emptyList(), listOf(0, 1, 2, 3))
            val miscBlock = permutations[(personality % 24).toInt()].indexOf(3)
            val field = uint32(plain, miscBlock * 12 + 4)
            (0 until 6).map { ((field ushr (it * 5)) and 31).toInt() }
        }
        return Inspection(sequence, count, ivs)
    }
}
