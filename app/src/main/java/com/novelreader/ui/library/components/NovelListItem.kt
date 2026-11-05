package com.novelreader.ui.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.ui.components.NovelCoverImage
import com.novelreader.ui.theme.AppShapes
import com.novelreader.ui.theme.mutedForeground

private val ListCoverWidth = 56.dp
private const val ListTitleMaxLines = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelListItem(
    novel: NovelEntity,
    readProgress: Float = 0f,
    newChapterCount: Int = 0,
    importOverlay: NovelImportOverlay = NovelImportOverlay.None,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowMenu: () -> Unit = {},
    wallpaperActive: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val containerColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
            ),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelCoverImage(
                novel = novel,
                readProgress = readProgress,
                newChapterCount = newChapterCount,
                importOverlay = importOverlay,
                wallpaperActive = wallpaperActive,
                modifier = Modifier.width(ListCoverWidth)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = ListTitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.chapters_count, novel.totalChapters),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedForeground(MaterialTheme.colorScheme.onSurface, containerColor)
                )
            }
            IconButton(onClick = onShowMenu) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.options)
                )
            }
        }
    }
}
