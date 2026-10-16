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
        put16(b, party + 86, 120)
        put16(b, party + 88, 120)
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

    @Test fun naturePreviewTouchesOnlySelectedPartyPidEncryptedDataAndSectionChecksum() {
        val save = fixture()
        val original = save.copyOf()
        val modified = EmeraldPartyNatureDraft.previewPatchedSave(save, 1, 7)
        assertArrayEquals(original, save)
        val before = EmeraldSaveInspector.inspect(save)
        val after = EmeraldSaveInspector.inspect(modified)
        assertEquals(before.partySpecies, after.partySpecies)
        assertEquals(before.partyIvs, after.partyIvs)
        assertEquals(before.partyEvs, after.partyEvs)
        val partyStart = offset(1) + 0x238
        val newPid = u32(modified, partyStart)
        assertEquals(7L, newPid % 25)
        assertEquals(0L, newPid % 24)
        assertEquals(
            EmeraldQuickSavePatcher.shinyFor(0x12345678L, 0L),
            EmeraldQuickSavePatcher.shinyFor(0x12345678L, newPid),
        )
        for (i in save.indices) {
            val isAllowed = i in partyStart until partyStart + 4 ||
                i in partyStart + 32 until partyStart + 80 ||
                i in offset(1) + 0xff6 until offset(1) + 0xff8
            if (!isAllowed) assertEquals("Unexpected change at $i", save[i], modified[i])
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldPartyNatureDraft.previewPatchedSave(save, 2, 7)
        }
    }

    @Test fun offlineLevelPreviewTouchesOnlyChosenPartyRecordAndChecksum() {
        val save = fixture()
        val before = save.copyOf()
        val plan = EmeraldPartyLevelPlan.prepare(save, 1, 75) { 3 }
        val changed = EmeraldPartyLevelPlan.previewPatchedSave(save, plan)
        assertArrayEquals(before, save)
        assertEquals(75, EmeraldSaveInspector.inspect(changed).partyLevels[0])
        val start = offset(1) + 0x238
        for (i in save.indices) {
            val allowed = i in start + 32 until start + 80 ||
                i in start + 28 until start + 30 ||
                i == start + 84 ||
                i in offset(1) + 0xff6 until offset(1) + 0xff8
            if (!allowed) assertEquals("Unexpected change at $i", save[i], changed[i])
        }
        assertEquals(6, EmeraldSaveInspector.inspect(changed).partySpecies[0])
    }

    @Test fun levelAndNatureStatWriterPreviewsAreChecksummedAndPartyOnly() {
        val source = fixture()
        val old = source.copyOf()
        val base = listOf(80, 82, 83, 80, 100, 100)
        val plan = EmeraldPartyLevelPlan.prepare(source, 1, 75) { 3 }
        val level = EmeraldPartyLevelStatsWriter.preview(source, plan, base)
        val levelRead = EmeraldSaveInspector.inspect(level)
        assertEquals(75, levelRead.partyLevels[0])
        assertEquals(6, levelRead.partySpecies[0])
        val nature = EmeraldPartyNatureStatsWriter.preview(source, 1, 7, base)
        assertEquals(7L, u32(nature, offset(1) + 0x238) % 25)
        assertEquals(6, EmeraldSaveInspector.inspect(nature).partySpecies[0])
        assertArrayEquals(old, source)
        val begin = offset(1) + 0x238
        for (i in source.indices) {
            val allowed = i in begin until begin + 100 ||
                i in offset(1) + 0xff6 until offset(1) + 0xff8
            if (!allowed) {
                assertEquals("Level modified outside selected party at $i", source[i], level[i])
                assertEquals("Nature modified outside selected party at $i", source[i], nature[i])
            }
        }
    }

    @Test fun recalculatedStatsMatchGenThreeFormulasAndPreserveDamage() {
        val save = fixture()
        val base = listOf(80, 82, 83, 80, 100, 100)
        val pokemon = offset(1) + 0x238
        put16(save, pokemon + 86, 95)
        checksum(save, 1)
        val plan = EmeraldPartyLevelPlan.prepare(save, 1, 75) { 3 }
        val changed = EmeraldPartyLevelStatsWriter.preview(save, plan, base)
        val expected = EmeraldPartyStats.calculate(base, List(6) { 0 }, List(6) { 0 }, 75, 0)
        assertEquals(expected.hp, (changed[pokemon + 88].toInt() and 255) or
            ((changed[pokemon + 89].toInt() and 255) shl 8))
        val expectedHp = (expected.hp - 25).coerceIn(1, expected.hp)
        assertEquals(expectedHp, (changed[pokemon + 86].toInt() and 255) or
            ((changed[pokemon + 87].toInt() and 255) shl 8))
        assertEquals(75, EmeraldSaveInspector.inspect(changed).partyLevels[0])
    }

    @Test fun invalidHpPreventsLevelOrNatureWrite() {
        val save = fixture()
        val pokemon = offset(1) + 0x238
        put16(save, pokemon + 86, 130)
        checksum(save, 1)
        val base = listOf(80, 82, 83, 80, 100, 100)
        val plan = EmeraldPartyLevelPlan.prepare(save, 1, 55) { 3 }
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldPartyLevelStatsWriter.preview(save, plan, base)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldPartyNatureStatsWriter.preview(save, 1, 7, base)
        }
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
