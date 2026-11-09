package com.novelreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelreader.R
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.ui.customization.libraryContainerColor
import com.novelreader.ui.library.components.NovelImportOverlay
import com.novelreader.ui.theme.AppShapes
import com.novelreader.ui.theme.pressable

const val NOVEL_COVER_TITLE_TAG = "novelCoverTitle"
const val NOVEL_COVER_PROGRESS_TAG = "novelCoverProgress"

private const val CoverAspectRatio = 3f / 4f
private const val TitleMaxLines = 2
internal const val OverlayScrimAlpha = 0.6f

@Composable
fun NovelCover(
    novel: NovelEntity,
    readProgress: Float,
    newChapterCount: Int,
    importOverlay: NovelImportOverlay,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    wallpaperActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val titleStyle = MaterialTheme.typography.bodySmall
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val titleBlockHeight = twoLineHeight(titleStyle, density.fontScale)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(scheme.surface)
            .pressable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        NovelCoverImage(
            novel = novel,
            readProgress = readProgress,
            newChapterCount = newChapterCount,
            importOverlay = importOverlay,
            wallpaperActive = wallpaperActive,
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleBlockHeight)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = novel.title,
                style = titleStyle.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = TitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(NOVEL_COVER_TITLE_TAG)
            )
        }
    }
}

private fun twoLineHeight(style: TextStyle, fontScale: Float) = with(style) {
    val lineHeight = if (this.lineHeight.isSp) this.lineHeight.value else fontSize.value * 1.4f
    (lineHeight * fontScale * TitleMaxLines).dp
}

@Composable
fun NovelCoverImage(
    novel: NovelEntity,
    readProgress: Float,
    newChapterCount: Int,
    importOverlay: NovelImportOverlay,
    wallpaperActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val placeholderColor = libraryContainerColor(
        default = scheme.surfaceVariant,
        surface = scheme.surface,
        wallpaperActive = wallpaperActive
    )

    Box(
        modifier = modifier
            .aspectRatio(CoverAspectRatio)
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        if (novel.coverPath != null) {
            AsyncImage(
                model = novel.coverPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = novel.title.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary
            )
        }

        CoverImportOverlay(importOverlay)
        NewChaptersBadge(hasUpdates = novel.hasUpdates, count = newChapterCount)

        if (readProgress > 0f) {
            LinearProgressIndicator(
                progress = { readProgress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .testTag(NOVEL_COVER_PROGRESS_TAG),
                color = scheme.primary,
                trackColor = scheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun CoverImportOverlay(importOverlay: NovelImportOverlay) {
    when (importOverlay) {
        NovelImportOverlay.None -> Unit
        NovelImportOverlay.Queued -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = OverlayScrimAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.import_queued_label),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }

        is NovelImportOverlay.Importing -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = OverlayScrimAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.importing),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (importOverlay.total > 0) {
                            importOverlay.imported.toFloat() / importOverlay.total
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${importOverlay.imported} / ${importOverlay.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun BoxScope.NewChaptersBadge(hasUpdates: Boolean, count: Int) {
    if (!hasUpdates) return

    val updatesLabel = stringResource(R.string.library_new_chapters_badge)
    if (count > 0) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { contentDescription = updatesLabel },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    } else {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .semantics { contentDescription = updatesLabel }
        )
    }
}
