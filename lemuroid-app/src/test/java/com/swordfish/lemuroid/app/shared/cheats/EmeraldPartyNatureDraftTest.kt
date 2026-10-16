package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.*
import org.junit.Test

class EmeraldPartyNatureDraftTest {
    @Test fun candidatesPreserveStructureOrderAbilityParityAndShiny() {
        val trainer = 0x12345678L
        val originals = listOf(0x87654321L, 0x00000000L,
            0x12345678L)
        for (original in originals) {
            for (nature in 0..24) {
                val replacement = requireNotNull(EmeraldPartyNatureDraft.matchingPid(original, trainer, nature))
                assertEquals(nature.toLong(), replacement % 25)
                assertEquals(original % 24, replacement % 24)
                assertEquals(original % 2, replacement % 2)
                assertEquals(EmeraldQuickSavePatcher.shinyFor(trainer, original),
                    EmeraldQuickSavePatcher.shinyFor(trainer, replacement))
            }
        }
    }

    private fun makeSave(): ByteArray {
        // matchingPid tests do not depend on save state; use a deterministic shiny PID instead.
        return ByteArray(0)
    }
}
