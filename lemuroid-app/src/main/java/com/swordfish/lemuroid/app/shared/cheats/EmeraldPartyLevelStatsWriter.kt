package com.swordfish.lemuroid.app.shared.cheats

/** Offline level edit with updated Gen III party battle stats. */
object EmeraldPartyLevelStatsWriter {
    private fun u16(b: ByteArray, p: Int): Int = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int): Long = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
    private fun put16(b: ByteArray, p: Int, v: Int) {
        b[p] = v.toByte()
        b[p + 1] = (v ushr 8).toByte()
    }
    fun preview(input: ByteArray, plan: EmeraldPartyLevelPlan.Plan, baseStats: List<Int>): ByteArray {
        val old = EmeraldSaveInspector.inspect(input)
        require(plan.position in 1..old.partyCount)
        val section = (0 until 14).map { old.activeSlot * 57344 + it * 4096 }
            .first { u16(input, it + 0xff4) == 1 }
        val start = section + 0x238 + (plan.position - 1) * 100
        val values = EmeraldPartyStats.calculate(
            baseStats, old.partyIvs[plan.position - 1], old.partyEvs[plan.position - 1],
            plan.targetLevel, (u32(input, start) % 25).toInt(), isShedinja = plan.species == 303,
        )
        val output = EmeraldPartyLevelPlan.previewPatchedSave(input, plan)
        val oldHp = u16(input, start + 86)
        val oldMax = u16(input, start + 88)
        val hp = if (oldHp == 0 && oldMax > 0) 0
            else (values.hp - (oldMax - oldHp).coerceAtLeast(0)).coerceIn(1, values.hp)
        listOf(hp, values.hp, values.attack, values.defense, values.speed, values.specialAttack, values.specialDefense)
            .forEachIndexed { i, v -> put16(output, start + 86 + 2 * i, v) }
        var sum = 0L
        for (i in 0 until 3968 step 4) sum = (sum + u32(output, section + i)) and 0xffffffffL
        put16(output, section + 0xff6, ((sum + (sum ushr 16)) and 65535).toInt())
        val check = EmeraldSaveInspector.inspect(output)
        check(check.partyLevels[plan.position - 1] == plan.targetLevel)
        check(check.partySpecies == old.partySpecies && check.partyIvs == old.partyIvs)
        return output
    }
}
