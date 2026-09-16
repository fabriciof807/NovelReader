package com.novelreader.data.storage

import kotlin.math.max
import kotlin.math.min

const val MIN_CROP_ZOOM = 1f
const val MAX_CROP_ZOOM = 4f

/**
 * How the picked image sits inside the crop frame.
 *
 * [panX] and [panY] are fractions of the available slack (-1..1), not pixels, so the same crop
 * means the same thing whatever size the crop frame is drawn at.
 */
data class WallpaperCrop(
    val zoom: Float = MIN_CROP_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f
)

fun coverScale(srcWidth: Float, srcHeight: Float, targetWidth: Float, targetHeight: Float): Float =
    max(targetWidth / srcWidth, targetHeight / srcHeight)

fun clampCrop(zoom: Float, panX: Float, panY: Float): WallpaperCrop = WallpaperCrop(
    zoom = zoom.coerceIn(MIN_CROP_ZOOM, MAX_CROP_ZOOM),
    panX = panX.coerceIn(-1f, 1f),
    panY = panY.coerceIn(-1f, 1f)
)

fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_CROP_ZOOM, MAX_CROP_ZOOM)

/** Slack, in source pixels, between the scaled image and the crop frame on each axis. */
fun cropSlack(
    srcWidth: Float,
    srcHeight: Float,
    targetWidth: Float,
    targetHeight: Float,
    zoom: Float
): Pair<Float, Float> {
    val scale = coverScale(srcWidth, srcHeight, targetWidth, targetHeight) * clampZoom(zoom)
    return max(0f, srcWidth * scale - targetWidth) / 2f to
        max(0f, srcHeight * scale - targetHeight) / 2f
}

data class CropRegion(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** Region of the source image the crop frame keeps, in source pixels. */
fun cropRectFor(
    crop: WallpaperCrop,
    srcWidth: Float,
    srcHeight: Float,
    targetWidth: Float,
    targetHeight: Float
): CropRegion {
    val clamped = clampCrop(crop.zoom, crop.panX, crop.panY)
    val scale = coverScale(srcWidth, srcHeight, targetWidth, targetHeight) * clamped.zoom
    val (slackX, slackY) = cropSlack(srcWidth, srcHeight, targetWidth, targetHeight, clamped.zoom)

    val width = (targetWidth / scale).coerceAtMost(srcWidth)
    val height = (targetHeight / scale).coerceAtMost(srcHeight)
    val left = (srcWidth - width) / 2f + clamped.panX * slackX
    val top = (srcHeight - height) / 2f + clamped.panY * slackY

    val clampedLeft = left.coerceIn(0f, max(0f, srcWidth - width))
    val clampedTop = top.coerceIn(0f, max(0f, srcHeight - height))
    return CropRegion(
        left = clampedLeft,
        top = clampedTop,
        width = min(clampedLeft + width, srcWidth) - clampedLeft,
        height = min(clampedTop + height, srcHeight) - clampedTop
    )
}

/** Power-of-two sample size that keeps the decoded bitmap within [maxEdge]. */
fun decodeSampleSize(srcWidth: Int, srcHeight: Int, maxEdge: Int): Int {
    var sample = 1
    while (max(srcWidth, srcHeight) / sample > maxEdge) {
        sample *= 2
    }
    return sample
}
