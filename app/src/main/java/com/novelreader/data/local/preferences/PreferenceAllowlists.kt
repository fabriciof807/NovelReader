package com.novelreader.data.local.preferences

object PreferenceAllowlists {
    private val FONT_FAMILIES = setOf("serif", "sans-serif", "monospace", "cursive", "fantasy")
    private val APP_THEMES = setOf("system", "light", "dark")
    private val LOCALES = setOf(LOCALE_SYSTEM, "pt", "en")

    /** Follow the device language. The app forces a locale only when the reader picked one. */
    const val LOCALE_SYSTEM = "system"
    private val SORT_ORDERS = setOf("TITLE", "CREATED_AT", "LAST_READ")
    private val CHAPTER_SORT_ORDERS = setOf("ASCENDING", "DESCENDING")
    private val VIEW_MODES = setOf("GRID", "LIST")
    private val SWIPE_DIRECTIONS = setOf("vertical", "horizontal", "both", "none")

    val PALETTES = setOf("indigo", "papel", "grafite", "floresta", "ameixa", "amoled")
    const val PALETTE_DYNAMIC = "dynamic"
    private val READER_VARIANTS = setOf("light", "dark")
    private val DYNAMIC_PALETTE = PALETTE_DYNAMIC

    private val LEGACY_READER_THEMES = mapOf(
        "light" to "indigo:light",
        "dark" to "indigo:dark",
        "sepia" to "papel:light",
        "gray" to "grafite:dark"
    )

    private val ACCENT_COLOR = Regex("^#[0-9a-f]{6}$")
    private val WALLPAPER_FILE = Regex("^[a-z0-9_]{1,64}\\.(jpg|jpeg|png|webp)$")

    val BUILTIN_WALLPAPERS = setOf(
        "areia", "ardosia", "musgo",
        "amanhecer", "aurora", "bosque", "carvao",
        "crepusculo", "noite", "oceano", "pergaminho"
    )

    const val WALLPAPER_NONE = "none"
    const val MAX_BLUR = 60
    const val MAX_VEIL = 100
    const val DEFAULT_VEIL = 80

    fun sanitizeFontFamily(value: String?): String =
        value?.trim()?.lowercase()?.takeIf { it in FONT_FAMILIES } ?: "serif"

    fun sanitizeAppTheme(value: String?): String =
        value?.trim()?.takeIf { it in APP_THEMES } ?: "system"

    fun sanitizeAppPalette(value: String?): String {
        val normalized = value?.trim()?.lowercase()
        return when {
            normalized == DYNAMIC_PALETTE -> DYNAMIC_PALETTE
            normalized != null && normalized in PALETTES -> normalized
            else -> "indigo"
        }
    }

    fun sanitizeReaderTheme(value: String?): String {
        val normalized = value?.trim()?.lowercase() ?: return "auto"
        if (normalized == "auto") return "auto"
        LEGACY_READER_THEMES[normalized]?.let { return it }
        val parts = normalized.split(":")
        return when {
            parts.size == 1 && parts[0] in PALETTES -> parts[0]
            parts.size == 2 && (parts[0] in PALETTES || parts[0] == "auto") &&
                parts[1] in READER_VARIANTS -> normalized
            else -> "auto"
        }
    }

    fun defaultAppPalette(sdkInt: Int): String =
        if (sdkInt >= android.os.Build.VERSION_CODES.S) PALETTE_DYNAMIC else "indigo"

    fun sanitizeAccentColor(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { ACCENT_COLOR.matches(it) }

    fun sanitizeWallpaperRef(value: String?): String {
        val normalized = value?.trim()?.lowercase() ?: return WALLPAPER_NONE
        return when {
            normalized == WALLPAPER_NONE -> WALLPAPER_NONE
            normalized.startsWith("builtin:") &&
                normalized.removePrefix("builtin:") in BUILTIN_WALLPAPERS -> normalized
            normalized.startsWith("file:") &&
                WALLPAPER_FILE.matches(normalized.removePrefix("file:")) -> normalized
            else -> WALLPAPER_NONE
        }
    }

    fun sanitizeBlur(value: Int?): Int = value?.coerceIn(0, MAX_BLUR) ?: 0

    fun sanitizeVeil(value: Int?): Int = value?.coerceIn(0, MAX_VEIL) ?: DEFAULT_VEIL

    fun sanitizeLocale(value: String?): String =
        value?.trim()?.lowercase()?.takeIf { it in LOCALES } ?: LOCALE_SYSTEM

    fun sanitizeSortOrder(value: String?): String =
        value?.trim()?.takeIf { it in SORT_ORDERS } ?: "LAST_READ"

    fun sanitizeChapterSortOrder(value: String?): String =
        value?.trim()?.takeIf { it in CHAPTER_SORT_ORDERS } ?: "ASCENDING"

    fun sanitizeViewMode(value: String?): String =
        value?.trim()?.takeIf { it in VIEW_MODES } ?: "GRID"

    fun sanitizeSwipeDirection(value: String?): String =
        value?.trim()?.lowercase()?.takeIf { it in SWIPE_DIRECTIONS } ?: "vertical"
}
