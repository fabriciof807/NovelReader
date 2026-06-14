package com.novelreader.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.ui.library.LibraryStats
import com.novelreader.ui.library.ViewMode
import com.novelreader.ui.library.components.ImportProgressBanner
import com.novelreader.ui.library.components.NovelCard
import com.novelreader.ui.library.components.NovelListItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTab(
    novels: List<NovelEntity>,
    stats: LibraryStats,
    backgroundImportState: BackgroundImportState,
    viewMode: ViewMode = ViewMode.GRID,
    onNovelClick: (NovelEntity) -> Unit,
    onLongClick: (NovelEntity) -> Unit,
    onToggleAutoUpdate: (NovelEntity) -> Unit,
    onRequestChangeCover: (NovelEntity) -> Unit,
    onRequestCoverByUrl: (NovelEntity) -> Unit,
    onContinueReading: (NovelEntity) -> Unit,
    onCancelImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ImportProgressBanner(
            state = backgroundImportState,
            onCancel = onCancelImport
        )
        if (novels.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.no_novels),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tap_to_import),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(novels, key = { it.id }) { novel ->
                    var showMenu by remember { mutableStateOf(false) }
                    NovelCard(
                        novel = novel,
                        bgState = backgroundImportState,
                        onClick = { onNovelClick(novel) },
                        onLongClick = { showMenu = true },
                        onContinueClick = { onContinueReading(novel) }
                    )
                    NovelMenu(
                        novel = novel,
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onChangeCover = { showMenu = false; onRequestChangeCover(novel) },
                        onCoverByUrl = { showMenu = false; onRequestCoverByUrl(novel) },
                        onToggleAutoUpdate = { showMenu = false; onToggleAutoUpdate(novel) },
                        onDelete = { showMenu = false; onLongClick(novel) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(novels, key = { it.id }) { novel ->
                    var showMenu by remember { mutableStateOf(false) }
                    NovelListItem(
                        novel = novel,
                        bgState = backgroundImportState,
                        onClick = { onNovelClick(novel) },
                        onLongClick = { showMenu = true }
                    )
                    NovelMenu(
                        novel = novel,
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onChangeCover = { showMenu = false; onRequestChangeCover(novel) },
                        onCoverByUrl = { showMenu = false; onRequestCoverByUrl(novel) },
                        onToggleAutoUpdate = { showMenu = false; onToggleAutoUpdate(novel) },
                        onDelete = { showMenu = false; onLongClick(novel) }
                    )
                }
            }
        }
        if (stats.totalNovels > 0) {
            StatsBar(stats)
        }
    }
}

@Composable
private fun StatsBar(stats: LibraryStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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

@Composable
private fun NovelMenu(
    novel: NovelEntity,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onChangeCover: () -> Unit,
    onCoverByUrl: () -> Unit,
    onToggleAutoUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.change_cover)) },
            onClick = onChangeCover,
            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.cover_by_url)) },
            onClick = onCoverByUrl,
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (novel.autoUpdate) stringResource(R.string.auto_update_disable)
                    else stringResource(R.string.auto_update_enable)
                )
            },
            onClick = onToggleAutoUpdate,
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
            leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        )
    }
}
