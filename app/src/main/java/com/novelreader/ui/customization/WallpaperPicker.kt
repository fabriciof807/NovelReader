package com.novelreader.ui.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.preferences.PreferenceAllowlists

@Composable
fun WallpaperChoiceRow(
    selectedRef: String,
    hasImage: Boolean,
    onPickImage: () -> Unit,
    onSelectBuiltin: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionChip(
                label = stringResource(R.string.wallpaper_pick),
                selected = false,
                onClick = onPickImage
            )
            if (hasImage || selectedRef != PreferenceAllowlists.WALLPAPER_NONE) {
                ActionChip(
                    label = stringResource(R.string.wallpaper_remove),
                    selected = false,
                    onClick = onRemove
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BUILTIN_WALLPAPERS.forEach { (id, colors) ->
                BuiltinSwatch(
                    colors = colors,
                    label = stringResource(builtinWallpaperLabelRes(id)),
                    selected = selectedRef == "builtin:$id",
                    onClick = { onSelectBuiltin("builtin:$id") }
                )
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

@Composable
private fun BuiltinSwatch(
    colors: List<Color>,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape
                )
                .semantics { contentDescription = label }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun builtinWallpaperLabelRes(id: String): Int = when (id) {
    "amanhecer" -> R.string.builtin_wallpaper_amanhecer
    "aurora" -> R.string.builtin_wallpaper_aurora
    "crepusculo" -> R.string.builtin_wallpaper_crepusculo
    "bosque" -> R.string.builtin_wallpaper_bosque
    "carvao" -> R.string.builtin_wallpaper_carvao
    "noite" -> R.string.builtin_wallpaper_noite
    "oceano" -> R.string.builtin_wallpaper_oceano
    else -> R.string.builtin_wallpaper_pergaminho
}

@Composable
fun BlurSlider(
    blur: Int,
    onBlurChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wallpaper_blur, blur),
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = blur.toFloat(),
            onValueChange = { onBlurChange(it.toInt()) },
            valueRange = 0f..PreferenceAllowlists.MAX_BLUR.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun VeilSlider(
    veil: Int,
    onVeilChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wallpaper_veil, veil),
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = veil.toFloat(),
            onValueChange = { onVeilChange(it.toInt()) },
            valueRange = 0f..PreferenceAllowlists.MAX_VEIL.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
