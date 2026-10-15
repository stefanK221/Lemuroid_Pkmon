package com.swordfish.lemuroid.app.shared.cheats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistent, per-game cheat definitions. This class never writes to the ROM or save file.
 * Back up saves separately before testing actual cheat execution.
 */
class CheatStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("gba_plus_cheats_v1", Context.MODE_PRIVATE)

    fun read(gameId: String): List<CheatEntry> {
        val raw = preferences.getString(gameId, null) ?: return emptyList()
        return runCatching {
            val items = JSONArray(raw)
            (0 until items.length()).mapNotNull { index ->
                runCatching {
                    val item = items.getJSONObject(index)
                    CheatEntry(
                        id = item.getString("id"),
                        gameId = gameId,
                        title = item.getString("title"),
                        code = item.getString("code"),
                        format = CheatFormat.valueOf(item.getString("format")),
                        enabled = item.optBoolean("enabled", false),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun add(gameId: String, title: String, code: String, format: CheatFormat): CheatEntry {
        require(title.isNotBlank()) { "Name is required" }
        require(CheatCodeValidator.isPlausibleHexCode(code)) { "Invalid hexadecimal cheat code" }
        val entry = CheatEntry(UUID.randomUUID().toString(), gameId, title.trim(), CheatCodeValidator.normalize(code), format)
        save(gameId, read(gameId) + entry)
        return entry
    }

    fun setEnabled(gameId: String, id: String, enabled: Boolean) {
        save(gameId, read(gameId).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun delete(gameId: String, id: String) {
        save(gameId, read(gameId).filterNot { it.id == id })
    }

    private fun save(gameId: String, entries: List<CheatEntry>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("title", entry.title)
                put("code", entry.code)
                put("format", entry.format.name)
                put("enabled", entry.enabled)
            })
        }
        check(preferences.edit().putString(gameId, array.toString()).commit()) { "Unable to save cheat definitions" }
    }
}
