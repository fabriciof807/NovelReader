package com.novelreader.ui.customization

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.novelreader.ui.theme.AppVisuals
import com.novelreader.ui.theme.LocalAppVisuals
import com.novelreader.ui.theme.SystemBarAppearance
import com.novelreader.ui.theme.Typography
import com.novelreader.ui.theme.appColorScheme
import com.novelreader.ui.theme.applySystemBarAppearance

/**
 * Renders the subtree with the palette variant that matches the wallpaper's tone, so text-bearing
 * containers follow the background the user picked instead of the palette the user picked: Grafite
 * (dark) over the light "areia" wallpaper shows the light Grafite variant — light bars, tabs, chips,
 * cards, FAB and counters with dark text — and the mirrored combination shows the dark one.
 *
 * A `null` tone (no wallpaper, or an image whose pixels could not be read) leaves the app's own
 * variant in place, which is also what a wallpaper agreeing with it resolves to.
 */
@Composable
fun WallpaperVariantTheme(isLightWallpaper: Boolean?, content: @Composable () -> Unit) {
    val visuals = LocalAppVisuals.current
    val overrideDark = isLightWallpaper?.not()?.takeIf { it != visuals.dark }
    val dark = overrideDark ?: visuals.dark
    val view = LocalView.current

    SystemBarAppearance(dark)
    // The library leaves composition when the reader opens and NovelReaderTheme does not recompose on
    // navigation, so the app's own bar appearance has to be put back from here.
    DisposableEffect(overrideDark, visuals.dark) {
        onDispose {
            if (overrideDark != null) applySystemBarAppearance(view, visuals.dark)
        }
    }

    CompositionLocalProvider(
        LocalAppVisuals provides visuals.copy(dark = dark)
    ) {
        MaterialTheme(
            colorScheme = if (overrideDark != null) {
                appColorScheme(visuals.palette, dark, visuals.accentColor)
            } else {
                MaterialTheme.colorScheme
            },
            typography = Typography,
            content = content
        )
    }
}
