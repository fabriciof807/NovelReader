package com.novelreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.db.entity.ChapterEntity

@Composable
fun SearchResultsPanel(
    query: String,
    results: List<ChapterEntity>,
    onResultClick: (ChapterEntity, String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (results.isEmpty() && query.length >= 3) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.search_no_results, query),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(results, key = { it.id }) { chapter ->
                    SearchResultItem(
                        chapter = chapter,
                        query = query,
                        onClick = { onResultClick(chapter, query) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    chapter: ChapterEntity,
    query: String,
    onClick: () -> Unit
) {
    val searchData = remember(chapter, query) {
        val plain = org.jsoup.Jsoup.parseBodyFragment(chapter.content).text()
        SearchData(
            count = countOccurrences(plain, query),
            snippet = extractSnippet(plain, query)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.search_matches_count, searchData.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildHighlightedText(searchData.snippet, query, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

private data class SearchData(val count: Int, val snippet: String)

private fun countOccurrences(text: String, keyword: String): Int {
    if (keyword.isBlank()) return 0
    var count = 0
    var index = 0
    while (true) {
        index = text.indexOf(keyword, index, ignoreCase = true)
        if (index < 0) break
        count++
        index += keyword.length
    }
    return count
}

private fun extractSnippet(text: String, keyword: String, contextChars: Int = 80): String {
    val idx = text.indexOf(keyword, ignoreCase = true)
    if (idx < 0) return text.take(contextChars * 2)
    val start = (idx - contextChars).coerceAtLeast(0)
    val end = (idx + keyword.length + contextChars).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
}

private fun buildHighlightedText(
    text: String,
    keyword: String,
    highlightColor: Color
) = buildAnnotatedString {
    var currentIndex = 0
    while (currentIndex < text.length) {
        val matchIndex = text.indexOf(keyword, currentIndex, ignoreCase = true)
        if (matchIndex < 0) {
            append(text.substring(currentIndex))
            break
        }
        append(text.substring(currentIndex, matchIndex))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
            append(text.substring(matchIndex, matchIndex + keyword.length))
        }
        currentIndex = matchIndex + keyword.length
    }
}
