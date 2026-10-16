package com.swordfish.lemuroid.app.shared.cheats

/**
 * Full-party battle EXP policy for the future native battle-award hook.
 * This is pure allocation math, NOT a save editor and NOT an active cheat.
 *
 * Each eligible party member receives the complete awarded EXP independently,
 * rather than dividing the award by the party size.
 */
object PokemonFullPartyExperience {
    data class PartyMember(
        val present: Boolean,
        val fainted: Boolean,
        val egg: Boolean,
        val level: Int,
    )

    /**
     * Returns one EXP award per party slot. Fainted and egg Pokémon are
     * excluded; no more than six in-party slots are accepted.
     * Caller must apply awards through the verified in-game battle routine,
     * so that level-ups, moves and evolutions happen normally.
     */
    fun allocate(fullAward: Long, party: List<PartyMember>, enabled: Boolean): List<Long> {
        require(fullAward in 0..0xffffffffL) { "EXP award out of bounds" }
        require(party.size <= 6) { "Invalid party count" }
        require(party.all { it.level in 1..100 }) { "Invalid party level" }
        return party.map { member ->
            if (enabled && member.present && !member.fainted && !member.egg && member.level < 100)
                fullAward else 0L
        }
    }
}
