package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.*
import org.junit.Test

/** Party-only level planning must never mutate saves or inspect PC boxes. */
class EmeraldPartyLevelPlanTest {
    private val lengths = intArrayOf(3884, 3968, 3968, 3968, 3848, 3968, 3968, 3968, 3968, 3968, 3968, 3968, 3968, 2000)

    private fun put16(b: ByteArray, p: Int, n: Int) {
        b[p] = n.toByte()
        b[p + 1] = (n ushr 8).toByte()
    }
    private fun put32(b: ByteArray, p: Int, n: Long) {
        repeat(4) { b[p + it] = (n ushr (it * 8)).toByte() }
    }
    private fun u32(b: ByteArray, p: Int): Long =
        ((b[p].toLong() and 255) or ((b[p + 1].toLong() and 255) shl 8) or
            ((b[p + 2].toLong() and 255) shl 16) or ((b[p + 3].toLong() and 255) shl 24))
    private fun offset(id: Int) = ((id + 3) % 14) * 4096
    private fun checksum(b: ByteArray, id: Int) {
        var sum = 0L
        for (i in 0 until lengths[id] step 4) sum = (sum + u32(b, offset(id) + i)) and 0xffffffffL
        put16(b, offset(id) + 0xff6, ((sum + (sum ushr 16)) and 65535).toInt())
    }

    private fun fixture(): ByteArray {
        val b = ByteArray(131072)
        repeat(14) { id ->
            val p = offset(id)
            put16(b, p + 0xff4, id)
            put32(b, p + 0xff8, 0x08012025)
            put32(b, p + 0xffc, 7)
        }
        val party = offset(1) + 0x238
        put32(b, offset(1) + 0x234, 1)
        val pid = 0L
        val trainer = 0x12345678L
        put32(b, party, pid)
        put32(b, party + 4, trainer)
        val plain = ByteArray(48)
        put16(plain, 0, 6) // PID 0: growth is first block
        put32(b, party + 32, u32(plain, 0) xor trainer)
        for (i in 1 until 12) put32(b, party + 32 + i * 4, trainer)
        put16(b, party + 28, 6)
        b[party + 84] = 50
        repeat(14) { checksum(b, it) }
        return b
    }

    @Test fun prepareUsesSelectedPartyPokemonAndPreservesOriginal() {
        val save = fixture()
        val original = save.copyOf()
        val plan = EmeraldPartyLevelPlan.prepare(save, 1, 100) { species ->
            assertEquals(6, species)
            3
        }
        assertEquals(1, plan.position)
        assertEquals(6, plan.species)
        assertEquals(50, plan.currentLevel)
        assertEquals(100, plan.targetLevel)
        assertEquals(PokemonExperience.forLevel(100, 3), plan.targetExperience)
        assertArrayEquals(original, save)
    }

    @Test fun rejectsPositionsAndLevelsOutsideParty() {
        val save = fixture()
        for (position in listOf(0, 2, 7)) {
            assertThrows(IllegalArgumentException::class.java) {
                EmeraldPartyLevelPlan.prepare(save, position, 50) { 3 }
            }
        }
        for (level in listOf(0, 101, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                EmeraldPartyLevelPlan.prepare(save, 1, level) { 3 }
            }
        }
    }
}
