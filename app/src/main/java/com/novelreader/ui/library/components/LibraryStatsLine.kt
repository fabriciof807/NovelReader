package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.ui.customization.libraryContainerColor
import com.novelreader.ui.library.LibraryStats
import com.novelreader.ui.theme.AppDimens
import com.novelreader.ui.theme.mutedForeground

const val LIBRARY_STATS_LINE_TAG = "libraryStatsLine"

data class LibraryStatsLineColors(
    val container: Color,
    val text: Color
)

fun libraryStatsLineColors(scheme: ColorScheme, wallpaperActive: Boolean): LibraryStatsLineColors {
    val container = libraryContainerColor(
        default = scheme.surfaceVariant,
        surface = scheme.surface,
        wallpaperActive = wallpaperActive
    )
    return LibraryStatsLineColors(
        container = container,
        text = mutedForeground(scheme.onSurface, container)
    )
}

@Composable
fun LibraryStatsLine(
    stats: LibraryStats,
    wallpaperActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = libraryStatsLineColors(MaterialTheme.colorScheme, wallpaperActive)
    val text = listOf(
        "${stats.totalNovels} ${stringResource(R.string.novels)}",
        "${stats.totalChapters} ${stringResource(R.string.chapters)}",
        "${stats.totalBookmarks} ${stringResource(R.string.favorites)}"
    ).joinToString(" · ")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LIBRARY_STATS_LINE_TAG),
        color = colors.container,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.StatsLineHeight)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
