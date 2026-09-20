package com.novelreader.ui.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.novelreader.R

const val BAR_VEIL_ALPHA = 0.8f
const val NAVIGATION_BAR_VEIL_TAG = "navigation_bar_veil"

fun barColorFor(surface: Color, wallpaperActive: Boolean, behindBars: Boolean): Color =
    if (wallpaperActive && behindBars) surface.copy(alpha = BAR_VEIL_ALPHA) else surface

/**
 * Colour for a library container that carries text. A translucent or transparent container lets the
 * wallpaper through, and light wallpapers wash the text out (the unselected filter chips measured
 * 2.19:1 against "Amanhecer"), so over a wallpaper these containers stay opaque, like the cards.
 */
fun libraryContainerColor(default: Color, surface: Color, wallpaperActive: Boolean): Color =
    if (wallpaperActive) surface else default

@Composable
fun NavigationBarVeil(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .testTag(NAVIGATION_BAR_VEIL_TAG)
            .background(color)
    )
}

@Composable
fun WallpaperBehindBarsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ToggleRow(
        title = stringResource(R.string.wallpaper_behind_bars),
        description = stringResource(R.string.wallpaper_behind_bars_desc),
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}
