package com.novelreader.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.ui.customization.libraryContainerColor
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.ui.library.NovelFilter
import com.novelreader.ui.library.ViewMode
import com.novelreader.ui.library.components.ImportProgressBanner
import com.novelreader.ui.library.components.LibraryEmptyState
import com.novelreader.ui.library.components.NovelCard
import com.novelreader.ui.library.components.NovelListItem

internal fun filterNovels(
    novels: List<NovelEntity>,
    searchQuery: String,
    filterChip: NovelFilter,
    readProgress: Map<Long, Float>
): List<NovelEntity> {
    val searchFiltered = if (searchQuery.isBlank()) novels
    else novels.filter { it.title.contains(searchQuery, ignoreCase = true) }

    return when (filterChip) {
        NovelFilter.ALL -> searchFiltered
        NovelFilter.READING -> searchFiltered.filter {
            val progress = readProgress[it.id] ?: 0f
            progress > 0f && progress < 1f
        }
        NovelFilter.COMPLETED -> searchFiltered.filter {
            val progress = readProgress[it.id] ?: 0f
            progress >= 1f && it.totalChapters > 0
        }
        NovelFilter.FAVORITES -> searchFiltered.filter { it.isFavorite }
    }
}

internal fun queuedNovelsNotShown(queuedTitles: List<String>, novels: List<NovelEntity>): List<String> =
    queuedTitles.filter { q -> novels.none { it.title.equals(q, ignoreCase = true) } }

internal fun favoriteIcon(isFavorite: Boolean) =
    if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTab(
    novels: List<NovelEntity>,
    backgroundImportState: BackgroundImportState,
    viewMode: ViewMode = ViewMode.GRID,
    readProgress: Map<Long, Float> = emptyMap(),
    newChapterCounts: Map<Long, Int> = emptyMap(),
    searchQuery: String = "",
    filterChip: NovelFilter = NovelFilter.ALL,
    wallpaperActive: Boolean = false,
    onFilterChipChange: (NovelFilter) -> Unit = {},
    onNovelClick: (NovelEntity) -> Unit,
    onLongClick: (NovelEntity) -> Unit,
    onToggleAutoUpdate: (NovelEntity) -> Unit,
    onCheckForUpdates: (NovelEntity) -> Unit,
    onResyncChapters: (NovelEntity) -> Unit,
    onChapters: (NovelEntity) -> Unit,
    onAddToCollection: (NovelEntity) -> Unit,
    onToggleFavorite: (NovelEntity) -> Unit,
    onRequestChangeCover: (NovelEntity) -> Unit,
    onRequestCoverByUrl: (NovelEntity) -> Unit,
    onContinueReading: (NovelEntity) -> Unit,
    onCancelImport: () -> Unit,
    onImportLocal: () -> Unit = {},
    onImportWeb: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chipFiltered = filterNovels(novels, searchQuery, filterChip, readProgress)

    val queuedNotShown = remember(backgroundImportState.queuedNovelTitles, novels) {
        queuedNovelsNotShown(backgroundImportState.queuedNovelTitles, novels)
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Column(modifier = modifier.fillMaxSize()) {
        ImportProgressBanner(
            state = backgroundImportState,
            onCancel = onCancelImport
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterChip == NovelFilter.ALL,
                onClick = { onFilterChipChange(NovelFilter.ALL) },
                colors = filterChipColors(wallpaperActive),
                label = { Text(stringResource(R.string.filter_all)) }
            )
            FilterChip(
                selected = filterChip == NovelFilter.READING,
                onClick = { onFilterChipChange(NovelFilter.READING) },
                colors = filterChipColors(wallpaperActive),
                label = { Text(stringResource(R.string.filter_reading)) }
            )
            FilterChip(
                selected = filterChip == NovelFilter.COMPLETED,
                onClick = { onFilterChipChange(NovelFilter.COMPLETED) },
                colors = filterChipColors(wallpaperActive),
                label = { Text(stringResource(R.string.filter_completed)) }
            )
            FilterChip(
                selected = filterChip == NovelFilter.FAVORITES,
                onClick = { onFilterChipChange(NovelFilter.FAVORITES) },
                colors = filterChipColors(wallpaperActive),
                label = { Text(stringResource(R.string.filter_favorite_novels)) }
            )
        }
        if (queuedNotShown.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                queuedNotShown.forEach { title ->
                    QueuedNovelRow(title = title, wallpaperActive = wallpaperActive)
                }
            }
        }
        if (novels.isEmpty()) {
            LibraryEmptyState(
                onImportLocal = onImportLocal,
                onImportWeb = onImportWeb,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else if (chipFiltered.isEmpty()) {
            LibraryEmptyState(
                onImportLocal = onImportLocal,
                onImportWeb = onImportWeb,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(chipFiltered, key = { it.id }) { novel ->
                    var showMenu by remember { mutableStateOf(false) }
                    NovelCard(
                        novel = novel,
                        readProgress = readProgress[novel.id] ?: 0f,
                        newChapterCount = newChapterCounts[novel.id] ?: 0,
                        bgState = backgroundImportState,
                        onClick = { onNovelClick(novel) },
                        onLongClick = { showMenu = true },
                        onShowMenu = { showMenu = true },
                        onContinueClick = { onContinueReading(novel) }
                    )
                    NovelMenu(
                        novel = novel,
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onChangeCover = { showMenu = false; onRequestChangeCover(novel) },
                        onCoverByUrl = { showMenu = false; onRequestCoverByUrl(novel) },
                        onToggleAutoUpdate = { showMenu = false; onToggleAutoUpdate(novel) },
                        onCheckForUpdates = { showMenu = false; onCheckForUpdates(novel) },
                        onResyncChapters = { showMenu = false; onResyncChapters(novel) },
                        onChapters = { showMenu = false; onChapters(novel) },
                        onAddToCollection = { showMenu = false; onAddToCollection(novel) },
                        onToggleFavorite = { updatedNovel -> onToggleFavorite(updatedNovel) },
                        onDelete = { showMenu = false; onLongClick(novel) }
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(chipFiltered, key = { it.id }) { novel ->
                    var showMenu by remember { mutableStateOf(false) }
                    NovelListItem(
                        novel = novel,
                        readProgress = readProgress[novel.id] ?: 0f,
                        newChapterCount = newChapterCounts[novel.id] ?: 0,
                        bgState = backgroundImportState,
                        onClick = { onNovelClick(novel) },
                        onLongClick = { showMenu = true },
                        onShowMenu = { showMenu = true }
                    )
                    NovelMenu(
                        novel = novel,
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onChangeCover = { showMenu = false; onRequestChangeCover(novel) },
                        onCoverByUrl = { showMenu = false; onRequestCoverByUrl(novel) },
                        onToggleAutoUpdate = { showMenu = false; onToggleAutoUpdate(novel) },
                        onCheckForUpdates = { showMenu = false; onCheckForUpdates(novel) },
                        onResyncChapters = { showMenu = false; onResyncChapters(novel) },
                        onChapters = { showMenu = false; onChapters(novel) },
                        onAddToCollection = { showMenu = false; onAddToCollection(novel) },
                        onToggleFavorite = { updatedNovel -> onToggleFavorite(updatedNovel) },
                        onDelete = { showMenu = false; onLongClick(novel) }
                    )
                }
            }
        }
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
    onCheckForUpdates: () -> Unit,
    onResyncChapters: () -> Unit,
    onChapters: () -> Unit,
    onAddToCollection: () -> Unit,
    onToggleFavorite: (NovelEntity) -> Unit,
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
            text = { Text(stringResource(R.string.check_for_updates)) },
            onClick = onCheckForUpdates,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.resync_chapters)) },
            onClick = onResyncChapters,
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chapters)) },
            onClick = onChapters,
            leadingIcon = { Icon(Icons.Default.List, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_to_collection)) },
            onClick = onAddToCollection,
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (novel.isFavorite) stringResource(R.string.unfavorite_novel)
                    else stringResource(R.string.favorite_novel)
                )
            },
            onClick = {
                onToggleFavorite(novel.copy(isFavorite = !novel.isFavorite))
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = favoriteIcon(novel.isFavorite),
                    contentDescription = null
                )
            }
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

@Composable
private fun filterChipColors(wallpaperActive: Boolean) =
    FilterChipDefaults.filterChipColors(
        containerColor = libraryContainerColor(
            default = Color.Transparent,
            surface = MaterialTheme.colorScheme.surface,
            wallpaperActive = wallpaperActive
        )
    )

@Composable
private fun QueuedNovelRow(title: String, wallpaperActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = libraryContainerColor(
                default = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                surface = MaterialTheme.colorScheme.surface,
                wallpaperActive = wallpaperActive
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.import_queued_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
