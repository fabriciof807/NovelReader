package com.novelreader.data.storage

import kotlin.math.pow

const val LIGHT_WALLPAPER_LUMINANCE = 0.5f

fun relativeLuminance(argb: Int): Float {
    val r = linearChannel((argb shr 16) and 0xFF)
    val g = linearChannel((argb shr 8) and 0xFF)
    val b = linearChannel(argb and 0xFF)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private fun linearChannel(value: Int): Float {
    val channel = value / 255f
    return if (channel <= 0.03928f) channel / 12.92f
    else ((channel + 0.055f) / 1.055f).pow(2.4f)
}

fun averageLuminance(pixels: IntArray): Float {
    var total = 0f
    var counted = 0
    pixels.forEach { pixel ->
        if (pixel ushr 24 != 0) {
            total += relativeLuminance(pixel)
            counted++
        }
    }
    return if (counted == 0) 0f else total / counted
}

fun isLightWallpaper(luminance: Float): Boolean = luminance >= LIGHT_WALLPAPER_LUMINANCE

fun sampleSizeFor(width: Int, height: Int, targetEdge: Int): Int {
    val target = targetEdge.coerceAtLeast(1)
    val longestEdge = maxOf(width, height)
    var size = 1
    while (longestEdge / size > target) size *= 2
    return size
}
