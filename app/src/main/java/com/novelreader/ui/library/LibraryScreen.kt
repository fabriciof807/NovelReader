package com.novelreader.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.R
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.components.APP_NAV_BAR_TAG
import com.novelreader.ui.components.AppDestination
import com.novelreader.ui.components.AppNavBar
import com.novelreader.ui.customization.HomeWallpaperViewModel
import com.novelreader.ui.customization.NavigationBarVeil
import com.novelreader.ui.customization.barColorFor
import com.novelreader.ui.customization.WallpaperBackground
import com.novelreader.ui.customization.WallpaperVariantTheme
import com.novelreader.ui.library.components.AddToCollectionDialog
import com.novelreader.ui.library.components.CollectionNameDialog
import com.novelreader.ui.library.components.CoverUrlDialog
import com.novelreader.ui.library.components.DeleteNovelDialog
import com.novelreader.ui.library.components.LibraryFab
import com.novelreader.ui.library.components.LibraryStatsLine
import com.novelreader.ui.library.components.LibraryTopBar
import com.novelreader.ui.library.components.WhatsNewBottomSheet
import com.novelreader.ui.library.tabs.ChaptersTab
import com.novelreader.ui.library.tabs.CollectionsTab
import com.novelreader.ui.library.tabs.LibraryTab
import com.novelreader.ui.library.tabs.PersonagensTab
import com.novelreader.ui.navigation.Routes
import com.novelreader.ui.navigation.destinationFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit,
    onNavigate: (AppDestination) -> Unit,
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
    val homeWallpaperIsLight by wallpaperViewModel.wallpaperIsLight.collectAsState()
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

    // Saveable: the library is disposed while the reader is on screen, so plain `remember` sent the
    // user back with the search box closed and the query gone.
    val browse = rememberSaveable(saver = LibraryBrowseState.Saver) { LibraryBrowseState() }
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

    WallpaperVariantTheme(homeWallpaperIsLight) {
        Box(modifier = Modifier.fillMaxSize()) {
            WallpaperBackground(
                ref = homeWallpaper,
                blur = homeWallpaperBlur,
                modifier = Modifier.fillMaxSize()
            )
            // Over a wallpaper the containers follow its tone, so the veil has to be derived from the
            // surface of the variant in force, not from the app's own one.
            val barColor = barColorFor(
                surface = MaterialTheme.colorScheme.surface,
                wallpaperActive = wallpaperActive,
                behindBars = homeWallpaperBehindBars
            )
            if (wallpaperActive && selectedNovel != null) {
                NavigationBarVeil(
                    color = barColor,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            Scaffold(
                containerColor = if (wallpaperActive) Color.Transparent
                else MaterialTheme.colorScheme.background,
                topBar = {
                    if (selectedTab == 0 && selectedNovel == null) {
                        LibraryTopBar(
                            browse = browse,
                            viewMode = viewMode,
                            onSortSelected = { viewModel.setSortOrder(it) },
                            onViewModeChange = { viewModel.setViewMode(it) },
                            wallpaperActive = wallpaperActive,
                            containerColor = barColor
                        )
                    } else {
                        Column {
                            TopAppBar(
                                title = {
                                    Text(
                                        libraryTopBarTitle(
                                            selectedTab = selectedTab,
                                            novelTitle = selectedNovel?.title,
                                            library = stringResource(R.string.library),
                                            collections = stringResource(R.string.collections)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                navigationIcon = {
                                    if (selectedTab == 1 || selectedTab == 2) {
                                        IconButton(onClick = { viewModel.deselectNovel() }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = barColor,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (selectedNovel != null) {
                                TabRow(
                                    selectedTabIndex = (selectedTab - 1).coerceIn(0, 1),
                                    containerColor = barColor
                                ) {
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
                LibraryBottomBar(
                    selectedTab = selectedTab,
                    hasSelectedNovel = selectedNovel != null,
                    stats = stats,
                    wallpaperActive = wallpaperActive,
                    onNavigate = { destination ->
                        val tab = libraryDestinationTab(destination)
                        if (tab == null) onNavigate(destination) else viewModel.selectTab(tab)
                    }
                )
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
                        searchQuery = browse.searchQuery,
                        filterChip = browse.filterChip,
                        wallpaperActive = wallpaperActive,
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
                        onConsumeScrollToFailed = { viewModel.consumeScrollToFailed() },
                        wallpaperActive = wallpaperActive
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

/**
 * What the library's top bar says. Tab 1 means two different things — the chapters of the selected
 * novel, or the collections when no novel is selected — so the tab index alone does not answer it.
 * Without a novel it used to answer "Capítulos" over the collections screen.
 */
internal fun libraryTopBarTitle(
    selectedTab: Int,
    novelTitle: String?,
    library: String,
    collections: String
): String = when {
    selectedTab == 0 -> library
    novelTitle != null -> novelTitle
    else -> collections
}

internal fun libraryDestinationTab(destination: AppDestination): Int? = when (destination) {
    AppDestination.LIBRARY -> 0
    AppDestination.COLLECTIONS -> 1
    AppDestination.FAVORITES, AppDestination.SETTINGS -> null
}

@Composable
internal fun LibraryBottomBar(
    selectedTab: Int,
    hasSelectedNovel: Boolean,
    stats: LibraryStats,
    wallpaperActive: Boolean,
    onNavigate: (AppDestination) -> Unit
) {
    Column {
        if (selectedTab == 0 && stats.totalNovels > 0) {
            LibraryStatsLine(stats = stats, wallpaperActive = wallpaperActive)
        }
        if (!hasSelectedNovel) {
            AppNavBar(
                selected = destinationFor(Routes.LIBRARY, selectedTab, hasSelectedNovel)
                    ?: AppDestination.LIBRARY,
                onSelect = onNavigate,
                wallpaperActive = wallpaperActive,
                modifier = Modifier.testTag(APP_NAV_BAR_TAG)
            )
        }
    }
}
