package com.swordfish.lemuroid.app.shared.cheats

/**
 * Generation III stat calculation shared by future safe party-level edits.
 * Pure arithmetic: this does not read/write SRAM, and may only be used after
 * validating the exact ROM's base stats and species-specific rules.
 */
object EmeraldPartyStats {
    data class Stats(val hp: Int, val attack: Int, val defense: Int, val speed: Int, val specialAttack: Int, val specialDefense: Int)
    /** The canonical Gen III nature order is Hardy..Quirky, in five groups. */
    fun calculate(base: List<Int>, ivs: List<Int>, evs: List<Int>, level: Int, nature: Int): Stats {
        require(base.size == 6 && base.all { it in 1..255 }) { "基础能力表无效" }
        require(ivs.size == 6 && ivs.all { it in 0..31 }) { "IV无效" }
        require(evs.size == 6 && evs.all { it in 0..255 } && evs.sum() <= 510) { "EV无效" }
        require(level in 1..100 && nature in 0..24)
        // Layout: HP, Attack, Defense, Speed, Sp. Attack, Sp. Defense.
        fun raw(index: Int) = ((2 * base[index] + ivs[index] + evs[index] / 4) * level) / 100
        val hp = if (base[0] == 1) 1 else raw(0) + level + 10 // Shedinja exception
        val raised = nature / 5 + 1 // Atk, Def, Speed, SpAtk, SpDef
        val lowered = nature % 5 + 1
        fun stat(index: Int): Int {
            val neutral = raw(index) + 5
            val modifier = when {
                raised == lowered -> 100
                index == raised -> 110
                index == lowered -> 90
                else -> 100
            }
            return neutral * modifier / 100
        }
        return Stats(hp, stat(1), stat(2), stat(3), stat(4), stat(5))
    }
}
