package com.swordfish.lemuroid.app.shared.cheats

/** Offline prototype: change one party nature and rebuild derived battle stats. */
object EmeraldPartyNatureStatsWriter {
    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun put16(b: ByteArray, p: Int, n: Int) {
        b[p] = n.toByte()
        b[p + 1] = (n ushr 8).toByte()
    }
    fun preview(input: ByteArray, position: Int, nature: Int, baseStats: List<Int>): ByteArray {
        val original = EmeraldSaveInspector.inspect(input)
        require(position in 1..original.partyCount && nature in 0..24)
        val section = (0 until 14).map { original.activeSlot * 57344 + it * 4096 }
            .first { u16(input, it + 0xff4) == 1 }
        val start = section + 0x238 + (position - 1) * 100
        val values = EmeraldPartyStats.calculate(
            baseStats, original.partyIvs[position - 1], original.partyEvs[position - 1],
            original.partyLevels[position - 1], nature,
            isShedinja = original.partySpecies[position - 1] == 303,
        )
        val out = EmeraldPartyNatureDraft.previewPatchedSave(input, position, nature)
        val currentHp = u16(input, start + 86)
        val oldMaximum = u16(input, start + 88)
        val current = if (currentHp == 0 && oldMaximum > 0) 0
            else (values.hp - (oldMaximum - currentHp).coerceAtLeast(0)).coerceIn(1, values.hp)
        val stats = listOf(current, values.hp, values.attack, values.defense,
            values.speed, values.specialAttack, values.specialDefense)
        stats.forEachIndexed { index, value -> put16(out, start + 86 + index * 2, value) }
        var checksum = 0L
        for (i in 0 until 3968 step 4) {
            val p = section + i
            val value = (out[p].toLong() and 255) or
                ((out[p + 1].toLong() and 255) shl 8) or
                ((out[p + 2].toLong() and 255) shl 16) or
                ((out[p + 3].toLong() and 255) shl 24)
            checksum = (checksum + value) and 0xffffffffL
        }
        put16(out, section + 0xff6, ((checksum + (checksum ushr 16)) and 65535).toInt())
        val verified = EmeraldSaveInspector.inspect(out)
        check(verified.partySpecies == original.partySpecies)
        check(verified.partyLevels == original.partyLevels)
        check(verified.partyIvs == original.partyIvs && verified.partyEvs == original.partyEvs)
        return out
    }
}
