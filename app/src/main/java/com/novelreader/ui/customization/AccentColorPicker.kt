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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.novelreader.R
import com.novelreader.ui.theme.accentHexFor
import com.novelreader.ui.theme.hsvOf
import com.novelreader.ui.theme.parseAccentHex

val ACCENT_PRESETS = listOf(
    "#1a237e", "#ff6f00", "#00695c", "#2e7d32",
    "#c62828", "#6a1b5a", "#8d6e63", "#37474f"
)

@Composable
fun AccentColorPicker(
    selected: String?,
    background: Color,
    fallback: Color,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = parseAccentHex(selected) ?: fallback
    var hue by remember { mutableFloatStateOf(hsvOf(selectedColor).hue) }
    var saturation by remember {
        mutableIntStateOf((hsvOf(selectedColor).saturation * 100f).roundToInt())
    }
    var emitted by remember { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        if (selected != emitted) {
            val hsv = hsvOf(parseAccentHex(selected) ?: fallback)
            hue = hsv.hue
            saturation = (hsv.saturation * 100f).roundToInt()
            emitted = selected
        }
    }

    fun emit(hex: String) {
        emitted = hex
        onSelect(hex)
    }

    val customHex = accentHexFor(hue, saturation, background)
    val customColor = parseAccentHex(customHex) ?: fallback

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentSwatch(
                color = fallback,
                label = stringResource(R.string.accent_color_default),
                selected = selected == null,
                onClick = { onSelect(null) }
            )
            ACCENT_PRESETS.forEach { hex ->
                AccentSwatch(
                    color = parseAccentHex(hex) ?: fallback,
                    label = hex,
                    selected = selected == hex,
                    onClick = { onSelect(hex) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.accent_color_hue),
            style = MaterialTheme.typography.labelMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(customColor)
                    .semantics { contentDescription = customHex }
            )
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    emit(accentHexFor(it, saturation, background))
                },
                valueRange = 0f..360f,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }

        Text(
            text = stringResource(R.string.accent_color_saturation),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = saturation.toFloat(),
            onValueChange = {
                saturation = it.toInt()
                emit(accentHexFor(hue, it.toInt(), background))
            },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}
