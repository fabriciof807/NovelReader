package com.novelreader.ui.customization

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.novelreader.data.storage.WallpaperStorage
import com.novelreader.data.storage.averageLuminance
import com.novelreader.data.storage.isLightWallpaper

/**
 * Whether the wallpaper asks for the palette's light variant. `null` when the tone is not knowable
 * from the reference alone (an image wallpaper: the pixels have to be sampled) or when the reference
 * resolves to nothing, in which case the app's own variant stays.
 */
fun wallpaperIsLight(ref: String): Boolean? {
    val builtin = WallpaperStorage.builtinId(ref) ?: return null
    val colors = BUILTIN_WALLPAPERS[builtin] ?: return null
    return isLightWallpaper(averageLuminance(colors))
}

fun sampledWallpaperIsLight(luminance: Float?): Boolean? = luminance?.let(::isLightWallpaper)

fun averageLuminance(colors: List<Color>): Float = averageLuminance(colors.map { it.toArgb() }.toIntArray())
