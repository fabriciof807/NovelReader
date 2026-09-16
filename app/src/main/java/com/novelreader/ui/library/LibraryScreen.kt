package com.novelreader.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.R
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.customization.HomeWallpaperViewModel
import com.novelreader.ui.customization.NavigationBarVeil
import com.novelreader.ui.customization.barColorFor
import com.novelreader.ui.customization.WallpaperBackground
import com.novelreader.ui.library.components.AddToCollectionDialog
import com.novelreader.ui.library.components.CollectionNameDialog
import com.novelreader.ui.library.components.CoverUrlDialog
import com.novelreader.ui.library.components.DeleteNovelDialog
import com.novelreader.ui.library.components.LibraryFab
import com.novelreader.ui.library.components.LibraryStatsBar
import com.novelreader.ui.library.components.WhatsNewBottomSheet
import com.novelreader.ui.library.tabs.ChaptersTab
import com.novelreader.ui.library.tabs.CollectionsTab
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
    val failedChapters by viewModel.failedChapters.collectAsState()
    val scrollToFailedRequest by viewModel.scrollToFailedRequest.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val wallpaperViewModel: HomeWallpaperViewModel = hiltViewModel()
    val homeWallpaper by wallpaperViewModel.wallpaper.collectAsState()
    val homeWallpaperBlur by wallpaperViewModel.blur.collectAsState()
    val homeWallpaperBehindBars by wallpaperViewModel.behindBars.collectAsState()
    val wallpaperActive = homeWallpaper != PreferenceAllowlists.WALLPAPER_NONE
    val barColor = barColorFor(
        surface = MaterialTheme.colorScheme.surface,
        wallpaperActive = wallpaperActive,
        behindBars = homeWallpaperBehindBars
    )
    val showWhatsNew by viewModel.showWhatsNew.collectAsState()
    val whatsNewGroups by viewModel.whatsNewGroups.collectAsState()
    val newChapterCounts by viewModel.newChapterCounts.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val folderCounts by viewModel.folderCounts.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val novelsInFolder by viewModel.novelsInFolder.collectAsState()
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var addToCollectionTarget by remember { mutableStateOf<com.novelreader.data.local.db.entity.NovelEntity?>(null) }
    var addToCollectionSelected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(addToCollectionTarget) {
        addToCollectionTarget?.let {
            addToCollectionSelected = viewModel.getFolderIdsForNovel(it.id)
        }
    }
    val context = LocalContext.current

    BackHandler(enabled = selectedNovel != null) {
        viewModel.deselectNovel()
    }

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
        if (uri != null) {
            viewModel.saveCover(novel.id, uri)
        } else {
            viewModel.clearCoverRequest()
        }
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

    if (showWhatsNew && whatsNewGroups.isNotEmpty()) {
        WhatsNewBottomSheet(
            groups = whatsNewGroups,
            onDismiss = { viewModel.dismissWhatsNew() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground(
            ref = homeWallpaper,
            blur = homeWallpaperBlur,
            modifier = Modifier.fillMaxSize()
        )
        if (wallpaperActive) {
            NavigationBarVeil(
                color = barColor,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        Scaffold(
            containerColor = if (wallpaperActive) Color.Transparent
            else MaterialTheme.colorScheme.background,
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
                            IconButton(onClick = { viewModel.deselectNovel() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        } else if (isSearchActive) {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = barColor,
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
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = barColor
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.deselectNovel() },
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
                    } else {
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.selectTab(1) },
                            text = { Text(stringResource(R.string.collections)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                LibraryFab(onClick = onImportClick)
            } else if (selectedTab == 1 && selectedNovel == null) {
                LibraryFab(onClick = { showCreateFolderDialog = true })
            }
        },
        bottomBar = {
            if (selectedTab == 0 && stats.totalNovels > 0) {
                LibraryStatsBar(stats)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> LibraryTab(
                    novels = novels,
                    backgroundImportState = backgroundImportState,
                    viewMode = viewMode,
                    readProgress = readProgress,
                    newChapterCounts = newChapterCounts,
                    searchQuery = searchQuery,
                    filterChip = filterChip,
                    onFilterChipChange = { filterChip = it },
                    onNovelClick = { novel ->
                        if (novel.lastChapterId != null) {
                            onChapterClick(novel.id, novel.lastChapterId)
                        } else {
                            viewModel.selectNovel(novel)
                        }
                    },
                    onLongClick = { viewModel.requestDeleteById(it.id) },
                    onToggleAutoUpdate = { viewModel.toggleAutoUpdate(it.id) },
                    onCheckForUpdates = { viewModel.checkForUpdates(it.id) },
                    onResyncChapters = { viewModel.resyncChapters(it.id) },
                    onChapters = { viewModel.selectNovel(it) },
                    onAddToCollection = { addToCollectionTarget = it },
                    onToggleFavorite = { novel ->
                        viewModel.toggleNovelFavorite(novel.id, novel.isFavorite)
                    },
                    onRequestChangeCover = { viewModel.requestChangeCoverById(it.id) },
                    onRequestCoverByUrl = { viewModel.requestCoverByUrlById(it.id) },
                    onContinueReading = { novel ->
                        novel.lastChapterId?.let { onChapterClick(novel.id, it) }
                    },
                    onCancelImport = { viewModel.cancelBackgroundImport() },
                    onImportLocal = { onImportClick() },
                    onImportWeb = { onImportClick() }
                )
                1 -> if (selectedNovel == null) {
                    CollectionsTab(
                        folders = folders,
                        folderCounts = folderCounts,
                        novelsInSelectedFolder = novelsInFolder,
                        allNovels = novels,
                        selectedFolder = selectedFolder,
                        onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                        onDeleteFolder = { viewModel.deleteFolder(it) },
                        onOpenFolder = { viewModel.openFolder(it) },
                        onCloseFolder = { viewModel.closeFolder() },
                        onTogglePin = { viewModel.togglePin(it) },
                        onAddNovelsToFolder = { id, ids -> viewModel.addNovelsToFolder(id, ids) },
                        onRemoveNovelFromFolder = { novelId ->
                            selectedFolder?.let { viewModel.removeNovelFromFolder(it.id, novelId) }
                        },
                        onNovelClick = { novel ->
                            if (novel.lastChapterId != null) {
                                onChapterClick(novel.id, novel.lastChapterId)
                            } else {
                                viewModel.selectNovel(novel)
                            }
                        }
                    )
                } else {
                    ChaptersTab(
                    novelId = selectedNovel?.id ?: 0L,
                    novelTitle = selectedNovel?.title,
                    chapters = chapters,
                    bookmarkCounts = bookmarkCounts,
                    sortOrder = chapterSortOrder,
                    onToggleSort = { viewModel.toggleChapterSortOrder() },
                    failedChapters = failedChapters,
                    onRetryFailed = { viewModel.retryFailedChapter(it.id) },
                    onRetryAllFailed = { viewModel.retryAllFailedChapters(it) },
                    onRetryFailedManually = { failed, uri ->
                        viewModel.retryFailedChapterManually(failed.id, uri)
                    },
                    onDismissFailed = { viewModel.dismissFailedChapter(it.id) },
                    onScanWeb = { id -> viewModel.scanMissingChapters(id) },
                    onScanLocal = { id, from, to ->
                        viewModel.scanMissingChaptersLocal(id, from, to)
                    },
                    sourceUrlAvailable = selectedNovel?.sourceUrl?.isNotBlank() == true,
                    maxChapterNumber = chapters.maxOfOrNull { it.orderIndex } ?: 0,
                    totalChapters = selectedNovel?.totalChapters ?: 0,
                    initialScroll = viewModel.getChaptersScroll(selectedNovel?.id ?: 0L),
                    onScroll = { idx, off ->
                        selectedNovel?.id?.let { viewModel.setChaptersScroll(it, idx, off) }
                    },
                    onChapterClick = { chapterId ->
                        selectedNovel?.let { onChapterClick(it.id, chapterId) }
                    },
                    pendingScrollToFailedNovelId = scrollToFailedRequest,
                    onConsumeScrollToFailed = { viewModel.consumeScrollToFailed() }
                )
                }
                2 -> PersonagensTab(
                    characters = characters,
                    characterPhotos = characterPhotos,
                    selectedNovel = selectedNovel,
                    isImporting = isImportingCharacters,
                    importResult = characterImportResult,
                    onClearImportResult = { viewModel.clearCharacterImportResult() },
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

    if (showCreateFolderDialog) {
        CollectionNameDialog(
            title = stringResource(R.string.create_collection),
            onConfirm = { name -> viewModel.createFolder(name); showCreateFolderDialog = false },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
    addToCollectionTarget?.let { novel ->
        AddToCollectionDialog(
            folders = folders,
            initialSelected = addToCollectionSelected,
            onConfirm = { ids -> viewModel.setNovelFolders(novel.id, ids); addToCollectionTarget = null },
            onDismiss = { addToCollectionTarget = null }
        )
    }
}
