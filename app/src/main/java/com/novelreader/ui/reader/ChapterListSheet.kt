package com.novelreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.parser.TitleExtractor

/**
 * The body of the reader's chapter-list sheet: the list itself and the "no matches" state the search
 * leaves behind.
 *
 * It owns the list state and the scroll to the chapter being read, so the sheet opens where the
 * reader is instead of at the top (`chapterScrollTarget`). The search field and the chapter counter
 * stay in the sheet header, in [ReaderScreen]: a text field keeps Robolectric from ever reporting
 * idle, which would make this behaviour impossible to test from the outside.
 *
 * @param chapters the list as it should be shown, already filtered by the search and ordered.
 */
@Composable
internal fun ChapterListSheet(
    chapters: List<ChapterEntity>,
    currentChapterId: Long?,
    novelTitle: String?,
    onChapterClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val target = chapterScrollTarget(chapters, currentChapterId)
        if (target >= 0) listState.scrollToItem(target)
    }

    if (chapters.isEmpty()) {
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
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        items(chapters, key = { it.id }) { chapter ->
            val isCurrent = chapter.id == currentChapterId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapterClick(chapter.id) }
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TitleExtractor.cleanChapterTitleForDisplay(chapter.title, novelTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent || !chapter.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (chapter.isRead && !isCurrent)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider()
        }
    }
}
