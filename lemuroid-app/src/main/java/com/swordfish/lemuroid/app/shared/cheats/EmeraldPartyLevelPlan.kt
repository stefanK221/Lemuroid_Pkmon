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


    private fun u16(b: ByteArray, p: Int): Int = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int): Long = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
    private fun put16(b: ByteArray, p: Int, n: Int) {
        b[p] = n.toByte()
        b[p + 1] = (n ushr 8).toByte()
    }
    private fun put32(b: ByteArray, p: Int, n: Long) {
        repeat(4) { b[p + it] = (n ushr (8 * it)).toByte() }
    }
    private val permutations = buildList<List<Int>> {
        fun generate(prefix: List<Int>, remaining: List<Int>) {
            if (remaining.isEmpty()) add(prefix) else remaining.forEach {
                generate(prefix + it, remaining - it)
            }
        }
        generate(emptyList(), listOf(0, 1, 2, 3))
    }

    /**
     * Offline preview ONLY. Changes the chosen party member's encrypted EXP
     * and cached level in a COPY of the save; never touches PC boxes.
     * The derived stats (HP, Attack, Defense etc.) are not recalculated here.
     * Do not wire this prototype to a user-facing Apply button.
     */
    fun previewPatchedSave(input: ByteArray, plan: Plan): ByteArray {
        val original = EmeraldSaveInspector.inspect(input)
        require(plan.position in 1..original.partyCount)
        require(plan.species == original.partySpecies[plan.position - 1])
        require(plan.currentLevel == original.partyLevels[plan.position - 1])
        require(plan.targetLevel in 1..100)
        require(plan.targetExperience == PokemonExperience.forLevel(plan.targetLevel, plan.growthRate))
        val section = (0 until 14)
            .map { original.activeSlot * 57344 + it * 4096 }
            .first { u16(input, it + 0xff4) == 1 }
        val start = section + 0x238 + (plan.position - 1) * 100
        val pid = u32(input, start)
        val key = pid xor u32(input, start + 4)
        val growth = permutations[(pid % 24).toInt()].indexOf(0) * 12
        val output = input.copyOf()
        val at = start + 32 + growth + 4
        put32(output, at, plan.targetExperience xor key)
        output[start + 84] = plan.targetLevel.toByte()
        val plain = ByteArray(48)
        for (word in 0 until 12) {
            put32(plain, word * 4, u32(output, start + 32 + word * 4) xor key)
        }
        put16(output, start + 28, (0 until 24).sumOf { u16(plain, it * 2) } and 65535)
        var sum = 0L
        for (i in 0 until 3968 step 4) sum = (sum + u32(output, section + i)) and 0xffffffffL
        put16(output, section + 0xff6, ((sum + (sum ushr 16)) and 65535).toInt())
        val checked = EmeraldSaveInspector.inspect(output)
        check(checked.partyLevels[plan.position - 1] == plan.targetLevel)
        check(checked.partySpecies == original.partySpecies)
        check(checked.partyIvs == original.partyIvs && checked.partyEvs == original.partyEvs)
        check(checked.partyCount == original.partyCount && checked.sequence == original.sequence)
        return output
    }

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
        require(species in 1..411) {
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
