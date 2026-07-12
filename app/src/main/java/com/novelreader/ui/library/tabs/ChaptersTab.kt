package com.novelreader.ui.library.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.ui.library.ChapterSortOrder
import com.novelreader.ui.library.LibraryViewModel
import com.novelreader.ui.library.components.ScanRangeDialog
import android.net.Uri
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
@Composable
fun ChaptersTab(
    novelId: Long = 0L,
    novelTitle: String? = null,
    chapters: List<ChapterEntity>,
    bookmarkCounts: Map<Long, Int>,
    onChapterClick: (Long) -> Unit,
    sortOrder: ChapterSortOrder = ChapterSortOrder.ASCENDING,
    onToggleSort: () -> Unit = {},
    failedChapters: List<FailedChapterEntity> = emptyList(),
    onRetryFailed: (FailedChapterEntity) -> Unit = {},
    onRetryAllFailed: (Long) -> Unit = {},
    onRetryFailedManually: (FailedChapterEntity, Uri) -> Unit = { _, _ -> },
    onDismissFailed: (FailedChapterEntity) -> Unit = {},
    onScanWeb: (Long) -> Unit = {},
    onScanLocal: (Long, Int, Int) -> Unit = { _, _, _ -> },
    sourceUrlAvailable: Boolean = false,
    maxChapterNumber: Int = 0,
    totalChapters: Int = 0,
    initialScroll: LibraryViewModel.ChaptersScrollState? = null,
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    pendingScrollToFailedNovelId: Long? = null,
    onConsumeScrollToFailed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pendingFilePickForFailed by remember { mutableStateOf<Long?>(null) }
    var showScanDialog by remember { mutableStateOf(false) }
    val listState = remember(novelId) {
        LazyListState(
            firstVisibleItemIndex = initialScroll?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = initialScroll?.firstVisibleItemScrollOffset ?: 0
        )
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .debounce(300)
            .collect { (idx, off) -> onScroll(idx, off) }
    }
    LaunchedEffect(pendingScrollToFailedNovelId, chapters.size, failedChapters.size) {
        val target = pendingScrollToFailedNovelId
        if (target != null && target == novelId && failedChapters.isNotEmpty()) {
            listState.scrollToItem(chapters.size + 1)
            onConsumeScrollToFailed()
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val failedId = pendingFilePickForFailed
        pendingFilePickForFailed = null
        if (uri != null && failedId != null) {
            val failed = failedChapters.firstOrNull { it.id == failedId }
            if (failed != null) onRetryFailedManually(failed, uri)
        }
    }

    if (chapters.isEmpty() && failedChapters.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.no_chapters),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    if (showScanDialog) {
        ScanRangeDialog(
            initialFrom = 1,
            initialTo = (maxOf(totalChapters, maxChapterNumber) + 10).coerceIn(1, 9999),
            onConfirm = { from, to ->
                showScanDialog = false
                onScanLocal(novelId, from, to)
            },
            onDismiss = { showScanDialog = false }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        if (chapters.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (sortOrder == ChapterSortOrder.ASCENDING)
                            stringResource(R.string.chapters_range_asc, chapters.size)
                        else
                            stringResource(R.string.chapters_range_desc, chapters.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.size(4.dp))
                    IconButton(onClick = onToggleSort) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            items(chapters, key = { it.id }) { chapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterClick(chapter.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = com.novelreader.data.parser.TitleExtractor.cleanChapterTitleForDisplay(chapter.title, novelTitle),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (chapter.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = if (chapter.isRead)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    val count = bookmarkCounts[chapter.id] ?: 0
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = stringResource(R.string.favorites),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        if (failedChapters.isNotEmpty()) {
            item {
                HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.failed_chapters_section_title) +
                            " (${failedChapters.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    val retryableCount = failedChapters.count { !it.url.isNullOrBlank() }
                    if (retryableCount > 0) {
                        TextButton(onClick = { onRetryAllFailed(novelId) }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.retry_all_failed, retryableCount))
                        }
                    }
                    IconButton(onClick = {
                        if (sourceUrlAvailable) {
                            onScanWeb(novelId)
                        } else {
                            showScanDialog = true
                        }
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.failed_chapters_scan)
                        )
                    }
                }
            }
            items(failedChapters, key = { it.id }) { failed ->
                FailedChapterRow(
                    failed = failed,
                    onRetry = { onRetryFailed(failed) },
                    onImportFile = {
                        pendingFilePickForFailed = failed.id
                        filePicker.launch(arrayOf("*/*"))
                    },
                    onDismiss = { onDismissFailed(failed) }
                )
                HorizontalDivider()
            }
        } else if (chapters.isNotEmpty()) {
            item {
                HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.failed_chapters_section_title) + " (0)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (sourceUrlAvailable) {
                            onScanWeb(novelId)
                        } else {
                            showScanDialog = true
                        }
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.failed_chapters_scan)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedChapterRow(
    failed: FailedChapterEntity,
    onRetry: () -> Unit,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = failed.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            ErrorTypeBadge(failed.errorType)
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = failed.errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = failed.fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (failed.sourceType == "WEB" && failed.url != null) {
                TextButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.failed_chapters_retry))
                }
            }
            TextButton(onClick = onImportFile) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.failed_chapters_import_file))
            }
            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            ) {
                Text(stringResource(R.string.failed_chapters_dismiss))
            }
        }
    }
}

@Composable
private fun ErrorTypeBadge(errorType: String) {
    val label = when (errorType) {
        FailedChapterErrorType.NETWORK -> stringResource(R.string.error_type_network)
        FailedChapterErrorType.PARSE -> stringResource(R.string.error_type_parse)
        FailedChapterErrorType.IO -> stringResource(R.string.error_type_io)
        FailedChapterErrorType.MISSING_NUMBER -> stringResource(R.string.error_type_missing_number)
        FailedChapterErrorType.EMPTY_CONTENT -> stringResource(R.string.error_type_empty_content)
        else -> stringResource(R.string.error_type_io)
    }
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
