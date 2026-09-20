package com.novelreader.ui.reader

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.ReaderConfig
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class ReaderSettingsSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSheet(
        config: ReaderConfig = ReaderConfig(theme = "indigo"),
        deviceBrightness: Int = 100,
        themeSelection: String = "indigo",
        onThemeChange: (String) -> Unit = {},
        onAccentChange: (String?) -> Unit = {},
        onWallpaperChange: (String) -> Unit = {},
        onVeilChange: (Int) -> Unit = {},
        onFontFamilyChange: (String) -> Unit = {},
        onBrightnessChange: (Int) -> Unit = {},
        onBrightnessPreview: (Int) -> Unit = {},
        sleepTimerMinutes: Int? = null,
        onSleepTimerChange: (Int?) -> Unit = {},
        onTapZonesChange: (Boolean) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                SettingsSheet(
                    config = config,
                    themeSelection = themeSelection,
                    onThemeChange = onThemeChange,
                    onAccentChange = onAccentChange,
                    onWallpaperChange = onWallpaperChange,
                    onVeilChange = onVeilChange,
                    onFontSizeChange = {},
                    onLineHeightChange = {},
                    onAutoScrollSpeedChange = {},
                    onKeepScreenOnChange = {},
                    onSwipeDirectionChange = {},
                    onFontFamilyChange = onFontFamilyChange,
                    deviceBrightness = deviceBrightness,
                    onBrightnessChange = onBrightnessChange,
                    onBrightnessPreview = onBrightnessPreview,
                    sleepTimerMinutes = sleepTimerMinutes,
                    onSleepTimerChange = onSleepTimerChange,
                    onTapZonesChange = onTapZonesChange,
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun `shows the auto palette option with the named palettes`() {
        setSheet(themeSelection = "auto")

        composeTestRule.onNodeWithContentDescription("Auto").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Papel").assertIsNotSelected()
        composeTestRule.onNodeWithText("Floresta").assertExists()
    }

    @Test
    fun `selects the named palette chip when the stored theme names a palette`() {
        setSheet(config = ReaderConfig(theme = "papel"), themeSelection = "papel:light")

        composeTestRule.onNodeWithContentDescription("Papel").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Auto").assertIsNotSelected()
    }

    @Test
    fun `reports the auto palette while keeping the chosen variant`() {
        var selected: String? = null
        setSheet(
            config = ReaderConfig(theme = "papel"),
            themeSelection = "papel:light",
            onThemeChange = { selected = it }
        )

        composeTestRule.onNodeWithContentDescription("Auto").performClick()

        assert(selected == "auto:light") { "expected auto:light but got $selected" }
    }

    @Test
    fun `reports the tapped palette with the current variant`() {
        var selected: String? = null
        setSheet(
            config = ReaderConfig(theme = "indigo", themeDark = true),
            themeSelection = "indigo:dark",
            onThemeChange = { selected = it }
        )

        composeTestRule.onNodeWithContentDescription("Papel").performClick()

        assert(selected == "papel:dark") { "expected papel:dark but got $selected" }
    }

    @Test
    fun `selects the dark variant chip when the stored theme forces dark`() {
        setSheet(config = ReaderConfig(theme = "papel", themeDark = true), themeSelection = "papel:dark")

        composeTestRule.onNodeWithContentDescription("Escura").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Clara").assertIsNotSelected()
        composeTestRule.onNodeWithContentDescription("Seguir o app").assertIsNotSelected()
    }

    @Test
    fun `reports the follow the app variant when that chip is tapped`() {
        var selected: String? = null
        setSheet(
            config = ReaderConfig(theme = "papel", themeDark = true),
            themeSelection = "papel:dark",
            onThemeChange = { selected = it }
        )

        composeTestRule.onNodeWithContentDescription("Seguir o app").performClick()

        assert(selected == "papel") { "expected papel but got $selected" }
    }

    @Test
    fun `reports the forced light variant when that chip is tapped`() {
        var selected: String? = null
        setSheet(themeSelection = "auto", onThemeChange = { selected = it })

        composeTestRule.onNodeWithContentDescription("Clara").performClick()

        assert(selected == "auto:light") { "expected auto:light but got $selected" }
    }

    @Test
    fun `shows the reader wallpaper section with its sliders`() {
        setSheet(config = ReaderConfig(theme = "indigo", wallpaper = "builtin:noite", veil = 80))

        composeTestRule.onNodeWithText("Fundo do leitor").assertExists()
        composeTestRule.onNodeWithText("Desfoque: 0").assertExists()
        composeTestRule.onNodeWithText("Véu de leitura: 80%").assertExists()
        composeTestRule.onNodeWithContentDescription("Noite").assertIsSelected()
    }

    @Test
    fun `reports the tapped builtin wallpaper`() {
        var selected: String? = null
        setSheet(
            config = ReaderConfig(theme = "indigo"),
            onWallpaperChange = { selected = it }
        )

        composeTestRule.onNodeWithContentDescription("Aurora")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assert(selected == "builtin:aurora") { "expected builtin:aurora but got $selected" }
    }

    @Test
    fun `reports none when the wallpaper is removed`() {
        var selected: String? = "builtin:noite"
        setSheet(
            config = ReaderConfig(theme = "indigo", wallpaper = "builtin:noite"),
            onWallpaperChange = { selected = it }
        )

        composeTestRule.onNodeWithContentDescription("Remover").performScrollTo().performClick()

        assert(selected == "none") { "expected none but got $selected" }
    }

    @Test
    fun `exposes the palette chips as a radio group`() {
        setSheet(themeSelection = "auto")

        composeTestRule.onNodeWithContentDescription("Auto").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
        composeTestRule.onNodeWithContentDescription("Papel").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
    }

    @Test
    fun `reports the tapped accent preset`() {
        var selected: String? = null
        setSheet(onAccentChange = { selected = it })

        composeTestRule.onNodeWithContentDescription("#ff6f00").performClick()

        assert(selected == "#ff6f00") { "expected #ff6f00 but got $selected" }
    }

    @Test
    fun `reports the palette default when the accent is reset`() {
        var selected: String? = "not-null"
        setSheet(
            config = ReaderConfig(theme = "papel", accentColor = "#ff6f00"),
            onAccentChange = { selected = it }
        )

        composeTestRule.onNodeWithContentDescription("Padrão da paleta").performClick()

        assert(selected == null) { "expected null but got $selected" }
    }

    @Test
    fun `reflects the stored accent on the preset swatches`() {
        setSheet(config = ReaderConfig(theme = "papel", accentColor = "#2e7d32"))

        composeTestRule.onNodeWithContentDescription("#2e7d32").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Padrão da paleta").assertIsNotSelected()
    }

    @Test
    fun `shows the font family section with the four available families`() {
        setSheet()

        composeTestRule.onNodeWithText("Fonte").assertExists()
        composeTestRule.onNodeWithText("Serifada").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Sem serifa").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Monoespaçada").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Cursiva").performScrollTo().assertExists()
    }

    @Test
    fun `reflects the stored font family on its chip`() {
        setSheet(config = ReaderConfig(theme = "indigo", fontFamily = "monospace"))

        composeTestRule.onNodeWithText("Monoespaçada").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithText("Serifada").performScrollTo().assertIsNotSelected()
    }

    @Test
    fun `reports the tapped font family`() {
        var selected: String? = null
        setSheet(onFontFamilyChange = { selected = it })

        composeTestRule.onNodeWithText("Cursiva")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assert(selected == "cursive") { "expected cursive but got $selected" }
    }

    @Test
    fun `offers no fantasy chip because Android aliases it onto serif`() {
        setSheet()

        composeTestRule.onNodeWithText("Fantasy").assertDoesNotExist()
    }

    @Test
    fun `exposes the font family chips as a radio group`() {
        setSheet()

        composeTestRule.onNodeWithText("Serifada").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
        composeTestRule.onNodeWithText("Cursiva").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
    }

    @Test
    fun `shows the brightness slider at the device level while following the system`() {
        setSheet(config = ReaderConfig(theme = "indigo", brightness = -1), deviceBrightness = 40)

        composeTestRule.onNodeWithText("Brilho").assertExists()
        composeTestRule.onNodeWithText("Sistema").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Brilho")
            .performScrollTo()
            .assertRangeInfoEquals(ProgressBarRangeInfo(40f, 5f..100f, 0))
    }

    @Test
    fun `shows the fixed level on the slider when brightness is set`() {
        setSheet(config = ReaderConfig(theme = "indigo", brightness = 25))

        composeTestRule.onNodeWithText("Sistema").performScrollTo().assertIsNotSelected()
        composeTestRule.onNodeWithContentDescription("Brilho")
            .performScrollTo()
            .assertRangeInfoEquals(ProgressBarRangeInfo(25f, 5f..100f, 0))
    }

    @Test
    fun `previews the brightness while dragging and commits it on release`() {
        var committed: Int? = null
        var previewed: Int? = null
        setSheet(
            config = ReaderConfig(theme = "indigo", brightness = 25),
            onBrightnessChange = { committed = it },
            onBrightnessPreview = { previewed = it }
        )

        composeTestRule.onNodeWithContentDescription("Brilho")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }

        assertThat(previewed).isEqualTo(60)
        assertThat(committed).isEqualTo(60)
    }

    @Test
    fun `reports the system sentinel when the follow-the-device chip is tapped`() {
        var committed: Int? = null
        setSheet(
            config = ReaderConfig(theme = "indigo", brightness = 25),
            onBrightnessChange = { committed = it }
        )

        composeTestRule.onNodeWithText("Sistema")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(committed).isEqualTo(-1)
    }

    @Test
    fun `exposes the follow-the-device chip as a radio button`() {
        setSheet()

        composeTestRule.onNodeWithText("Sistema").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
    }

    @Test
    fun `shows the sleep timer off by default`() {
        setSheet()

        composeTestRule.onNodeWithText("Temporizador de sono").assertExists()
        composeTestRule.onNodeWithText("Nenhum").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithText("30 min").performScrollTo().assertIsNotSelected()
    }

    @Test
    fun `marks the armed duration and reports the tapped one`() {
        var chosen: Int? = null
        setSheet(sleepTimerMinutes = 30, onSleepTimerChange = { chosen = it })

        composeTestRule.onNodeWithText("30 min").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithText("Nenhum").performScrollTo().assertIsNotSelected()
        composeTestRule.onNodeWithText("5 min")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(chosen).isEqualTo(5)
    }

    @Test
    fun `reports none when the sleep timer is turned off`() {
        var chosen: Int? = 30
        setSheet(sleepTimerMinutes = 30, onSleepTimerChange = { chosen = it })

        composeTestRule.onNodeWithText("Nenhum")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(chosen).isNull()
    }

    @Test
    fun `shows the tap zones off by default with the explanation`() {
        setSheet()

        composeTestRule.onNodeWithText("Zonas de toque").performScrollTo().assertIsOff()
        composeTestRule.onNodeWithText("Toque nas laterais para trocar de capítulo").assertExists()
    }

    @Test
    fun `marks the tap zones on once the reader enabled them`() {
        setSheet(config = ReaderConfig(theme = "indigo", tapZones = true))

        composeTestRule.onNodeWithText("Zonas de toque").performScrollTo().assertIsOn()
    }

    @Test
    fun `reports the tap zones being switched on`() {
        var enabled: Boolean? = null
        setSheet(onTapZonesChange = { enabled = it })

        composeTestRule.onNodeWithText("Zonas de toque")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(enabled).isTrue()
    }
}
