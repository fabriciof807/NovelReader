package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderThemeTest {

    @Test
    fun `storedValue keeps a palette without a variant`() {
        assertThat(ReaderTheme.storedValue("papel", null)).isEqualTo("papel")
        assertThat(ReaderTheme.storedValue("papel", ReaderTheme.DARK)).isEqualTo("papel:dark")
    }

    @Test
    fun `paletteId and variant read a composite value`() {
        assertThat(ReaderTheme.paletteId("floresta")).isEqualTo("floresta")
        assertThat(ReaderTheme.paletteId("floresta:dark")).isEqualTo("floresta")
        assertThat(ReaderTheme.paletteId(ReaderTheme.AUTO)).isNull()
        assertThat(ReaderTheme.variant("floresta")).isNull()
        assertThat(ReaderTheme.variant("floresta:dark")).isEqualTo(ReaderTheme.DARK)
        assertThat(ReaderTheme.variant("floresta:light")).isEqualTo(ReaderTheme.LIGHT)
        assertThat(ReaderTheme.variant(ReaderTheme.AUTO)).isNull()
    }

    @Test
    fun `auto follows the app palette and the app variant`() {
        assertThat(ReaderTheme.resolve(ReaderTheme.AUTO, "floresta", appDark = true))
            .isEqualTo("floresta" to true)
        assertThat(ReaderTheme.resolve(ReaderTheme.AUTO, "grafite", appDark = false))
            .isEqualTo("grafite" to false)
    }

    @Test
    fun `a palette without a variant still follows the app variant`() {
        assertThat(ReaderTheme.resolve("papel", "floresta", appDark = true))
            .isEqualTo("papel" to true)
    }

    @Test
    fun `an explicit variant overrides the app variant`() {
        assertThat(ReaderTheme.resolve("papel:light", "floresta", appDark = true))
            .isEqualTo("papel" to false)
        assertThat(ReaderTheme.resolve("papel:dark", "floresta", appDark = false))
            .isEqualTo("papel" to true)
    }

    @Test
    fun `legacy stored themes keep their original palette and variant`() {
        assertThat(ReaderTheme.resolve("light", "ameixa", appDark = true))
            .isEqualTo("indigo" to false)
        assertThat(ReaderTheme.resolve("dark", "ameixa", appDark = false))
            .isEqualTo("indigo" to true)
        assertThat(ReaderTheme.resolve("sepia", "ameixa", appDark = true))
            .isEqualTo("papel" to false)
        assertThat(ReaderTheme.resolve("gray", "ameixa", appDark = false))
            .isEqualTo("grafite" to true)
    }

    @Test
    fun `an unknown stored theme behaves like auto`() {
        assertThat(ReaderTheme.resolve("neon", "papel", appDark = true))
            .isEqualTo("papel" to true)
    }

    @Test
    fun `the dynamic app palette falls back to the default reader surface`() {
        assertThat(ReaderTheme.resolve(ReaderTheme.AUTO, "dynamic", appDark = true))
            .isEqualTo("indigo" to true)
    }
}
