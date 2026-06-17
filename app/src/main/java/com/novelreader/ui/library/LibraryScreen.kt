package com.novelreader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.R
import com.novelreader.ui.library.components.CoverUrlDialog
import com.novelreader.ui.library.components.DeleteNovelDialog
import com.novelreader.ui.library.tabs.ChaptersTab
import com.novelreader.ui.library.tabs.LibraryTab
import com.novelreader.ui.library.tabs.PersonagensTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onChapterClick: (Long, Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val novels by viewModel.novels.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val characterPhotos by viewModel.characterPhotos.collectAsState()
    val selectedNovel by viewModel.selectedNovel.collectAsState()
    val bookmarkCounts by viewModel.bookmarkCounts.collectAsState()
    val deleteTarget by viewModel.showDeleteDialog.collectAsState()
    val coverTarget by viewModel.coverTargetNovel.collectAsState()
    val urlDialogTarget by viewModel.showUrlDialog.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val chapterSortOrder by viewModel.chapterSortOrder.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val coverError by viewModel.coverError.collectAsState()
    val backgroundImportState by viewModel.backgroundImportState.collectAsState()
    val readProgress by viewModel.readProgress.collectAsState()
    val isImportingCharacters by viewModel.isImportingCharacters.collectAsState()
    val characterImportResult by viewModel.characterImportResult.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterChip by remember { mutableStateOf(NovelFilter.ALL) }
    val snackbarHostState = remember { SnackbarHostState() }
    val previousBgRunning = remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val novel = coverTarget ?: return@rememberLauncherForActivityResult
        uri?.let { viewModel.saveCover(novel.id, it) }
    }

    coverTarget?.let { novel ->
        LaunchedEffect(novel) {
            imagePickerLauncher.launch("image/*")
        }
    }

    LaunchedEffect(coverError) {
        coverError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCoverError()
        }
    }

    LaunchedEffect(backgroundImportState) {
        val bgState = backgroundImportState ?: return@LaunchedEffect
        if (bgState.completed && previousBgRunning.value) {
            snackbarHostState.showSnackbar(context.getString(R.string.background_import_complete))
        }
        previousBgRunning.value = bgState.running
    }

    LaunchedEffect(characterImportResult) {
        characterImportResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCharacterImportResult()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    urlDialogTarget?.let { novel ->
        CoverUrlDialog(
            onConfirm = { url -> viewModel.saveCoverFromUrl(novel.id, url) },
            onDismiss = { viewModel.cancelUrlDialog() }
        )
    }

    deleteTarget?.let { novel ->
        DeleteNovelDialog(
            novelTitle = novel.title,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSearchActive && selectedTab == 0) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                if (selectedTab == 0) stringResource(R.string.library)
                                else selectedNovel?.title ?: stringResource(R.string.chapters),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (selectedTab == 1 || selectedTab == 2) {
                            IconButton(onClick = { viewModel.selectTab(0) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        } else if (isSearchActive) {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        if (selectedTab == 0) {
                            if (isSearchActive) {
                                IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                                }
                            } else {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                                }
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_title)) },
                                        onClick = { viewModel.setSortOrder(SortOrder.TITLE); showSortMenu = false },
                                        leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_date)) },
                                        onClick = { viewModel.setSortOrder(SortOrder.CREATED_AT); showSortMenu = false },
                                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_last_read)) },
                                        onClick = { viewModel.setSortOrder(SortOrder.LAST_READ); showSortMenu = false },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }
                                    )
                                }
                            }
                        }
                        if (selectedTab == 0) {
                            IconButton(onClick = {
                                viewModel.setViewMode(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
                            }) {
                                Icon(
                                    if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = if (viewMode == ViewMode.GRID)
                                        stringResource(R.string.view_mode_list)
                                    else stringResource(R.string.view_mode_grid)
                                )
                            }
                        }
                        IconButton(onClick = onFavoritesClick) {
                            Icon(Icons.Default.Bookmark, contentDescription = stringResource(R.string.favorites))
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text(stringResource(R.string.library)) }
                    )
                    if (selectedNovel != null) {
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.selectTab(1) },
                            text = { Text(stringResource(R.string.chapters)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { viewModel.selectTab(2) },
                            text = { Text(stringResource(R.string.characters)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = onImportClick) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> LibraryTab(
                    novels = novels,
                    stats = stats,
                    backgroundImportState = backgroundImportState,
                    viewMode = viewMode,
                    readProgress = readProgress,
                    searchQuery = searchQuery,
                    filterChip = filterChip,
                    onFilterChipChange = { filterChip = it },
                    onNovelClick = { viewModel.selectNovel(it) },
                    onLongClick = { viewModel.requestDelete(it) },
                    onToggleAutoUpdate = { viewModel.toggleAutoUpdate(it.id) },
                    onRequestChangeCover = { viewModel.requestChangeCover(it) },
                    onRequestCoverByUrl = { viewModel.requestCoverByUrl(it) },
                    onContinueReading = { novel ->
                        novel.lastChapterId?.let { onChapterClick(novel.id, it) }
                    },
                    onCancelImport = { viewModel.cancelBackgroundImport() }
                )
                1 -> ChaptersTab(
                    chapters = chapters,
                    bookmarkCounts = bookmarkCounts,
                    sortOrder = chapterSortOrder,
                    onToggleSort = { viewModel.toggleChapterSortOrder() },
                    onChapterClick = { chapterId ->
                        selectedNovel?.let { onChapterClick(it.id, chapterId) }
                    }
                )
                2 -> PersonagensTab(
                    characters = characters,
                    characterPhotos = characterPhotos,
                    selectedNovel = selectedNovel,
                    isImporting = isImportingCharacters,
                    onAddCharacter = { name, photoPath ->
                        selectedNovel?.let { viewModel.addCharacter(it.id, name, photoPath) }
                    },
                    onDeleteCharacter = { id -> viewModel.deleteCharacter(id) },
                    onAddCharacterPhoto = { charId, path -> viewModel.addCharacterPhoto(charId, path) },
                    onBatchAddCharacterPhotos = { charId, paths -> viewModel.batchAddCharacterPhotos(charId, paths) },
                    onDeleteCharacterPhoto = { photoId, charId -> viewModel.deleteCharacterPhoto(photoId, charId) },
                    onUpdateCharacterName = { charId, name -> viewModel.updateCharacterName(charId, name) },
                    onUpdateCharacterNotes = { charId, notes -> viewModel.updateCharacterNotes(charId, notes) },
                    onToggleCharacterFavorite = { charId, fav -> viewModel.toggleCharacterFavorite(charId, fav) },
                    onImportCharacters = { url -> viewModel.importCharactersFromUrl(url) }
                )
            }
        }
    }
}
