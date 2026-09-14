package com.novelreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

    val context = LocalContext.current
    val useDynamic = shouldUseDynamicColor(palette, accentColor)
    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        else -> resolvePaletteScheme(palette, darkTheme, accentColor)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
