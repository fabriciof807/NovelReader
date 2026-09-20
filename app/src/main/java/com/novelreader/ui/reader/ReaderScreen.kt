package com.novelreader.ui.reader

import android.app.Activity
import android.provider.Settings
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novelreader.R
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.customization.WallpaperBackground
import com.novelreader.ui.customization.WallpaperCropOverlay
import com.novelreader.ui.customization.barColorFor
import com.novelreader.ui.theme.parseAccentHex
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.preferences.ReaderConfig
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    novelId: Long,
    chapterId: Long,
    onBack: () -> Unit,
    onChapterChange: (Long, String?) -> Unit,
    initialSearchQuery: String? = null,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isOptionsVisible by remember { mutableStateOf(true) }
    var scrollRatio by remember { mutableStateOf(0f) }
    var lastInitialSearchQuery by remember { mutableStateOf(initialSearchQuery) }
    val pendingSearchQueryState = remember { mutableStateOf(initialSearchQuery) }
    var pendingSearchQuery by pendingSearchQueryState
    var isPageLoaded by remember { mutableStateOf(false) }
    var pendingRestoreToken by remember { mutableStateOf<Int?>(null) }
    var pendingSwipeTransition by remember { mutableStateOf(ChapterTransition.NONE) }
    val loadToken = remember { LoadToken() }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkToDelete by remember { mutableStateOf<Long?>(null) }
    var showChapterList by remember { mutableStateOf(false) }
    var chapterSearchQuery by remember { mutableStateOf("") }
    var reverseChapterOrder by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val view = LocalView.current
    val lastLoad = remember { mutableStateOf<Pair<ChapterEntity, WebView>?>(null) }

    LaunchedEffect(state.config.keepScreenOn) {
        view.keepScreenOn = state.config.keepScreenOn
    }

    val readerWindow = (view.context as? Activity)?.window
    var liveBrightness by remember { mutableIntStateOf(state.config.brightness) }

    LaunchedEffect(state.config.brightness) { liveBrightness = state.config.brightness }

    LaunchedEffect(liveBrightness, readerWindow) {
        val window = readerWindow ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = windowBrightnessFor(liveBrightness)
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    // The library shares this Activity window, so the override has to leave with the reader.
    DisposableEffect(readerWindow) {
        onDispose {
            val window = readerWindow ?: return@onDispose
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    LaunchedEffect(isOptionsVisible) {
        if (isOptionsVisible) {
            delay(4000L)
            isOptionsVisible = false
        }
    }

    if (initialSearchQuery != lastInitialSearchQuery) {
        lastInitialSearchQuery = initialSearchQuery
        pendingSearchQuery = initialSearchQuery
    }

    val savedThemes by viewModel.savedThemes.collectAsState()
    val wallpaperBehindBars by viewModel.wallpaperBehindBars.collectAsState()
    var liveVeil by remember { mutableIntStateOf(state.config.veil) }
    var liveBlur by remember { mutableIntStateOf(state.config.wallpaperBlur) }

    // Read once: while the reader follows the device, this is where its slider has to start, or the
    // first drag would jump away from a level that does not match the screen.
    val deviceBrightness = remember(view.context) {
        runCatching {
            Settings.System.getInt(view.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()?.let { systemBrightnessPercent(it) }
            ?: PreferenceAllowlists.MAX_BRIGHTNESS
    }

    LaunchedEffect(state.config.veil) { liveVeil = state.config.veil }
    LaunchedEffect(state.config.wallpaperBlur) { liveBlur = state.config.wallpaperBlur }

    val pendingCrop by viewModel.pendingCrop.collectAsState()
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.startCrop(it) }
    }

    fun saveScroll(callback: () -> Unit = {}) {
        val wv = webView
        if (wv != null) {
            wv.evaluateJavascript(
                bookmarkCaptureRatioJs(),
                ValueCallback { value ->
                    val ratio = value?.trim('"')?.toFloatOrNull()
                    if (ratio != null) {
                        viewModel.saveScrollPosition(ratio)
                    } else {
                        viewModel.saveScrollPosition()
                    }
                    callback()
                }
            )
        } else {
            viewModel.saveScrollPosition()
            callback()
        }
    }

    LaunchedEffect(state.chapter, webView) {
        val chapter = state.chapter ?: return@LaunchedEffect
        val wv = webView ?: return@LaunchedEffect
        if (lastLoad.value == (chapter to wv)) return@LaunchedEffect
        lastLoad.value = chapter to wv
        val issued = loadToken.next()
        pendingRestoreToken = issued
        scrollRatio = viewModel.getScrollRatio()
        isPageLoaded = false
        val transition = pendingSwipeTransition
        pendingSwipeTransition = ChapterTransition.NONE
        val html = buildReaderHtml(
            content = chapter.content,
            config = state.config,
            transition = transition,
            chapterTitle = com.novelreader.data.parser.TitleExtractor
                .cleanChapterTitleForDisplay(chapter.title, state.novel?.title)
        )
        wv.loadDataWithBaseURL(loadToken.baseUrl(issued), html, "text/html", "UTF-8", null)
    }

    LaunchedEffect(state.config, webView) {
        webView?.evaluateJavascript(applyConfigJs(state.config), null)
    }

    val retryLabel = stringResource(R.string.action_retry)

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (viewModel.retryAvailable.value) retryLabel else null,
                duration = SnackbarDuration.Short
            )?.let {
                if (it == SnackbarResult.ActionPerformed) viewModel.retryLastFailedAction()
            }
        }
    }

    LaunchedEffect(state.bookmarks, webView) {
        webView?.evaluateJavascript(applyBookmarksJs(state.bookmarks), null)
    }

    bookmarkToDelete?.let {
        DeleteBookmarkDialog(
            onConfirm = {
                viewModel.deleteBookmark(it)
                bookmarkToDelete = null
            },
            onDismiss = { bookmarkToDelete = null }
        )
    }

    if (state.showBookmarkDialog) {
        BookmarkManagerDialog(
            bookmarks = state.bookmarks,
            onDismiss = { viewModel.hideBookmarkDialog() },
            onAddNew = {
                showAddBookmarkDialog = true
            },
            onDelete = { id -> bookmarkToDelete = id },
            onBookmarkClick = { bookmark ->
                viewModel.hideBookmarkDialog()
                val ratio = (bookmark.scrollPosition / 1000f).coerceIn(0f, 1f)
                webView?.evaluateJavascript(scrollRestoreJs(ratio), null)
                viewModel.saveScrollPosition(ratio)
            }
        )
    }

    if (showAddBookmarkDialog) {
        AddBookmarkDialog(
            defaultTitle = viewModel.getDefaultBookmarkTitle(),
            onDismiss = { showAddBookmarkDialog = false },
            onSave = { title, note ->
                viewModel.addBookmark(title, note)
                showAddBookmarkDialog = false
            }
        )
    }

    if (state.showSettings) {
        SettingsSheet(
            config = state.config,
            themeSelection = state.themeSelection,
            onThemeChange = { viewModel.updateTheme(it) },
            onAccentChange = { viewModel.updateAccentColor(it) },
            onPickWallpaper = { wallpaperPicker.launch("image/*") },
            onWallpaperChange = { viewModel.updateWallpaper(it) },
            onWallpaperBlurChange = { viewModel.updateWallpaperBlur(it) },
            onWallpaperBlurPreview = { liveBlur = it },
            onVeilChange = { viewModel.updateVeil(it) },
            onVeilPreview = { liveVeil = it },
            wallpaperBehindBars = wallpaperBehindBars,
            onWallpaperBehindBarsChange = { viewModel.updateWallpaperBehindBars(it) },
            savedThemes = savedThemes,
            onSaveTheme = { viewModel.saveTheme(it) },
            onApplyTheme = { viewModel.applyTheme(it) },
            onDeleteTheme = { viewModel.deleteTheme(it) },
            onResetAppearance = { viewModel.resetAppearance() },
            onFontSizeChange = { viewModel.updateFontSize(it) },
            onLineHeightChange = { viewModel.updateLineHeight(it) },
            onFontFamilyChange = { viewModel.updateFontFamily(it) },
            onAutoScrollSpeedChange = { viewModel.updateAutoScrollSpeed(it) },
            onKeepScreenOnChange = { viewModel.updateKeepScreenOn(it) },
            deviceBrightness = deviceBrightness,
            onBrightnessChange = { viewModel.updateBrightness(it) },
            onBrightnessPreview = { liveBrightness = it },
            onSwipeDirectionChange = { viewModel.updateSwipeDirection(it) },
            onDismiss = { viewModel.hideSettings() }
        )
    }

    if (showChapterList && state.allChapters.isNotEmpty()) {
        val chapterListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filtered = remember(chapterSearchQuery, state.allChapters) {
            filterChaptersByQuery(state.allChapters, chapterSearchQuery)
        }
        val displayList = chapterListDisplayOrder(filtered, reverseChapterOrder)
        ModalBottomSheet(
            onDismissRequest = {
                chapterSearchQuery = ""
                showChapterList = false
            },
            sheetState = chapterListSheetState
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = chapterSearchQuery,
                    onValueChange = { chapterSearchQuery = it },
                    placeholder = { Text(stringResource(R.string.chapter_list_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (chapterSearchQuery.isNotBlank())
                            stringResource(R.string.chapters_count_filtered, filtered.size, state.allChapters.size)
                        else
                            stringResource(R.string.chapters_count, state.allChapters.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    IconButton(onClick = { reverseChapterOrder = !reverseChapterOrder }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.chapter_list_reverse_order),
                            tint = if (reverseChapterOrder) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ChapterListSheet(
                    chapters = displayList,
                    currentChapterId = state.chapter?.id,
                    novelTitle = state.novel?.title,
                    onChapterClick = { chapterId ->
                        saveScroll {
                            viewModel.loadChapter(chapterId, restorePosition = true)
                            chapterSearchQuery = ""
                            showChapterList = false
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val readerWallpaperActive = state.config.wallpaper != PreferenceAllowlists.WALLPAPER_NONE
    val readerBarColor = barColorFor(
        surface = MaterialTheme.colorScheme.surface,
        wallpaperActive = readerWallpaperActive,
        behindBars = wallpaperBehindBars
    )

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground(
            ref = state.config.wallpaper,
            blur = liveBlur,
            veil = liveVeil,
            veilColor = parseAccentHex(readerSurfaceOf(state.config).bg) ?: Color.Black,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            containerColor = if (readerWallpaperActive) Color.Transparent
            else MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ReaderTopBar(
                    title = com.novelreader.data.parser.TitleExtractor.cleanChapterTitleForDisplay(
                        state.chapter?.title ?: "",
                        state.novel?.title
                    ),
                    isSearchActive = state.isSearchActive,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                    onBack = { saveScroll { onBack() } },
                    onCloseSearch = { viewModel.deactivateSearch() },
                    onActivateSearch = { viewModel.activateSearch() },
                    containerColor = readerBarColor
                )
            },
            bottomBar = {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    AnimatedVisibility(
                        visible = isOptionsVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        BottomAppBar(
                            containerColor = readerBarColor,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        saveScroll { viewModel.goToPrevChapter() }
                                    },
                                    enabled = state.prevChapterId != null
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBackIos,
                                        contentDescription = stringResource(R.string.previous)
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(onClick = {
                                    webView?.evaluateJavascript(
                                        bookmarkCaptureRatioJs(),
                                        ValueCallback { value ->
                                            val ratio = value?.trim('"')?.toFloatOrNull() ?: 0f
                                            viewModel.saveScrollPosition(ratio)
                                            viewModel.showBookmarkDialog()
                                        }
                                    ) ?: viewModel.showBookmarkDialog()
                                }) {
                                    Box {
                                        Icon(
                                            Icons.Default.Bookmark,
                                            contentDescription = stringResource(R.string.bookmarks),
                                            tint = if (state.bookmarks.isNotEmpty())
                                                MaterialTheme.colorScheme.secondary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (state.bookmarks.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .align(Alignment.TopEnd)
                                                    .background(
                                                        MaterialTheme.colorScheme.secondary,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${state.bookmarks.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(onClick = { viewModel.showSettings() }) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.settings)
                                    )
                                }

                                IconButton(onClick = { showChapterList = true }) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = stringResource(R.string.chapter_list)
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(
                                    onClick = {
                                        saveScroll { viewModel.goToNextChapter() }
                                    },
                                    enabled = state.nextChapterId != null
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = stringResource(R.string.next)
                                    )
                                }
                            }
                        }
                    }
                    ReaderStatusBar(
                        battery = rememberBatteryState(),
                        containerColor = readerBarColor
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    state.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.error ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { saveScroll { onBack() } }) {
                                    Text(stringResource(R.string.back))
                                }
                            }
                        }
                    }
                    state.isEmpty -> {
                        EmptyChapterState(
                            onImportMht = { viewModel.importMhtForChapter(it) },
                            onBack = { saveScroll { onBack() } }
                        )
                    }
                    else -> {
                        ReaderWebView(
                        onScrollChanged = { ratio ->
                            if (isPageLoaded) {
                                scrollRatio = ratio
                                viewModel.updateLiveScroll(ratio)
                            }
                        },
                        onPageFinished = { wv, url ->
                            if (!loadToken.shouldAccept(url)) return@ReaderWebView
                            val restoreToken = pendingRestoreToken ?: return@ReaderWebView
                            val ratio = viewModel.getScrollRatio()
                            scrollRatio = ratio
                            wv.evaluateJavascript(applyConfigJs(state.config), null)
                            wv.evaluateJavascript(applyBookmarksJs(state.bookmarks), null)
                            val search = pendingSearchQuery
                            if (search != null) {
                                pendingSearchQuery = null
                                wv.evaluateJavascript(buildJs(
                                    code = "window.scrollTo(0, 0);",
                                    params = emptyMap()
                                ), null)
                                wv.evaluateJavascript(
                                    searchHighlightJs(search, ratio, completionToken = restoreToken),
                                    null
                                )
                            } else {
                                wv.evaluateJavascript(scrollRestoreJs(ratio, completionToken = restoreToken), null)
                            }
                        },
                        onWebViewReady = { webView = it },
                        onScrollRestoreComplete = { token ->
                            if (token == pendingRestoreToken) isPageLoaded = true
                        },
                        onTap = { isOptionsVisible = !isOptionsVisible },
                        onSwipe = { direction, axis ->
                            pendingSwipeTransition = chapterTransitionFor(direction, axis)
                            val navigate: () -> Unit = {
                                if (direction == "prev") {
                                    viewModel.goToPrevChapter()
                                } else {
                                    viewModel.goToNextChapter()
                                }
                            }
                            when {
                                axis == "v" && direction == "next" -> { viewModel.saveScrollPosition(1f); navigate() }
                                axis == "v" && direction == "prev" -> { viewModel.saveScrollPosition(0f); navigate() }
                                else -> saveScroll(navigate)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (state.isSearchActive) {
                        SearchResultsPanel(
                            query = state.searchQuery,
                            results = state.searchResults,
                            onResultClick = { chapter, query ->
                                viewModel.deactivateSearch()
                                onChapterChange(chapter.id, query)
                            },
                            onClose = { viewModel.deactivateSearch() }
                        )
                    }
                    }
                }
            }
        }
        pendingCrop?.let { cropUri ->
            WallpaperCropOverlay(
                imageModel = cropUri,
                title = state.chapter?.title.orEmpty().ifBlank { stringResource(R.string.library) },
                topBarColor = readerBarColor,
                bottomBarColor = readerBarColor,
                veilColor = parseAccentHex(readerSurfaceOf(state.config).bg),
                veilAlpha = liveVeil / 100f,
                onApply = { crop, width, height ->
                    viewModel.applyCrop(crop, width, height)
                },
                onSkipCrop = {
                    viewModel.importWallpaper(cropUri)
                    viewModel.cancelCrop()
                },
                onCancel = { viewModel.cancelCrop() }
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onReaderPaused()
                    saveScroll()
                    webView?.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webView?.onResume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.destroy()
        }
    }
}
