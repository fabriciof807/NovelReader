package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PreferenceAllowlists
import org.junit.Test

class AppPaletteTest {

    @Test
    fun `every allowlisted palette id resolves to its own palette`() {
        PreferenceAllowlists.PALETTES.forEach { id ->
            assertThat(AppPalette.fromId(id).id).isEqualTo(id)
        }
    }

    @Test
    fun `unknown palette ids fall back to the default palette`() {
        assertThat(AppPalette.fromId(null)).isEqualTo(AppPalette.DEFAULT)
        assertThat(AppPalette.fromId("")).isEqualTo(AppPalette.DEFAULT)
        assertThat(AppPalette.fromId("neon")).isEqualTo(AppPalette.DEFAULT)
        assertThat(AppPalette.fromId(PreferenceAllowlists.PALETTE_DYNAMIC))
            .isEqualTo(AppPalette.DEFAULT)
    }

    @Test
    fun `every palette keeps primary readable in both variants`() {
        AppPalette.entries.forEach { palette ->
            assertThat(contrastRatio(palette.light.primary, palette.light.onPrimary))
                .isAtLeast(MinContrast)
            assertThat(contrastRatio(palette.dark.primary, palette.dark.onPrimary))
                .isAtLeast(MinContrast)
        }
    }

    @Test
    fun `every palette keeps body text readable over its background`() {
        AppPalette.entries.forEach { palette ->
            assertThat(contrastRatio(palette.light.onBackground, palette.light.background))
                .isAtLeast(MinContrast)
            assertThat(contrastRatio(palette.dark.onBackground, palette.dark.background))
                .isAtLeast(MinContrast)
        }
    }

    @Test
    fun `every reader surface keeps text readable and exposes valid css hex`() {
        AppPalette.entries.forEach { palette ->
            listOf(palette.readerLight, palette.readerDark).forEach { surface ->
                listOf(surface.bg, surface.text, surface.accent, surface.link).forEach { hex ->
                    assertThat(hex).matches("#[0-9a-f]{6}")
                }
                val bg = requireNotNull(parseAccentHex(surface.bg))
                val text = requireNotNull(parseAccentHex(surface.text))
                assertThat(contrastRatio(text, bg)).isAtLeast(MinContrast)
            }
        }
    }

    @Test
    fun `the reader surface follows the requested variant`() {
        assertThat(AppPalette.PAPEL.readerSurface(dark = false)).isEqualTo(AppPalette.PAPEL.readerLight)
        assertThat(AppPalette.PAPEL.readerSurface(dark = true)).isEqualTo(AppPalette.PAPEL.readerDark)
    }

    @Test
    fun `every container colour is opaque`() {
        // A translucent container lets whatever is behind (the wallpaper, a Surface shadow) show
        // through the FAB and the selected chips.
        AppPalette.entries.forEach { palette ->
            listOf(palette.light, palette.dark).forEach { scheme ->
                assertThat(scheme.primaryContainer.alpha).isEqualTo(1f)
                assertThat(scheme.secondaryContainer.alpha).isEqualTo(1f)
                assertThat(scheme.surfaceVariant.alpha).isEqualTo(1f)
                assertThat(scheme.errorContainer.alpha).isEqualTo(1f)
            }
        }
    }

    @Test
    fun `text on a container stays readable`() {
        AppPalette.entries.forEach { palette ->
            listOf(palette.light, palette.dark).forEach { scheme ->
                assertThat(contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer))
                    .isAtLeast(MinContrast)
                assertThat(contrastRatio(scheme.onSecondaryContainer, scheme.secondaryContainer))
                    .isAtLeast(MinContrast)
            }
        }
    }

    @Test
    fun `an accent override keeps the container opaque and readable`() {
        listOf("#ffff00", "#000080", "#ff6f00", "#808080").forEach { hex ->
            val scheme = AppPalette.INDIGO.light.withAccent(hex)
            assertThat(scheme.primaryContainer.alpha).isEqualTo(1f)
            assertThat(contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer))
                .isAtLeast(MinContrast)
        }
    }

    @Test
    fun `amoled stays pure black in both variants`() {
        val amoled = AppPalette.AMOLED
        listOf(amoled.light, amoled.dark).forEach { scheme ->
            assertThat(scheme.background).isEqualTo(Color(0xFF000000))
            assertThat(scheme.surface).isEqualTo(Color(0xFF000000))
            assertThat(scheme.surfaceVariant).isEqualTo(Color(0xFF000000))
        }
        assertThat(amoled.readerLight).isEqualTo(amoled.readerDark)
        assertThat(amoled.readerLight.bg).isEqualTo("#000000")
    }

    @Test
    fun `parseAccentHex accepts only a strict six digit hex`() {
        assertThat(parseAccentHex("#ffffff")).isEqualTo(Color(0xFFFFFFFF))
        assertThat(parseAccentHex("#FF6F00")).isEqualTo(Color(0xFFFF6F00))
        assertThat(parseAccentHex("#fff")).isNull()
        assertThat(parseAccentHex("ffffff")).isNull()
        assertThat(parseAccentHex("#ff6f00;}body{display:none}")).isNull()
        assertThat(parseAccentHex(null)).isNull()
    }

    @Test
    fun `withAccent replaces primary and keeps onPrimary readable`() {
        listOf("#ffff00", "#000080", "#ff6f00", "#808080").forEach { hex ->
            val scheme = AppPalette.INDIGO.light.withAccent(hex)
            assertThat(scheme.primary).isEqualTo(parseAccentHex(hex))
            assertThat(contrastRatio(scheme.primary, scheme.onPrimary)).isAtLeast(MinContrast)
        }
    }

    @Test
    fun `withAccent ignores a hostile or missing value`() {
        val base = AppPalette.INDIGO.light
        listOf(null, "", "#fff", "red", "#ff6f00;}body{display:none}").forEach { hostile ->
            assertThat(base.withAccent(hostile)).isEqualTo(base)
        }
    }

    @Test
    fun `resolvePaletteScheme honours the variant and the accent`() {
        assertThat(resolvePaletteScheme("papel", dark = false)).isEqualTo(AppPalette.PAPEL.light)
        assertThat(resolvePaletteScheme("papel", dark = true)).isEqualTo(AppPalette.PAPEL.dark)
        assertThat(resolvePaletteScheme("papel", dark = true, accentColor = "#ff6f00").primary)
            .isEqualTo(Color(0xFFFF6F00))
    }

    @Test
    fun `hexString renders a lowercase six digit hex`() {
        assertThat(hexString(Color(0xFFFF6F00))).isEqualTo("#ff6f00")
        assertThat(hexString(Color(0xFF000000))).isEqualTo("#000000")
        assertThat(hexString(Color(0xFFFFFFFF))).isEqualTo("#ffffff")
    }

    @Test
    fun `hsvOf inverts the hue of the accent derivation`() {
        listOf(0f, 45f, 120f, 210f, 300f, 359f).forEach { hue ->
            val hex = accentHexFor(hue, saturationPercent = 100, background = Color.White)
            val roundTrip = hsvOf(requireNotNull(parseAccentHex(hex)))
            assertThat(roundTrip.hue).isWithin(1.5f).of(hue)
            assertThat(roundTrip.saturation).isWithin(0.02f).of(1f)
        }
        assertThat(hsvOf(Color(0xFF808080)).saturation).isEqualTo(0f)
    }

    @Test
    fun `the derived accent always survives the preference allowlist`() {
        for (hue in 0..359 step 15) {
            for (saturation in listOf(0, 25, 60, 100)) {
                listOf(Color.White, Color(0xFF141414), Color(0xFF000000)).forEach { background ->
                    val hex = accentHexFor(hue.toFloat(), saturation, background)
                    assertThat(PreferenceAllowlists.sanitizeAccentColor(hex)).isEqualTo(hex)
                }
            }
        }
    }

    @Test
    fun `the derived accent stays readable over every palette background`() {
        AppPalette.entries.forEach { palette ->
            listOf(palette.light, palette.dark).forEach { scheme ->
                for (hue in 0..359 step 15) {
                    for (saturation in listOf(0, 50, 100)) {
                        val hex = accentHexFor(hue.toFloat(), saturation, scheme.background)
                        val accent = requireNotNull(parseAccentHex(hex))
                        assertThat(contrastRatio(accent, scheme.background)).isAtLeast(4.5f)
                    }
                }
            }
        }
    }

    @Test
    fun `an explicit accent wins over the dynamic palette`() {
        assertThat(shouldUseDynamicColor("dynamic", null, 33)).isTrue()
        assertThat(shouldUseDynamicColor("dynamic", "#ff6f00", 33)).isFalse()
        assertThat(shouldUseDynamicColor("dynamic", "#fff;}body{}", 33)).isTrue()
        assertThat(shouldUseDynamicColor("papel", null, 33)).isFalse()
        assertThat(shouldUseDynamicColor("dynamic", null, 30)).isFalse()
    }

    @Test
    fun `the dynamic palette is only advertised as available from api 31`() {
        assertThat(isDynamicPaletteAvailable(30)).isFalse()
        assertThat(isDynamicPaletteAvailable(31)).isTrue()
        assertThat(isDynamicPaletteAvailable(36)).isTrue()
    }
}
