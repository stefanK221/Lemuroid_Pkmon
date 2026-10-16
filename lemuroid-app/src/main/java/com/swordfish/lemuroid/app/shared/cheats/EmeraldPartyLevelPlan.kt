package com.swordfish.lemuroid.app.shared.cheats

/**
 * Read-only planner for editing the level of one current party member.
 * Does not edit boxed Pokémon. The final save writer must also recalculate
 * derived party stats before exposing this operation in the app.
 */
object EmeraldPartyLevelPlan {
    data class Plan(
        val position: Int,
        val species: Int,
        val currentLevel: Int,
        val targetLevel: Int,
        val growthRate: Int,
        val targetExperience: Long,
    )

    fun prepare(
        input: ByteArray,
        position: Int,
        targetLevel: Int,
        growthRateForSpecies: (Int) -> Int,
    ): Plan {
        val save = EmeraldSaveInspector.inspect(input)
        require(position in 1..save.partyCount) { "只能选择当前队伍中的精灵" }
        require(targetLevel in 1..100) { "等级必须在1～100之间" }
        val species = save.partySpecies[position - 1]
        require(species in 1..251 || species in 277..411) {
            "该队伍精灵的物种尚未经过验证，禁止修改"
        }
        val rate = growthRateForSpecies(species)
        val experience = PokemonExperience.forLevel(targetLevel, rate)
        require(experience in 0..0xffffffffL) { "经验值超出存档格式" }
        return Plan(
            position = position,
            species = species,
            currentLevel = save.partyLevels[position - 1],
            targetLevel = targetLevel,
            growthRate = rate,
            targetExperience = experience,
        )
    }
}
