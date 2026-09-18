package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.ui.customization.libraryContainerColor
import com.novelreader.ui.library.LibraryStats

const val STATS_BAR_ALPHA = 0.5f

@Composable
fun LibraryStatsBar(
    stats: LibraryStats,
    wallpaperActive: Boolean = false,
    modifier: Modifier = Modifier,
    navigationBarInsets: WindowInsets = WindowInsets.navigationBars
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = libraryContainerColor(
            default = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = STATS_BAR_ALPHA),
            surface = MaterialTheme.colorScheme.surface,
            wallpaperActive = wallpaperActive
        ),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Inside the Surface, not on it: the counters colour has to cover the Android
                // navigation bar strip below (the gesture bar area), which is part of this bar's
                // surface. On the Surface it only shrank the painted area and the strip kept the
                // wallpaper veil, so the screen ended in a different surface than it appears to.
                .windowInsetsPadding(navigationBarInsets)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = stringResource(R.string.novels),
                value = stats.totalNovels.toString()
            )
            StatItem(
                label = stringResource(R.string.chapters),
                value = stats.totalChapters.toString()
            )
            StatItem(
                label = stringResource(R.string.favorites),
                value = stats.totalBookmarks.toString()
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
