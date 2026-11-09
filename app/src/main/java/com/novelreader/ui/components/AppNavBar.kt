package com.novelreader.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.ui.customization.libraryContainerColor
import com.novelreader.ui.theme.AppDimens
import com.novelreader.ui.theme.AppShapes
import com.novelreader.ui.theme.MinContrast
import com.novelreader.ui.theme.contrastRatio
import com.novelreader.ui.theme.mutedForeground
import com.novelreader.ui.theme.pressedScale
import com.novelreader.ui.theme.rememberReducedMotion

enum class AppDestination {
    LIBRARY,
    COLLECTIONS,
    FAVORITES,
    SETTINGS
}

const val APP_NAV_BAR_TAG = "appNavBar"

fun appNavBarItemTag(destination: AppDestination): String = "appNavBar_${destination.name}"

data class AppNavBarColors(
    val container: Color,
    val label: Color,
    val unselectedLabel: Color,
    val indicator: Color,
    val indicatorContent: Color
)

fun appNavBarColors(scheme: ColorScheme, wallpaperActive: Boolean): AppNavBarColors {
    val container = libraryContainerColor(
        default = scheme.surfaceVariant,
        surface = scheme.surface,
        wallpaperActive = wallpaperActive
    )
    return AppNavBarColors(
        container = container,
        label = if (contrastRatio(scheme.primary, container) >= MinContrast) {
            scheme.primary
        } else {
            mutedForeground(scheme.onSurface, container)
        },
        unselectedLabel = mutedForeground(scheme.onSurface, container),
        indicator = scheme.primaryContainer,
        indicatorContent = scheme.onPrimaryContainer
    )
}

private val IndicatorHeight = 22.dp
private val ItemVerticalPadding = 4.dp
private const val LabelLineHeightFactor = 1.4f

fun appNavBarHeight(labelSizeSp: Float, fontScale: Float): Dp = maxOf(
    AppDimens.NavBarHeight,
    IndicatorHeight +
        (labelSizeSp * LabelLineHeightFactor * fontScale).dp +
        ItemVerticalPadding * 2
)

@Composable
fun AppNavBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
    wallpaperActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = appNavBarColors(MaterialTheme.colorScheme, wallpaperActive)
    val labelStyle = MaterialTheme.typography.labelSmall
    val barHeight = appNavBarHeight(labelStyle.fontSize.value, LocalDensity.current.fontScale)
    val reducedMotion = rememberReducedMotion()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.container,
        contentColor = colors.label,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(barHeight)
                .padding(horizontal = 4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppDestination.entries.forEach { destination ->
                AppNavBarItem(
                    destination = destination,
                    selected = destination == selected,
                    colors = colors,
                    reducedMotion = reducedMotion,
                    onClick = { onSelect(destination) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.AppNavBarItem(
    destination: AppDestination,
    selected: Boolean,
    colors: AppNavBarColors,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .weight(1f)
            .pressedScale(interactionSource, reducedMotion)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick
            )
            .testTag(appNavBarItemTag(destination))
            .padding(horizontal = 2.dp, vertical = ItemVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(IndicatorHeight)
                .widthIn(min = 32.dp)
                .background(
                    color = if (selected) colors.indicator else Color.Transparent,
                    shape = AppShapes.NavIndicator
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (selected) colors.indicatorContent else colors.unselectedLabel,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.label else colors.unselectedLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val AppDestination.labelRes: Int
    get() = when (this) {
        AppDestination.LIBRARY -> R.string.library
        AppDestination.COLLECTIONS -> R.string.collections
        AppDestination.FAVORITES -> R.string.favorites
        AppDestination.SETTINGS -> R.string.settings
    }

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
        AppDestination.COLLECTIONS -> Icons.Default.Folder
        AppDestination.FAVORITES -> Icons.Default.Bookmark
        AppDestination.SETTINGS -> Icons.Default.Settings
    }
