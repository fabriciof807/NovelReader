package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.ui.components.AppTopBar
import com.novelreader.ui.library.LibraryBrowseState
import com.novelreader.ui.library.NovelFilter
import com.novelreader.ui.library.SortOrder
import com.novelreader.ui.library.ViewMode
import com.novelreader.ui.theme.mutedForeground

const val LIBRARY_TOP_BAR_TAG = "libraryTopBar"

@Composable
fun LibraryTopBar(
    browse: LibraryBrowseState,
    viewMode: ViewMode,
    onSortSelected: (SortOrder) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    wallpaperActive: Boolean,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    AppTopBar(
        modifier = modifier.testTag(LIBRARY_TOP_BAR_TAG),
        containerColor = containerColor
    ) {
        if (browse.isSearchActive) {
            IconButton(onClick = {
                browse.isSearchActive = false
                browse.searchQuery = ""
            }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
            OutlinedTextField(
                value = browse.searchQuery,
                onValueChange = { browse.searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = stringResource(R.string.library),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            Row(
                modifier = Modifier.selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                FilterLabel(
                    filter = NovelFilter.ALL,
                    current = browse.filterChip,
                    wallpaperActive = wallpaperActive,
                    containerColor = containerColor,
                    onSelect = { browse.filterChip = it }
                )
                FilterLabel(
                    filter = NovelFilter.READING,
                    current = browse.filterChip,
                    wallpaperActive = wallpaperActive,
                    containerColor = containerColor,
                    onSelect = { browse.filterChip = it }
                )
                FilterLabel(
                    filter = NovelFilter.COMPLETED,
                    current = browse.filterChip,
                    wallpaperActive = wallpaperActive,
                    containerColor = containerColor,
                    onSelect = { browse.filterChip = it }
                )
                FavoriteFilter(
                    selected = browse.filterChip == NovelFilter.FAVORITES,
                    color = filterLabelColor(
                        selected = browse.filterChip == NovelFilter.FAVORITES,
                        scheme = scheme,
                        containerColor = containerColor,
                        wallpaperActive = wallpaperActive
                    ),
                    onSelect = { browse.filterChip = NovelFilter.FAVORITES }
                )
            }
            IconButton(onClick = { browse.isSearchActive = true }) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_title)) },
                        onClick = { showMenu = false; onSortSelected(SortOrder.TITLE) },
                        leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_date)) },
                        onClick = { showMenu = false; onSortSelected(SortOrder.CREATED_AT) },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_last_read)) },
                        onClick = { showMenu = false; onSortSelected(SortOrder.LAST_READ) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (viewMode == ViewMode.GRID) stringResource(R.string.view_mode_list)
                                else stringResource(R.string.view_mode_grid)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onViewModeChange(
                                if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList
                                else Icons.Default.GridView,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterLabel(
    filter: NovelFilter,
    current: NovelFilter,
    wallpaperActive: Boolean,
    containerColor: Color,
    onSelect: (NovelFilter) -> Unit
) {
    val selected = current == filter
    val short = stringResource(shortLabelOf(filter))
    val full = stringResource(fullLabelOf(filter))

    Box(
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(filter) })
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = full },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = short,
            style = MaterialTheme.typography.labelLarge,
            color = filterLabelColor(selected, MaterialTheme.colorScheme, containerColor, wallpaperActive),
            maxLines = 1,
            textDecoration = if (selected) TextDecoration.Underline else null
        )
    }
}

@Composable
private fun FavoriteFilter(selected: Boolean, color: Color, onSelect: () -> Unit) {
    IconButton(
        onClick = onSelect,
        modifier = Modifier.semantics { this.selected = selected }
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = stringResource(R.string.filter_favorite_novels),
            tint = color
        )
    }
}

internal fun shortLabelOf(filter: NovelFilter): Int = when (filter) {
    NovelFilter.ALL -> R.string.filter_all_short
    NovelFilter.READING -> R.string.filter_reading_short
    NovelFilter.COMPLETED -> R.string.filter_completed_short
    NovelFilter.FAVORITES -> R.string.filter_favorite_novels
}

internal fun fullLabelOf(filter: NovelFilter): Int = when (filter) {
    NovelFilter.ALL -> R.string.filter_all
    NovelFilter.READING -> R.string.filter_reading
    NovelFilter.COMPLETED -> R.string.filter_completed
    NovelFilter.FAVORITES -> R.string.filter_favorite_novels
}

internal fun filterLabelColor(
    selected: Boolean,
    scheme: ColorScheme,
    containerColor: Color,
    wallpaperActive: Boolean
): Color = when {
    selected -> scheme.primary
    wallpaperActive && containerColor.alpha < 1f -> scheme.onSurface
    else -> mutedForeground(scheme.onSurface, containerColor)
}
