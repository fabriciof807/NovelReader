package com.novelreader.data.local.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SavedThemeCodecTest {

    private fun theme(
        name: String = "Noite",
        palette: String = "amoled",
        accentColor: String? = "#ff6f00",
        readerTheme: String = "papel:dark",
        readerAccentColor: String? = "#8d6e63"
    ) = SavedTheme(name, palette, accentColor, readerTheme, readerAccentColor)

    @Test
    fun `round trips a saved theme`() {
        val decoded = SavedThemeCodec.decode(SavedThemeCodec.encode(listOf(theme())))

        assertThat(decoded).containsExactly(theme())
    }

    @Test
    fun `round trips a theme without accents`() {
        val plain = theme(accentColor = null, readerAccentColor = null)

        val decoded = SavedThemeCodec.decode(SavedThemeCodec.encode(listOf(plain)))

        assertThat(decoded).containsExactly(plain)
    }

    @Test
    fun `empty and malformed payloads decode to an empty list`() {
        assertThat(SavedThemeCodec.decode(null)).isEmpty()
        assertThat(SavedThemeCodec.decode("")).isEmpty()
        assertThat(SavedThemeCodec.decode("   ")).isEmpty()
        assertThat(SavedThemeCodec.decode("not json at all")).isEmpty()
        assertThat(SavedThemeCodec.decode("{\"themes\":[]}")).isEmpty()
        assertThat(SavedThemeCodec.decode("[1, 2, 3]")).isEmpty()
        assertThat(SavedThemeCodec.decode("[]")).isEmpty()
    }

    @Test
    fun `an entry without a usable name is dropped`() {
        val raw = """
            [
              {"palette": "papel"},
              {"name": "", "palette": "papel"},
              {"name": "   ", "palette": "papel"},
              {"name": "Válido", "palette": "floresta"}
            ]
        """.trimIndent()

        assertThat(SavedThemeCodec.decode(raw).map { it.name }).containsExactly("Válido")
    }

    @Test
    fun `hostile values are sanitized to the defaults`() {
        val raw = """
            [
              {
                "name": "Malicioso",
                "palette": "papel;}body{display:none}",
                "accentColor": "#fff;} body { display: none }",
                "readerTheme": "floresta:purple",
                "readerAccentColor": "url(javascript:alert(1))"
              }
            ]
        """.trimIndent()

        val theme = SavedThemeCodec.decode(raw).single()

        assertThat(theme.palette).isEqualTo("indigo")
        assertThat(theme.accentColor).isNull()
        assertThat(theme.readerTheme).isEqualTo("auto")
        assertThat(theme.readerAccentColor).isNull()
    }

    @Test
    fun `a name is trimmed, stripped of control characters and capped`() {
        val raw = """
            [{"name": "  Noite\u0007\u0000escura  ", "palette": "amoled"}]
        """.trimIndent()

        assertThat(SavedThemeCodec.decode(raw).single().name).isEqualTo("Noite escura")

        val long = SavedThemeCodec.sanitizeName("x".repeat(200))
        assertThat(long).hasLength(SavedThemeCodec.MAX_NAME_LENGTH)
        assertThat(SavedThemeCodec.sanitizeName("   ")).isNull()
        assertThat(SavedThemeCodec.sanitizeName(null)).isNull()
    }

    @Test
    fun `at most five themes are decoded`() {
        val raw = (1..9).joinToString(prefix = "[", postfix = "]") {
            """{"name":"Tema $it","palette":"papel"}"""
        }

        val decoded = SavedThemeCodec.decode(raw)

        assertThat(decoded).hasSize(SavedThemeCodec.MAX_THEMES)
        assertThat(decoded.map { it.name }).containsExactly("Tema 1", "Tema 2", "Tema 3", "Tema 4", "Tema 5")
    }

    @Test
    fun `at most five themes are encoded and duplicate names collapse`() {
        val many = (1..9).map { theme(name = "Tema $it") }
        assertThat(SavedThemeCodec.decode(SavedThemeCodec.encode(many))).hasSize(5)

        val duplicates = listOf(theme(name = "Noite"), theme(name = "noite"), theme(name = "Noite"))
        assertThat(SavedThemeCodec.decode(SavedThemeCodec.encode(duplicates))).hasSize(1)
    }

    @Test
    fun `a legacy reader theme in a saved theme keeps its appearance`() {
        val raw = """[{"name":"Antigo","palette":"indigo","readerTheme":"sepia"}]"""

        assertThat(SavedThemeCodec.decode(raw).single().readerTheme).isEqualTo("papel:light")
    }
}
