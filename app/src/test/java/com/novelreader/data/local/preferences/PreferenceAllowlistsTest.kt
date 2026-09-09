package com.novelreader.data.local.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreferenceAllowlistsTest {

    @Test
    fun `sanitizeFontFamily keeps an allowlisted family`() {
        assertThat(PreferenceAllowlists.sanitizeFontFamily("serif")).isEqualTo("serif")
        assertThat(PreferenceAllowlists.sanitizeFontFamily("monospace")).isEqualTo("monospace")
        assertThat(PreferenceAllowlists.sanitizeFontFamily("Sans-Serif")).isEqualTo("sans-serif")
    }

    @Test
    fun `sanitizeFontFamily maps a hostile value to the default`() {
        assertThat(
            PreferenceAllowlists.sanitizeFontFamily(
                "serif;} </style><script>alert(1)</script><style>a{"
            )
        ).isEqualTo("serif")
        assertThat(PreferenceAllowlists.sanitizeFontFamily(null)).isEqualTo("serif")
        assertThat(PreferenceAllowlists.sanitizeFontFamily("  ")).isEqualTo("serif")
    }

    @Test
    fun `sanitizeReaderTheme maps unknown themes to light`() {
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("dark")).isEqualTo("dark")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("neon")).isEqualTo("light")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme(null)).isEqualTo("light")
    }

    @Test
    fun `sanitizeReaderTheme keeps auto so the reader can follow the app theme`() {
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("auto")).isEqualTo("auto")
    }

    @Test
    fun `sanitizeAppTheme and locale fall back to their defaults`() {
        assertThat(PreferenceAllowlists.sanitizeAppTheme("dark")).isEqualTo("dark")
        assertThat(PreferenceAllowlists.sanitizeAppTheme("garbage")).isEqualTo("system")
        assertThat(PreferenceAllowlists.sanitizeLocale("en")).isEqualTo("en")
        assertThat(PreferenceAllowlists.sanitizeLocale("xx")).isEqualTo("pt")
    }

    @Test
    fun `library preference sanitizers fall back to their defaults`() {
        assertThat(PreferenceAllowlists.sanitizeSortOrder("TITLE")).isEqualTo("TITLE")
        assertThat(PreferenceAllowlists.sanitizeSortOrder("DROP TABLE")).isEqualTo("LAST_READ")
        assertThat(PreferenceAllowlists.sanitizeChapterSortOrder("DESCENDING")).isEqualTo("DESCENDING")
        assertThat(PreferenceAllowlists.sanitizeChapterSortOrder("nope")).isEqualTo("ASCENDING")
        assertThat(PreferenceAllowlists.sanitizeViewMode("LIST")).isEqualTo("LIST")
        assertThat(PreferenceAllowlists.sanitizeViewMode("nope")).isEqualTo("GRID")
    }
}
