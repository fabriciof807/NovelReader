package com.novelreader.ui.reader

import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novelreader.R
import com.novelreader.data.local.db.entity.BookmarkEntity
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
    var isControlsVisible by remember { mutableStateOf(true) }
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

    LaunchedEffect(state.config.keepScreenOn) {
        view.keepScreenOn = state.config.keepScreenOn
    }

    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    if (initialSearchQuery != lastInitialSearchQuery) {
        lastInitialSearchQuery = initialSearchQuery
        pendingSearchQuery = initialSearchQuery
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
        state.chapter?.let { chapter ->
            webView?.let { wv ->
                val issued = loadToken.next()
                pendingRestoreToken = issued
                scrollRatio = viewModel.getScrollRatio()
                isPageLoaded = false
                val transition = pendingSwipeTransition
                pendingSwipeTransition = ChapterTransition.NONE
                val html = buildReaderHtml(
                    content = chapter.content,
                    config = state.config,
                    transition = transition
                )
                wv.loadDataWithBaseURL(loadToken.baseUrl(issued), html, "text/html", "UTF-8", null)
            }
        }
    }

    LaunchedEffect(state.config, webView) {
        if (!isPageLoaded) return@LaunchedEffect
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
        if (!isPageLoaded) return@LaunchedEffect
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
            onThemeChange = { viewModel.updateTheme(it) },
            onFontSizeChange = { viewModel.updateFontSize(it) },
            onLineHeightChange = { viewModel.updateLineHeight(it) },
            onAutoScrollSpeedChange = { viewModel.updateAutoScrollSpeed(it) },
            onKeepScreenOnChange = { viewModel.updateKeepScreenOn(it) },
            onSwipeDirectionChange = { viewModel.updateSwipeDirection(it) },
            onDismiss = { viewModel.hideSettings() }
        )
    }

    if (showChapterList && state.allChapters.isNotEmpty()) {
        val chapterListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filtered = remember(chapterSearchQuery, state.allChapters) {
            filterChaptersByQuery(state.allChapters, chapterSearchQuery)
        }
        val displayList = if (reverseChapterOrder) filtered.asReversed() else filtered
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
                if (chapterSearchQuery.isNotBlank() && filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.chapter_list_no_matches),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(displayList, key = { it.id }) { chapter ->
                            val isCurrent = chapter.id == state.chapter?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        saveScroll {
                                            viewModel.loadChapter(chapter.id, restorePosition = true)
                                            chapterSearchQuery = ""
                                            showChapterList = false
                                        }
                                    }
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = com.novelreader.data.parser.TitleExtractor.cleanChapterTitleForDisplay(chapter.title, state.novel?.title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent || !chapter.isRead) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (chapter.isRead && !isCurrent)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                TopAppBar(
                    title = {
                        if (state.isSearchActive) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                placeholder = {
                                    Text(stringResource(R.string.search_chapters_hint))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                com.novelreader.data.parser.TitleExtractor.cleanChapterTitleForDisplay(
                                    state.chapter?.title ?: "",
                                    state.novel?.title
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (state.isSearchActive) {
                            IconButton(onClick = { viewModel.deactivateSearch() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        } else {
                            IconButton(onClick = {
                                saveScroll { onBack() }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (!state.isSearchActive) {
                            IconButton(onClick = { viewModel.activateSearch() }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = scrollRatio,
                            modifier = Modifier.weight(1f).height(4.dp)
                        )
                        Text(
                            text = "${(scrollRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surface
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
                    onTap = { isControlsVisible = !isControlsVisible },
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
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
