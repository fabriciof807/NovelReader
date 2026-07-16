package com.novelreader.ui.reader

import android.net.Uri
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    var showCreateCharacterDialog by remember { mutableStateOf(false) }
    var pendingCharacterPhoto by remember { mutableStateOf<Uri?>(null) }
    var lastInitialSearchQuery by remember { mutableStateOf(initialSearchQuery) }
    val pendingSearchQueryState = remember { mutableStateOf(initialSearchQuery) }
    var pendingSearchQuery by pendingSearchQueryState
    var isPageLoaded by remember { mutableStateOf(false) }
    val loadToken = remember { LoadToken() }
    var inflightToken by remember { mutableStateOf<Int?>(null) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkToDelete by remember { mutableStateOf<Long?>(null) }
    var showChapterList by remember { mutableStateOf(false) }
    var chapterSearchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    LaunchedEffect(state.config.keepScreenOn) {
        view.keepScreenOn = state.config.keepScreenOn
    }

    if (initialSearchQuery != lastInitialSearchQuery) {
        lastInitialSearchQuery = initialSearchQuery
        pendingSearchQuery = initialSearchQuery
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) pendingCharacterPhoto = uri
    }

    fun saveScroll() {
        viewModel.saveScrollPosition()
    }

    LaunchedEffect(state.chapter, webView) {
        state.chapter?.let { chapter ->
            webView?.let { wv ->
                isPageLoaded = false
                val issued = loadToken.next()
                inflightToken = issued
                val html = buildReaderHtml(
                    content = chapter.content,
                    config = state.config
                )
                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
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
                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
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

    if (showCreateCharacterDialog) {
        CreateCharacterDialog(
            selectedName = state.selectedText,
            photoUri = pendingCharacterPhoto,
            onPhotoPick = { photoPickerLauncher.launch("image/*") },
            onDismiss = {
                showCreateCharacterDialog = false
                pendingCharacterPhoto = null
            },
            onCreate = { name ->
                viewModel.createCharacter(name, pendingCharacterPhoto?.toString())
                showCreateCharacterDialog = false
                pendingCharacterPhoto = null
            }
        )
    }

    if (showChapterList && state.allChapters.isNotEmpty()) {
        val chapterListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filtered = remember(chapterSearchQuery, state.allChapters) {
            filterChaptersByQuery(state.allChapters, chapterSearchQuery)
        }
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
                Text(
                    text = if (chapterSearchQuery.isNotBlank())
                        stringResource(R.string.chapters_count_filtered, filtered.size, state.allChapters.size)
                    else
                        stringResource(R.string.chapters_count, state.allChapters.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
                        items(filtered, key = { it.id }) { chapter ->
                            val isCurrent = chapter.id == state.chapter?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        saveScroll()
                                        viewModel.loadChapter(chapter.id)
                                        chapterSearchQuery = ""
                                        showChapterList = false
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
                                saveScroll()
                                onBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (!state.isSearchActive) {
                            IconButton(onClick = { haptic?.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.activateSearch() }) {
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
                                    haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                                    saveScroll()
                                    state.prevChapterId?.let { viewModel.loadChapter(it) }
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
                                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
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

                            IconButton(onClick = { haptic?.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.showSettings() }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings)
                                )
                            }

                            IconButton(onClick = { haptic?.performHapticFeedback(HapticFeedbackType.LongPress); showChapterList = true }) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = stringResource(R.string.chapter_list)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                                    saveScroll()
                                    state.nextChapterId?.let { viewModel.loadChapter(it) }
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
                            Button(onClick = { saveScroll(); onBack() }) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    }
                }
                state.isEmpty -> {
                    EmptyChapterState(
                        onImportMht = { viewModel.importMhtForChapter(it) },
                        onBack = { saveScroll(); onBack() }
                    )
                }
                else -> {
                    ReaderWebView(
                    onTextSelected = { viewModel.onTextSelected(it) },
                    onScrollChanged = { ratio ->
                        scrollRatio = ratio
                        viewModel.updateLiveScroll(ratio)
                    },
                    onPageFinished = { wv, _ ->
                        val issued = inflightToken
                        inflightToken = null
                        if (issued != null && loadToken.shouldAccept(issued)) {
                            isPageLoaded = true
                            wv.evaluateJavascript(applyConfigJs(state.config), null)
                            wv.evaluateJavascript(applyBookmarksJs(state.bookmarks), null)
                            val ratio = viewModel.getScrollRatio()
                            val search = pendingSearchQuery
                            if (search != null) {
                                pendingSearchQuery = null
                                wv.evaluateJavascript(buildJs(
                                    code = "window.scrollTo(0, 0);",
                                    params = emptyMap()
                                ), null)
                                wv.evaluateJavascript(
                                    searchHighlightJs(search, ratio),
                                    null
                                )
                            } else if (ratio > 0f) {
                                wv.evaluateJavascript(scrollRestoreJs(ratio), null)
                            }
                        }
                    },
                    onWebViewReady = { webView = it },
                    onTap = { isControlsVisible = !isControlsVisible },
                    onSwipe = { direction ->
                        saveScroll()
                        if (direction == "prev") {
                            state.prevChapterId?.let { viewModel.loadChapter(it) }
                        } else {
                            state.nextChapterId?.let { viewModel.loadChapter(it) }
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

                if (state.selectedText.isNotBlank() && !state.isSearchActive) {
                    FloatingActionButton(
                        onClick = { showCreateCharacterDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = stringResource(R.string.create_character)
                        )
                    }
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
                    viewModel.saveScrollPosition()
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
