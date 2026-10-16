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
                val replacement = EmeraldPartyNatureDraft.matchingPid(original, trainer, nature)
                if (replacement == null) {
                    // Some shiny PID / nature combinations cannot preserve gender.
                    assertTrue(EmeraldQuickSavePatcher.shinyFor(trainer, original))
                    continue
                }
                assertEquals(nature.toLong(), replacement % 25)
                assertEquals(original % 24, replacement % 24)
                assertEquals(original % 2, replacement % 2)
                assertEquals(original and 255, replacement and 255)
                assertEquals(EmeraldQuickSavePatcher.shinyFor(trainer, original),
                    EmeraldQuickSavePatcher.shinyFor(trainer, replacement))
            }
        }
    }

}
