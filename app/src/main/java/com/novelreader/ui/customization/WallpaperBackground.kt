package com.novelreader.ui.customization

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.storage.WallpaperStorage

const val WALLPAPER_TAG = "wallpaper_background"
const val WALLPAPER_VEIL_TAG = "wallpaper_veil"

val BUILTIN_WALLPAPERS: Map<String, List<Color>> = mapOf(
    "amanhecer" to listOf(Color(0xFFFDCB82), Color(0xFFF98E5A), Color(0xFFC9557F)),
    "aurora" to listOf(Color(0xFF4A148C), Color(0xFF1B7A8C), Color(0xFF66BB6A)),
    "crepusculo" to listOf(Color(0xFF2B1055), Color(0xFF7597DE), Color(0xFFF3B7C8)),
    "bosque" to listOf(Color(0xFF0B3D2E), Color(0xFF1E6F5C), Color(0xFFA3C9A8)),
    "carvao" to listOf(Color(0xFF232526), Color(0xFF414345), Color(0xFF6B6F72)),
    "noite" to listOf(Color(0xFF0B1026), Color(0xFF1B2A4A), Color(0xFF3A4A6B)),
    "oceano" to listOf(Color(0xFF04395E), Color(0xFF1B6CA8), Color(0xFF5BC0BE)),
    "pergaminho" to listOf(Color(0xFFF5F0E8), Color(0xFFE8DCC8), Color(0xFFD9C7A7))
)

@Composable
fun WallpaperBackground(
    ref: String,
    blur: Int,
    modifier: Modifier = Modifier,
    veil: Int = 0,
    veilColor: Color = Color.Black
) {
    if (ref == PreferenceAllowlists.WALLPAPER_NONE) return
    Box(modifier) {
        WallpaperLayer(ref = ref, blur = blur, modifier = Modifier.matchParentSize())
        if (veil > 0) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .testTag(WALLPAPER_VEIL_TAG)
                    .background(veilColor.copy(alpha = veil / 100f))
            )
        }
    }
}

@Composable
private fun WallpaperLayer(ref: String, blur: Int, modifier: Modifier) {
    val builtin = WallpaperStorage.builtinId(ref)
    if (builtin != null) {
        val colors = BUILTIN_WALLPAPERS[builtin] ?: BUILTIN_WALLPAPERS.getValue("noite")
        Box(
            modifier = modifier
                .testTag(WALLPAPER_TAG)
                .background(Brush.linearGradient(colors))
        )
        return
    }

    val context = LocalContext.current
    val file = WallpaperStorage.resolveFile(context.filesDir, ref) ?: return
    val softEdge = if (blur > 0) Modifier.blur(blur.dp) else Modifier

    BoxWithConstraints(modifier = modifier.testTag(WALLPAPER_TAG)) {
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val downsample = if (blur > 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            1f + blur / 12f
        } else {
            1f
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .size(
                    (width / downsample).toInt().coerceAtLeast(16),
                    (height / downsample).toInt().coerceAtLeast(16)
                )
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
            modifier = Modifier
                .fillMaxSize()
                .then(softEdge)
        )
    }
}
