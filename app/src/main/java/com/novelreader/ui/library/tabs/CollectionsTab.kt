package com.novelreader.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.db.entity.FolderEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.ui.library.components.AddNovelsToCollectionDialog
import com.novelreader.ui.library.components.CollectionCard
import com.novelreader.ui.library.components.CollectionNameDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionsTab(
    folders: List<FolderEntity>,
    folderCounts: Map<Long, Int>,
    novelsInSelectedFolder: List<NovelEntity>,
    allNovels: List<NovelEntity>,
    selectedFolder: FolderEntity?,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onOpenFolder: (FolderEntity) -> Unit,
    onCloseFolder: () -> Unit,
    onTogglePin: (FolderEntity) -> Unit,
    onAddNovelsToFolder: (Long, List<Long>) -> Unit,
    onRemoveNovelFromFolder: (Long) -> Unit,
    onNovelClick: (NovelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var menuTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var showAddNovelsDialog by remember { mutableStateOf(false) }

    if (selectedFolder != null) {
        FolderDetail(
            folder = selectedFolder,
            novels = novelsInSelectedFolder,
            onBack = onCloseFolder,
            onAddNovels = { showAddNovelsDialog = true },
            onNovelClick = onNovelClick,
            onNovelLongClick = { novel -> onRemoveNovelFromFolder(novel.id) }
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.collection_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folders, key = { it.id }) { folder ->
                        Box {
                            CollectionCard(
                                name = folder.name,
                                novelCount = folderCounts[folder.id] ?: 0,
                                isPinned = folder.isPinned,
                                onClick = { onOpenFolder(folder) },
                                onLongClick = { menuTarget = folder },
                                onTogglePin = { onTogglePin(folder) }
                            )
                            DropdownMenu(
                                expanded = menuTarget == folder,
                                onDismissRequest = { menuTarget = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename_collection)) },
                                    onClick = { renameTarget = folder; menuTarget = null },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_collection), color = MaterialTheme.colorScheme.error) },
                                    onClick = { deleteTarget = folder; menuTarget = null },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { folder ->
        CollectionNameDialog(
            title = stringResource(R.string.rename_collection),
            initialName = folder.name,
            onConfirm = { name -> onRenameFolder(folder.id, name); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_collection)) },
            text = { Text(stringResource(R.string.delete_collection_confirm)) },
            confirmButton = {
                Button(onClick = { onDeleteFolder(folder.id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showAddNovelsDialog && selectedFolder != null) {
        AddNovelsToCollectionDialog(
            novels = allNovels,
            initialSelected = novelsInSelectedFolder.map { it.id }.toSet(),
            onConfirm = { ids -> onAddNovelsToFolder(selectedFolder.id, ids); showAddNovelsDialog = false },
            onDismiss = { showAddNovelsDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderDetail(
    folder: FolderEntity,
    novels: List<NovelEntity>,
    onBack: () -> Unit,
    onAddNovels: () -> Unit,
    onNovelClick: (NovelEntity) -> Unit,
    onNovelLongClick: (NovelEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        folder.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAddNovels) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_novels_to_collection))
                    }
                }
            )
        }
    ) { padding ->
        if (novels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.collection_no_novels),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(novels, key = { it.id }) { novel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = novel.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
