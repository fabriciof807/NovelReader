package com.novelreader.data.local.preferences

object PreferenceAllowlists {
    private val FONT_FAMILIES = setOf("serif", "sans-serif", "monospace", "cursive", "fantasy")
    private val READER_THEMES = setOf("light", "dark", "sepia", "gray")
    private val APP_THEMES = setOf("system", "light", "dark")
    private val LOCALES = setOf("pt", "en")
    private val SORT_ORDERS = setOf("TITLE", "CREATED_AT", "LAST_READ")
    private val CHAPTER_SORT_ORDERS = setOf("ASCENDING", "DESCENDING")
    private val VIEW_MODES = setOf("GRID", "LIST")

    fun sanitizeFontFamily(value: String?): String =
        value?.trim()?.lowercase()?.takeIf { it in FONT_FAMILIES } ?: "serif"

    fun sanitizeReaderTheme(value: String?): String =
        value?.trim()?.takeIf { it in READER_THEMES } ?: "light"

    fun sanitizeAppTheme(value: String?): String =
        value?.trim()?.takeIf { it in APP_THEMES } ?: "system"

    fun sanitizeLocale(value: String?): String =
        value?.trim()?.lowercase()?.takeIf { it in LOCALES } ?: "pt"

    fun sanitizeSortOrder(value: String?): String =
        value?.trim()?.takeIf { it in SORT_ORDERS } ?: "LAST_READ"

    fun sanitizeChapterSortOrder(value: String?): String =
        value?.trim()?.takeIf { it in CHAPTER_SORT_ORDERS } ?: "ASCENDING"

    fun sanitizeViewMode(value: String?): String =
        value?.trim()?.takeIf { it in VIEW_MODES } ?: "GRID"
}
