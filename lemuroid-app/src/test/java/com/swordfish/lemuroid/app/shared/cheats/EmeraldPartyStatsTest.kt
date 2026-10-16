package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmeraldPartyStatsTest {
    @Test fun neutralNatureAndLevelBoundaries() {
        val base = listOf(80, 82, 83, 80, 100, 100)
        val ivs = List(6) { 31 }
        val evs = List(6) { 0 }
        val result = EmeraldPartyStats.calculate(base, ivs, evs, 50, 0)
        assertEquals(155, result.hp)
        assertEquals(102, result.attack)
        assertEquals(103, result.defense)
        assertEquals(100, result.speed)
        assertEquals(120, result.specialAttack)
        val hundred = EmeraldPartyStats.calculate(base, ivs, evs, 100, 0)
        assertEquals(301, hundred.hp)
    }

    @Test fun adamantRaisesAttackAndLowersSpecialAttack() {
        val base = List(6) { 100 }
        val ivs = List(6) { 31 }
        val evs = listOf(0, 252, 0, 0, 0, 0)
        val neutral = EmeraldPartyStats.calculate(base, ivs, evs, 100, 0)
        val adamant = EmeraldPartyStats.calculate(base, ivs, evs, 100, 3)
        assertEquals(neutral.attack * 110 / 100, adamant.attack)
        assertEquals(neutral.specialAttack * 90 / 100, adamant.specialAttack)
    }

    @Test fun shedinjaAndInvalidInputs() {
        assertEquals(1, EmeraldPartyStats.calculate(listOf(1, 90, 45, 40, 30, 30), List(6) { 31 }, List(6) { 0 }, 100, 0, isShedinja = true).hp)
        assertThrows(IllegalArgumentException::class.java) {
            EmeraldPartyStats.calculate(List(6) { 100 }, List(6) { 31 }, List(6) { 255 }, 100, 0)
        }
    }
}
