package com.novelreader.ui.reader

import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.theme.AppPalette

object ReaderTheme {
    const val AUTO = "auto"
    const val DEFAULT = AUTO

    const val LIGHT = "light"
    const val DARK = "dark"

    fun sanitize(storedTheme: String): String = PreferenceAllowlists.sanitizeReaderTheme(storedTheme)

    fun paletteId(storedTheme: String): String? {
        val sanitized = sanitize(storedTheme)
        return sanitized.substringBefore(':').takeIf { it.isNotEmpty() && it != AUTO }
    }

    fun variant(storedTheme: String): String? {
        val sanitized = sanitize(storedTheme)
        return sanitized.substringAfter(':', "").takeIf { it == LIGHT || it == DARK }
    }

    fun storedValue(paletteId: String, variant: String?): String =
        if (variant == null) paletteId else "$paletteId:$variant"

    fun withPalette(storedTheme: String, paletteId: String): String =
        storedValue(paletteId, variant(storedTheme))

    fun withVariant(storedTheme: String, variant: String?): String =
        storedValue(paletteId(storedTheme) ?: AUTO, variant)

    fun resolve(
        storedTheme: String,
        appPalette: String,
        appDark: Boolean
    ): Pair<String, Boolean> {
        val palette = paletteId(storedTheme) ?: appPalette
        val dark = when (variant(storedTheme)) {
            LIGHT -> false
            DARK -> true
            else -> appDark
        }
        return AppPalette.fromId(palette).id to dark
    }
}
