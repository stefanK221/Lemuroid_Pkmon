package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PokemonFullPartyExperienceTest {
    @Test fun awardsFullAmountRatherThanSplit() {
        val party = List(6) { PokemonFullPartyExperience.PartyMember(true, false, false, 50) }
        assertEquals(List(6) { 1000L }, PokemonFullPartyExperience.allocate(1000, party, true))
        assertEquals(List(6) { 0L }, PokemonFullPartyExperience.allocate(1000, party, false))
    }

    @Test fun excludesEggFaintedEmptyAndMaximumLevel() {
        val m = PokemonFullPartyExperience.PartyMember(true, false, false, 50)
        val party = listOf(
            m, m.copy(fainted = true), m.copy(egg = true),
            m.copy(present = false), m.copy(level = 100),
        )
        assertEquals(listOf(600L, 0L, 0L, 0L, 0L),
            PokemonFullPartyExperience.allocate(600, party, true))
    }

    @Test fun zeroAndInvalidInput() {
        val member = PokemonFullPartyExperience.PartyMember(true, false, false, 1)
        assertEquals(listOf(0L), PokemonFullPartyExperience.allocate(0, listOf(member), true))
        assertThrows(IllegalArgumentException::class.java) {
            PokemonFullPartyExperience.allocate(-1, listOf(member), true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PokemonFullPartyExperience.allocate(100, List(7) { member }, true)
        }
    }
}
