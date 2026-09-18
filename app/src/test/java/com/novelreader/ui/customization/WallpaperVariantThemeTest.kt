package com.novelreader.ui.customization

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.AppPalette
import com.novelreader.ui.theme.MinContrast
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.contrastRatio
import com.novelreader.ui.theme.withAccent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #17: with Grafite (dark) over the light "areia" wallpaper the library rendered the wallpaper
 * and the palette variant side by side — bars, tabs, chips, cards, FAB and counters all dark over a
 * light background. Containers that carry text follow the wallpaper's tone instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class WallpaperVariantThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var captured: ColorScheme? = null

    private lateinit var appTheme: MutableState<String>
    private lateinit var palette: MutableState<String>
    private lateinit var accentColor: MutableState<String?>
    private lateinit var isLightWallpaper: MutableState<Boolean?>

    /** One `setContent` per test: the rule refuses a second call, so the inputs have to be mutable. */
    private fun harness(appTheme: String = "dark", palette: String = "grafite", accent: String? = null) {
        this.appTheme = mutableStateOf(appTheme)
        this.palette = mutableStateOf(palette)
        this.accentColor = mutableStateOf(accent)
        this.isLightWallpaper = mutableStateOf(null)

        composeTestRule.setContent {
            NovelReaderTheme(
                appTheme = this.appTheme.value,
                palette = this.palette.value,
                accentColor = this.accentColor.value
            ) {
                WallpaperVariantTheme(this.isLightWallpaper.value) {
                    captured = MaterialTheme.colorScheme
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun show(tone: Boolean?, theme: String? = null) {
        isLightWallpaper.value = tone
        theme?.let { appTheme.value = it }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `a light wallpaper over a dark palette uses the light variant`() {
        harness(appTheme = "dark", palette = "grafite")

        show(tone = true)

        assertThat(captured!!.surface).isEqualTo(AppPalette.GRAFITE.light.surface)
        assertThat(captured!!.onSurface).isEqualTo(AppPalette.GRAFITE.light.onSurface)
        assertThat(captured!!.background).isEqualTo(AppPalette.GRAFITE.light.background)
    }

    @Test
    fun `a dark wallpaper over a light palette uses the dark variant`() {
        harness(appTheme = "light", palette = "indigo")

        show(tone = false)

        assertThat(captured!!.surface).isEqualTo(AppPalette.INDIGO.dark.surface)
        assertThat(captured!!.onSurface).isEqualTo(AppPalette.INDIGO.dark.onSurface)
    }

    @Test
    fun `no wallpaper leaves the app variant alone`() {
        harness(appTheme = "dark", palette = "grafite")

        show(tone = null)

        assertThat(captured!!.surface).isEqualTo(AppPalette.GRAFITE.dark.surface)
        assertThat(captured!!.onSurface).isEqualTo(AppPalette.GRAFITE.dark.onSurface)
    }

    @Test
    fun `a wallpaper that agrees with the app variant changes nothing`() {
        harness(appTheme = "dark", palette = "grafite")

        show(tone = false)
        assertThat(captured!!.surface).isEqualTo(AppPalette.GRAFITE.dark.surface)

        show(tone = true, theme = "light")
        assertThat(captured!!.surface).isEqualTo(AppPalette.GRAFITE.light.surface)
    }

    // The accent is the user's choice and has to survive the swap: withAccent re-derives the
    // containers from the accent over the variant's own background, and the pair stays readable.
    @Test
    fun `the accent survives the variant swap`() {
        harness(appTheme = "dark", palette = "grafite", accent = "#ff6f00")

        show(tone = true)

        assertThat(captured!!.primary).isEqualTo(Color(0xFFFF6F00))
        assertThat(captured!!.primaryContainer)
            .isEqualTo(AppPalette.GRAFITE.light.withAccent("#ff6f00").primaryContainer)
        assertThat(captured!!.primaryContainer)
            .isNotEqualTo(AppPalette.GRAFITE.dark.withAccent("#ff6f00").primaryContainer)
        assertThat(contrastRatio(captured!!.primaryContainer, captured!!.onPrimaryContainer))
            .isAtLeast(MinContrast)
    }

    // Readability has to hold in either case, not only in the variant the palette was designed for:
    // swapping the variant is safe exactly as long as the palette's own text pairs survive it.
    @Test
    fun `every palette keeps its text readable in the swapped variant`() {
        harness(appTheme = "dark", palette = "indigo")

        show(tone = true)

        AppPalette.entries.forEach { entry ->
            palette.value = entry.id
            composeTestRule.waitForIdle()

            val scheme = captured!!
            assertThat(contrastRatio(scheme.onSurface, scheme.surface)).isAtLeast(MinContrast)
            assertThat(contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer))
                .isAtLeast(MinContrast)
        }
    }

    // The veiled bar derives from the surface it is handed, so over a light wallpaper the 80% veil
    // has to come from the light surface too — otherwise the bar keeps the dark tone the issue
    // reports ("the veiled bars have the same problem with the veil on").
    @Test
    fun `the veiled bar follows the wallpaper tone`() {
        var bar: Color? = null
        val tone = mutableStateOf<Boolean?>(null)
        composeTestRule.setContent {
            NovelReaderTheme(appTheme = "dark", palette = "grafite") {
                WallpaperVariantTheme(tone.value) {
                    bar = barColorFor(
                        surface = MaterialTheme.colorScheme.surface,
                        wallpaperActive = true,
                        behindBars = true
                    )
                }
            }
        }
        tone.value = true
        composeTestRule.waitForIdle()

        assertThat(bar!!.alpha).isEqualTo(BAR_VEIL_ALPHA)
        assertThat(bar!!.red).isEqualTo(AppPalette.GRAFITE.light.surface.red)
        assertThat(bar!!.green).isEqualTo(AppPalette.GRAFITE.light.surface.green)
        assertThat(bar!!.blue).isEqualTo(AppPalette.GRAFITE.light.surface.blue)
    }
}
