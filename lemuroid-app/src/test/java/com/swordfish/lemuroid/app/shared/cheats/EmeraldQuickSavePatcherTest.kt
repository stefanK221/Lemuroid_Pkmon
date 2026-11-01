package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.*
import org.junit.Test

class EmeraldQuickSavePatcherTest {
    private val lengths = intArrayOf(3884,3968,3968,3968,3848,3968,3968,3968,3968,3968,3968,3968,3968,2000)
    private fun put16(b: ByteArray, p: Int, n: Int) { b[p] = n.toByte(); b[p + 1] = (n ushr 8).toByte() }
    private fun put32(b: ByteArray, p: Int, n: Long) { repeat(4) { b[p + it] = (n ushr (8 * it)).toByte() } }
    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int) = u16(b,p).toLong() or (u16(b,p+2).toLong() shl 16)
    private fun offset(id: Int) = ((id + 3) % 14) * 4096
    private fun checksum(b: ByteArray, id: Int) {
        var sum = 0L
        for (i in 0 until lengths[id] step 4) sum = (sum + u32(b, offset(id) + i)) and 0xFFFFFFFFL
        put16(b, offset(id) + 0xFF6, ((sum + (sum ushr 16)) and 65535).toInt())
    }
    private fun fixture(): ByteArray {
        val b = ByteArray(131072) { 0xFF.toByte() }
        repeat(14) { id ->
            val p = offset(id)
            b.fill(0, p, p + 4096)
            put16(b, p + 0xFF4, id); put32(b,p+0xFF8,0x08012025); put32(b,p+0xFFC,64)
        }
        put32(b,offset(0)+0xA,0x12345678)
        put32(b,offset(0)+0xAC,0xDEADBEEF)
        put32(b,offset(1)+0x490,12345L xor 0xDEADBEEF)
        repeat(14) { checksum(b,it) }
        return b
    }
    private val name = ByteArray(11) { if (it < 4) 0xBB.toByte() else 0xFF.toByte() }
    @Test fun resourcesDecodeEncryptionAndRejectUnknownPocket() {
        val input = fixture()
        assertEquals(12345L, EmeraldQuickSavePatcher.resources(input).money)
        val p = offset(1) + 0x650
        put16(input,p,1); put16(input,p+2,13 xor 0xBEEF); checksum(input,1)
        assertEquals(13, EmeraldQuickSavePatcher.resources(input).masterBalls)
        put16(input,p,999); checksum(input,1)
        assertThrows(IllegalArgumentException::class.java) { EmeraldQuickSavePatcher.resources(input) }
    }
    @Test fun oneShotMoneyDoesNotDependOnBallPocketOrChangeOtherRecords() {
        val input = fixture()
        val ball = offset(1) + 0x650
        put16(input, ball, 999)
        checksum(input, 1)
        assertEquals(12345L, EmeraldQuickSavePatcher.verifiedMoney(input))
        val before = input.copyOf()
        val patched = EmeraldQuickSavePatcher.fillMoney(input)
        assertArrayEquals(before, input)
        assertEquals(999999L, EmeraldQuickSavePatcher.verifiedMoney(patched))
        assertEquals(999, u16(patched, ball))
        assertEquals(64L, EmeraldSaveInspector.inspect(patched).sequence)
        val wallet = offset(1) + 0x490
        for (i in input.indices) {
            if (i !in wallet until wallet + 4 && i !in offset(1) + 0xFF6..offset(1) + 0xFF7) {
                assertEquals("Unexpected one-shot money write at $i", input[i], patched[i])
            }
        }
    }

    @Test fun oneShotMasterBallsUseExistingStackOrEmptySlotAndNeverOverwriteOthers() {
        val input = fixture()
        val first = offset(1) + 0x650
        put16(input, first, 4)
        put16(input, first + 2, 10 xor 0xBEEF)
        checksum(input, 1)
        assertTrue(EmeraldQuickSavePatcher.hasBallSpace(input))
        val before = input.copyOf()
        val patched = EmeraldQuickSavePatcher.giveMasterBalls(input)
        assertArrayEquals(before, input)
        assertEquals(4, u16(patched, first))
        assertEquals(10, u16(patched, first + 2) xor 0xBEEF)
        assertEquals(1, u16(patched, first + 4))
        assertEquals(99, u16(patched, first + 6) xor 0xBEEF)
        assertEquals(64L, EmeraldSaveInspector.inspect(patched).sequence)
        val patchedAgain = EmeraldQuickSavePatcher.giveMasterBalls(patched)
        assertArrayEquals(patched, patchedAgain)
        val badMoney = input.copyOf()
        put32(badMoney, offset(1) + 0x490, 0xFFFFFFFFL xor 0xDEADBEEFL)
        checksum(badMoney, 1)
        assertEquals(99, u16(EmeraldQuickSavePatcher.giveMasterBalls(badMoney), first + 6) xor 0xBEEF)
        val full = fixture()
        repeat(16) { i ->
            put16(full, first + i * 4, 4)
            put16(full, first + i * 4 + 2, 2 xor 0xBEEF)
        }
        checksum(full, 1)
        assertFalse(EmeraldQuickSavePatcher.hasBallSpace(full))
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldQuickSavePatcher.giveMasterBalls(full)
        }
        put16(full, first, 999)
        checksum(full, 1)
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldQuickSavePatcher.hasBallSpace(full)
        }
    }

    @Test fun giftPreservesAllExistingDataAndChecksumsForEveryPermutation() {
        repeat(24) { personality ->
            val input = fixture()
            val original = input.copyOf()
            val result = EmeraldQuickSavePatcher.addToFirstEmptyBox(input,150,name,35,personality.toLong())
            assertArrayEquals(original,input)
            assertEquals(1,result.box); assertEquals(1,result.position)
            assertEquals(64L,EmeraldSaveInspector.inspect(result.bytes).sequence)
            val p = offset(5) + 4
            val decoded = ByteArray(48)
            repeat(12) { put32(decoded,it*4,u32(result.bytes,p+32+it*4) xor personality.toLong() xor 0x12345678) }
            val order = mutableListOf<List<Int>>()
            fun permute(prefix: List<Int>, rest: List<Int>) { if (rest.isEmpty()) order.add(prefix) else rest.forEach { permute(prefix+it,rest-it) } }
            permute(emptyList(),listOf(0,1,2,3))
            assertEquals(150,u16(decoded,order[personality].indexOf(0)*12))
            assertEquals(33,u16(decoded,order[personality].indexOf(1)*12))
            assertEquals(35,decoded[order[personality].indexOf(1)*12+8].toInt())
            assertEquals((0 until 24).sumOf { u16(decoded,it*2) } and 65535,u16(result.bytes,p+28))
            for (i in input.indices) {
                if (i !in p until p+80 && i !in offset(5)+0xFF6..offset(5)+0xFF7) assertEquals("Unexpected write at $i",input[i],result.bytes[i])
            }
        }
    }
    @Test fun personalityGeneratorMatchesAllNaturesAndShinyRequests() {
        val input = fixture()
        val trainer = 0x12345678L
        for (nature in 0..24) {
            for (shiny in listOf(false, true)) {
                val pid = EmeraldQuickSavePatcher.personalityFor(input, nature, shiny)
                assertEquals(nature.toLong(), pid % 25)
                assertEquals(shiny, EmeraldQuickSavePatcher.shinyFor(trainer, pid))
                val output = EmeraldQuickSavePatcher.addToFirstEmptyBox(input, 6, name, 35, pid)
                assertEquals(pid, u32(output.bytes, offset(5) + 4))
                assertArrayEquals(input, fixture())
            }
        }
    }
    @Test fun customExperienceIsEncryptedCorrectlyWithoutTouchingExistingSave() {
        val input = fixture()
        val original = input.copyOf()
        val expectedExperience = PokemonExperience.forLevel(100, 3)
        val pid = 0x12345678L
        val gift = EmeraldQuickSavePatcher.addToFirstEmptyBox(input, 6, name, 35, pid, expectedExperience)
        val recordStart = offset(5) + 4
        val trainer = 0x12345678L
        val decrypted = ByteArray(48)
        repeat(12) {
            put32(decrypted, it * 4, u32(gift.bytes, recordStart + 32 + it * 4) xor pid xor trainer)
        }
        val order = mutableListOf<List<Int>>()
        fun permute(prefix: List<Int>, rest: List<Int>) {
            if (rest.isEmpty()) order.add(prefix) else rest.forEach { permute(prefix + it, rest - it) }
        }
        permute(emptyList(), listOf(0, 1, 2, 3))
        val growth = order[(pid % 24).toInt()].indexOf(0) * 12
        assertEquals(expectedExperience, u32(decrypted, growth + 4))
        assertArrayEquals(original, input)
        assertEquals(64L, EmeraldSaveInspector.inspect(gift.bytes).sequence)
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldQuickSavePatcher.addToFirstEmptyBox(input, 6, name, 35, pid, -1L)
        }
    }

    @Test fun personalityGeneratorRejectsInvalidNatureAndCorruptSave() {
        val input = fixture()
        for (nature in listOf(-1, 25, 100)) {
            assertThrows(IllegalArgumentException::class.java) {
                EmeraldQuickSavePatcher.personalityFor(input, nature, false)
            }
        }
        val damaged = input.copyOf()
        damaged[offset(0)] = 2
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldQuickSavePatcher.personalityFor(damaged, 0, true)
        }
    }
    @Test fun giftCustomIvsAndEvsAreEncryptedAndValidated() {
        val original = fixture()
        val pid = 0x12345678L
        val ivs = listOf(0, 1, 16, 29, 30, 31)
        val evs = listOf(252, 0, 4, 250, 0, 4)
        val result = EmeraldQuickSavePatcher.addToFirstEmptyBox(original, 6, name, 35, pid, 0, ivs, evs)
        val record = offset(5) + 4
        val plain = ByteArray(48)
        repeat(12) { put32(plain, it * 4, u32(result.bytes, record + 32 + it * 4) xor pid xor 0x12345678L) }
        val order = mutableListOf<List<Int>>()
        fun permute(prefix: List<Int>, rest: List<Int>) {
            if (rest.isEmpty()) order.add(prefix) else rest.forEach { permute(prefix + it, rest - it) }
        }
        permute(emptyList(), listOf(0, 1, 2, 3))
        val permutation = order[(pid % 24).toInt()]
        val effort = permutation.indexOf(2) * 12
        val misc = permutation.indexOf(3) * 12
        assertEquals(evs, (0..5).map { plain[effort + it].toInt() and 255 })
        val packed = u32(plain, misc + 4)
        assertEquals(ivs, (0..5).map { ((packed ushr (it * 5)) and 31).toInt() })
        assertEquals(0L, packed ushr 30)
        assertEquals(64L, EmeraldSaveInspector.inspect(result.bytes).sequence)
        assertArrayEquals(fixture(), original)
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldQuickSavePatcher.addToFirstEmptyBox(original, 6, name, 35, pid, 0, listOf(31), evs)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldQuickSavePatcher.addToFirstEmptyBox(original, 6, name, 35, pid, 0, ivs, listOf(255,255,255,0,0,0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldQuickSavePatcher.addToFirstEmptyBox(original, 6, name, 35, pid, 0, listOf(32,1,1,1,1,1), evs)
        }
    }
    @Test fun occupiedSlotIsNeverReplaced() {
        val input = fixture()
        input[offset(5)+4] = 99; checksum(input,5)
        val result = EmeraldQuickSavePatcher.addToFirstEmptyBox(input,25,name,35,1)
        assertEquals(2,result.position)
        assertEquals(99,result.bytes[offset(5)+4].toInt())
    }
    @Test fun invalidInputAndFullBoxesFailBeforeReturningModifiedData() {
        val input = fixture()
        assertThrows(IllegalArgumentException::class.java) { EmeraldQuickSavePatcher.addToFirstEmptyBox(input,412,name,35,1) }
        val corrupt = input.copyOf(); corrupt[offset(0)] = 1
        assertThrows(IllegalArgumentException::class.java) { EmeraldQuickSavePatcher.addToFirstEmptyBox(corrupt,25,name,35,1) }
        for (id in 5..13) { input.fill(1,offset(id),offset(id)+lengths[id]); checksum(input,id) }
        input[offset(5)] = 0; checksum(input,5)
        assertThrows(IllegalStateException::class.java) { EmeraldQuickSavePatcher.addToFirstEmptyBox(input,25,name,35,1) }
    }
}
