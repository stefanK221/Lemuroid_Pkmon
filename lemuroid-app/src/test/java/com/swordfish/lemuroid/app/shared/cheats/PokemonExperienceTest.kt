package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PokemonExperienceTest {
    @Test fun allSixExperienceCurvesHaveCanonicalLevel100Values() {
        val totals = listOf(1000000L, 600000L, 1640000L, 1059860L, 800000L, 1250000L)
        totals.forEachIndexed { growth, total ->
            assertEquals(total, PokemonExperience.forLevel(100, growth))
            assertEquals(0L, PokemonExperience.forLevel(1, growth))
        }
    }

    @Test fun levelsAlwaysIncreaseAndStayWithinUnsigned32BitSaveField() {
        for (growth in 0..5) {
            var previous = -1L
            for (level in 1..100) {
                val total = PokemonExperience.forLevel(level, growth)
                org.junit.Assert.assertTrue("curve $growth level $level", total > previous)
                org.junit.Assert.assertTrue(total in 0..0xFFFFFFFFL)
                previous = total
            }
        }
    }

    @Test fun rejectsInvalidGrowthOrLevel() {
        for (level in listOf(0, 101, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                PokemonExperience.forLevel(level, 0)
            }
        }
        for (growth in listOf(-1, 6, 99)) {
            assertThrows(IllegalArgumentException::class.java) {
                PokemonExperience.forLevel(50, growth)
            }
        }
    }
}
