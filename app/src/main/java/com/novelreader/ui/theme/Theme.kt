package com.novelreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.novelreader.data.local.preferences.PreferenceAllowlists

fun isDynamicPaletteAvailable(sdkInt: Int = android.os.Build.VERSION.SDK_INT): Boolean =
    sdkInt >= android.os.Build.VERSION_CODES.S

fun shouldUseDynamicColor(
    palette: String,
    accentColor: String?,
    sdkInt: Int = android.os.Build.VERSION.SDK_INT
): Boolean = palette == PreferenceAllowlists.PALETTE_DYNAMIC &&
    PreferenceAllowlists.sanitizeAccentColor(accentColor) == null &&
    isDynamicPaletteAvailable(sdkInt)

fun resolvePaletteScheme(palette: String, dark: Boolean, accentColor: String? = null): ColorScheme {
    val resolved = AppPalette.fromId(palette)
    val scheme = if (dark) resolved.dark else resolved.light
    return scheme.withAccent(accentColor)
}

/**
 * The app's own colours: the palette the user picked and the variant it is showing. A screen that
 * follows something else — the library wallpaper, say — overrides the variant for its subtree, and
 * everything below keeps reading its colours off `MaterialTheme`.
 */
data class AppVisuals(
    val palette: String = AppPalette.DEFAULT.id,
    val accentColor: String? = null,
    val dark: Boolean = false
)

val LocalAppVisuals = staticCompositionLocalOf { AppVisuals() }

/** The scheme for a palette and a variant, dynamic colour included. */
@Composable
fun appColorScheme(palette: String, dark: Boolean, accentColor: String? = null): ColorScheme {
    val context = LocalContext.current
    val dynamic = shouldUseDynamicColor(palette, accentColor)
    return when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        else -> resolvePaletteScheme(palette, dark, accentColor)
    }
}

/** A dark scheme needs light status bar icons, a light one dark icons. */
fun applySystemBarAppearance(view: android.view.View, dark: Boolean) {
    val window = (view.context as? Activity)?.window ?: return
    WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !dark
}

@Composable
fun SystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect { applySystemBarAppearance(view, dark) }
}

@Composable
fun NovelReaderTheme(
    appTheme: String = "system",
    palette: String = AppPalette.DEFAULT.id,
    accentColor: String? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = appColorScheme(palette, darkTheme, accentColor)
    SystemBarAppearance(darkTheme)

    CompositionLocalProvider(
        LocalAppVisuals provides AppVisuals(
            palette = palette,
            accentColor = accentColor,
            dark = darkTheme
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
