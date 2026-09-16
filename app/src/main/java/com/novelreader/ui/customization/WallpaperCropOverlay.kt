package com.novelreader.ui.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelreader.R
import com.novelreader.data.storage.MAX_CROP_ZOOM
import com.novelreader.data.storage.MIN_CROP_ZOOM
import com.novelreader.data.storage.WallpaperCrop
import com.novelreader.data.storage.clampCrop
import com.novelreader.data.storage.clampZoom
import com.novelreader.data.storage.coverScale
import kotlin.math.max

const val CROP_PREVIEW_TAG = "wallpaper_crop_preview"

@Composable
fun WallpaperCropOverlay(
    imageModel: Any?,
    title: String,
    topBarColor: Color,
    bottomBarColor: Color,
    onApply: (WallpaperCrop, Int, Int) -> Unit,
    onSkipCrop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    veilColor: Color? = null,
    veilAlpha: Float = 0f
) {
    var crop by remember { mutableStateOf(WallpaperCrop()) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val screenWidthPx = (configuration.screenWidthDp * density).toInt().coerceAtLeast(1)
    val screenHeightPx = (configuration.screenHeightDp * density).toInt().coerceAtLeast(1)
    val screenAspect = screenWidthPx.toFloat() / screenHeightPx.toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.crop_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.crop_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CropPreview(
                imageModel = imageModel,
                crop = crop,
                screenAspect = screenAspect,
                title = title,
                topBarColor = topBarColor,
                bottomBarColor = bottomBarColor,
                veilColor = veilColor,
                veilAlpha = veilAlpha,
                onCropChange = { crop = it }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.crop_zoom, (crop.zoom * 100).toInt()),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = crop.zoom,
            onValueChange = { crop = clampCrop(it, crop.panX, crop.panY) },
            valueRange = MIN_CROP_ZOOM..MAX_CROP_ZOOM,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            TextButton(onClick = onSkipCrop) {
                Text(stringResource(R.string.crop_skip))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { onApply(crop, screenWidthPx, screenHeightPx) }) {
                Text(stringResource(R.string.crop_apply))
            }
        }
    }
}

@Composable
private fun CropPreview(
    imageModel: Any?,
    crop: WallpaperCrop,
    screenAspect: Float,
    title: String,
    topBarColor: Color,
    bottomBarColor: Color,
    veilColor: Color?,
    veilAlpha: Float,
    onCropChange: (WallpaperCrop) -> Unit
) {
    var sourceSize by remember { mutableStateOf<IntSize?>(null) }
    // The gesture handler outlives recompositions, so it must read the current crop, not the one it
    // was built with: a stale read made a drag undo the chosen zoom and never accumulate the pan.
    val latestCrop by rememberUpdatedState(crop)

    Box(
        modifier = Modifier
            .aspectRatio(screenAspect)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clipToBounds()
            .testTag(CROP_PREVIEW_TAG)
            .pointerInput(sourceSize) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val base = latestCrop
                    val zoom = clampZoom(base.zoom * gestureZoom)
                    val slack = previewSlack(
                        boxWidth = size.width.toFloat(),
                        boxHeight = size.height.toFloat(),
                        sourceSize = sourceSize,
                        zoom = zoom
                    )
                    onCropChange(
                        clampCrop(
                            zoom = zoom,
                            panX = if (slack.x > 0f) base.panX + pan.x / slack.x else base.panX,
                            panY = if (slack.y > 0f) base.panY + pan.y / slack.y else base.panY
                        )
                    )
                }
            }
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            onSuccess = { state ->
                val drawable = state.result.drawable
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    sourceSize = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val slack = previewSlack(
                        boxWidth = size.width,
                        boxHeight = size.height,
                        sourceSize = sourceSize,
                        zoom = crop.zoom
                    )
                    scaleX = crop.zoom
                    scaleY = crop.zoom
                    translationX = crop.panX * slack.x
                    translationY = crop.panY * slack.y
                }
        )

        if (veilColor != null && veilAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(veilColor.copy(alpha = veilAlpha))
            )
        }

        PreviewTopBar(title = title, containerColor = topBarColor)
        PreviewBottomBar(containerColor = bottomBarColor)
    }
}

/** Travel, in preview pixels, that the zoomed image has on each axis before it runs out of image. */
private fun previewSlack(
    boxWidth: Float,
    boxHeight: Float,
    sourceSize: IntSize?,
    zoom: Float
): Offset {
    if (sourceSize == null || boxWidth <= 0f || boxHeight <= 0f) return Offset.Zero
    if (sourceSize.width <= 0 || sourceSize.height <= 0) return Offset.Zero
    val scale = coverScale(
        srcWidth = sourceSize.width.toFloat(),
        srcHeight = sourceSize.height.toFloat(),
        targetWidth = boxWidth,
        targetHeight = boxHeight
    ) * zoom
    return Offset(
        x = max(0f, (sourceSize.width * scale - boxWidth) / 2f),
        y = max(0f, (sourceSize.height * scale - boxHeight) / 2f)
    )
}

@Composable
private fun PreviewTopBar(title: String, containerColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(containerColor)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PreviewBottomBar(containerColor: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(containerColor)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(R.string.novels, R.string.chapters, R.string.favorites).forEach { label ->
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
