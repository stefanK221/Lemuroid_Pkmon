package com.swordfish.lemuroid.app.shared.cheats

/** Gen III experience curves. Growth IDs are read only from the verified ROM's base stats. */
object PokemonExperience {
    fun forLevel(level: Int, growthRate: Int): Long {
        require(level in 1..100) { "等级必须为1～100" }
        require(growthRate in 0..5) { "不支持的经验成长类型" }
        val n = level.toLong()
        val cube = n * n * n
        val experience = when (growthRate) {
            0 -> cube // medium fast
            1 -> when {
                n <= 50 -> cube * (100 - n) / 50
                n <= 68 -> cube * (150 - n) / 100
                n <= 98 -> cube * ((1911 - 10 * n) / 3) / 500
                else -> cube * (160 - n) / 100
            }
            2 -> when {
                n <= 15 -> cube * ((n + 1) / 3 + 24) / 50
                n <= 36 -> cube * (n + 14) / 50
                else -> cube * (n / 2 + 32) / 50
            }
            3 -> (6 * cube / 5 - 15 * n * n + 100 * n - 140).coerceAtLeast(0)
            4 -> cube * 4 / 5
            else -> cube * 5 / 4
        }
        return if (level == 1) 0 else experience
    }
}
