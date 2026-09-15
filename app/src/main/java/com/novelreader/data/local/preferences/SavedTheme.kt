package com.novelreader.data.local.preferences

import org.json.JSONArray
import org.json.JSONObject

data class SavedTheme(
    val name: String,
    val palette: String,
    val accentColor: String?,
    val readerTheme: String,
    val readerAccentColor: String?
)

object SavedThemeCodec {
    const val MAX_THEMES = 5
    const val MAX_NAME_LENGTH = 40

    private val CONTROL_CHARS = Regex("[\\p{Cntrl}]")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizeName(value: String?): String? =
        value?.let { WHITESPACE.replace(CONTROL_CHARS.replace(it, " "), " ") }
            ?.trim()
            ?.take(MAX_NAME_LENGTH)
            ?.takeIf { it.isNotEmpty() }

    fun decode(raw: String?): List<SavedTheme> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        val themes = mutableListOf<SavedTheme>()
        for (index in 0 until array.length()) {
            if (themes.size >= MAX_THEMES) break
            val entry = array.optJSONObject(index) ?: continue
            val name = sanitizeName(entry.optString("name")) ?: continue
            if (themes.any { it.name.equals(name, ignoreCase = true) }) continue
            themes += SavedTheme(
                name = name,
                palette = PreferenceAllowlists.sanitizeAppPalette(entry.optString("palette")),
                accentColor = PreferenceAllowlists.sanitizeAccentColor(entry.optString("accentColor")),
                readerTheme = PreferenceAllowlists.sanitizeReaderTheme(entry.optString("readerTheme")),
                readerAccentColor = PreferenceAllowlists.sanitizeAccentColor(
                    entry.optString("readerAccentColor")
                )
            )
        }
        return themes
    }

    fun encode(themes: List<SavedTheme>): String {
        val array = JSONArray()
        themes.take(MAX_THEMES).forEach { theme ->
            val name = sanitizeName(theme.name) ?: return@forEach
            array.put(
                JSONObject().apply {
                    put("name", name)
                    put("palette", PreferenceAllowlists.sanitizeAppPalette(theme.palette))
                    theme.accentColor
                        ?.let { PreferenceAllowlists.sanitizeAccentColor(it) }
                        ?.let { put("accentColor", it) }
                    put(
                        "readerTheme",
                        PreferenceAllowlists.sanitizeReaderTheme(theme.readerTheme)
                    )
                    theme.readerAccentColor
                        ?.let { PreferenceAllowlists.sanitizeAccentColor(it) }
                        ?.let { put("readerAccentColor", it) }
                }
            )
        }
        return array.toString()
    }
}
