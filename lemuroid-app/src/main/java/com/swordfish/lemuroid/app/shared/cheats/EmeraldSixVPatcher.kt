package com.swordfish.lemuroid.app.shared.cheats

/**
 * Produces a NEW Emerald-format save with selected party members changed to six 31 IVs.
 * Never changes the input buffer. Caller must back up the original and explicitly export output.
 * Save compatibility with ROM hacks must still be verified in-game.
 */
object EmeraldSixVPatcher {
    private const val SLOT_BYTES = 14 * 4096
    private val sectionLengths = intArrayOf(
        3884, 3968, 3968, 3968, 3848, 3968, 3968,
        3968, 3968, 3968, 3968, 3968, 3968, 2000,
    )

    private fun u16(data: ByteArray, at: Int): Int =
        (data[at].toInt() and 255) or ((data[at + 1].toInt() and 255) shl 8)

    private fun u32(data: ByteArray, at: Int): Long =
        (u16(data, at).toLong() or (u16(data, at + 2).toLong() shl 16)) and 0xffffffffL

    private fun put16(data: ByteArray, at: Int, value: Int) {
        data[at] = value.toByte()
        data[at + 1] = (value ushr 8).toByte()
    }

    private fun put32(data: ByteArray, at: Int, value: Long) {
        repeat(4) { data[at + it] = (value ushr (8 * it)).toByte() }
    }

    private fun checksum(data: ByteArray, at: Int, length: Int): Int {
        var sum = 0L
        for (offset in 0 until length step 4) {
            sum = (sum + u32(data, at + offset)) and 0xffffffffL
        }
        return ((sum + (sum ushr 16)) and 65535).toInt()
    }

    private val permutations: List<List<Int>> = buildList {
        fun rec(prefix: List<Int>, remain: List<Int>) {
            if (remain.isEmpty()) add(prefix)
            else remain.forEach { rec(prefix + it, remain - it) }
        }
        rec(emptyList(), listOf(0, 1, 2, 3))
    }

    /** Party positions are 1-based; an empty selection is rejected. */
    fun apply(input: ByteArray, selectedPositions: Set<Int>): ByteArray {
        val original = EmeraldSaveInspector.inspect(input)
        require(selectedPositions.isNotEmpty())
        require(selectedPositions.all { it in 1..original.partyCount }) {
            "Invalid party position"
        }
        val output = input.copyOf()

        // Use the active slot, locating sections by their stored ID rather than physical order.
        val activeSlot = original.activeSlot
        val section1 = (0 until 14).map { activeSlot * SLOT_BYTES + it * 4096 }
            .first { u16(output, it + 0xff4) == 1 }

        for (position in selectedPositions) {
            val start = section1 + 0x238 + 100 * (position - 1)
            val personality = u32(output, start)
            val key = personality xor u32(output, start + 4)
            val plain = ByteArray(48)
            for (word in 0 until 12) {
                put32(plain, word * 4, u32(output, start + 32 + word * 4) xor key)
            }
            val misc = permutations[(personality % 24).toInt()].indexOf(3)
            val ivOffset = misc * 12 + 4
            val oldField = u32(plain, ivOffset)
            put32(plain, ivOffset, (oldField and 0xC0000000L) or 0x3FFFFFFFL)
            for (word in 0 until 12) {
                put32(output, start + 32 + word * 4, u32(plain, word * 4) xor key)
            }
            val pokemonChecksum = (0 until 24).sumOf { u16(plain, it * 2) } and 65535
            put16(output, start + 28, pokemonChecksum)
        }
        put16(output, section1 + 0xff6, checksum(output, section1, sectionLengths[1]))
        val verified = EmeraldSaveInspector.inspect(output)
        check(verified.sequence == original.sequence)
        check(verified.partyCount == original.partyCount)
        for (position in selectedPositions) {
            check(verified.partyIvs[position - 1] == List(6) { 31 })
        }
        for (position in 1..original.partyCount) {
            if (position !in selectedPositions) {
                check(verified.partyIvs[position - 1] == original.partyIvs[position - 1])
            }
        }
        return output
    }
}
