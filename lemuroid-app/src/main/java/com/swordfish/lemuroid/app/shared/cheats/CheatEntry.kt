package com.swordfish.lemuroid.app.shared.cheats

/**
 * A saved cheat definition. This is intentionally only a data/validation layer.
 * Applying a cheat requires LibretroDroid support for retro_cheat_set or an mGBA bridge.
 */
data class CheatEntry(
    val id: String,
    val gameId: String,
    val title: String,
    val code: String,
    val format: CheatFormat,
    val enabled: Boolean = false,
)

enum class CheatFormat {
    GAME_SHARK,
    CODE_BREAKER,
    RAW,
}

object CheatCodeValidator {
    private val codeLine = Regex("^[0-9A-Fa-f]{8}(?:[ :\\-]?[0-9A-Fa-f]{4,8})?$")
    private val compactLine = Regex("^[0-9A-Fa-f]{12,16}$")

    fun normalize(code: String): String =
        code.lines()
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /** Syntax only: valid hex does NOT imply compatibility with any ROM or core. */
    fun isPlausibleHexCode(code: String): Boolean {
        val lines = code.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.isNotEmpty() &&
            lines.size <= 128 &&
            lines.all { codeLine.matches(it) || compactLine.matches(it) }
    }
}
