package com.novelreader.ui.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.theme.AppPalette
import com.novelreader.ui.theme.parseAccentHex

private const val SWATCH_SIZE = 44

const val READER_AUTO = "auto"

data class PaletteChoice(
    val id: String,
    val labelRes: Int,
    val background: Color,
    val primary: Color
)

@Composable
fun appPaletteChoices(dark: Boolean, allowDynamic: Boolean): List<PaletteChoice> {
    val choices = AppPalette.entries.map { palette ->
        val scheme = if (dark) palette.dark else palette.light
        PaletteChoice(palette.id, paletteLabelRes(palette), scheme.background, scheme.primary)
    }
    if (!allowDynamic) return choices
    val fallback = if (dark) AppPalette.DEFAULT.dark else AppPalette.DEFAULT.light
    return choices + PaletteChoice(
        id = PreferenceAllowlists.PALETTE_DYNAMIC,
        labelRes = R.string.palette_dynamic,
        background = fallback.background,
        primary = fallback.primary
    )
}

@Composable
fun readerPaletteChoices(
    dark: Boolean,
    autoBackground: Color,
    autoPrimary: Color
): List<PaletteChoice> {
    val auto = PaletteChoice(
        id = READER_AUTO,
        labelRes = R.string.reader_theme_auto,
        background = autoBackground,
        primary = autoPrimary
    )
    val palettes = AppPalette.entries.map { palette ->
        val surface = palette.readerSurface(dark)
        PaletteChoice(
            id = palette.id,
            labelRes = paletteLabelRes(palette),
            background = parseAccentHex(surface.bg) ?: autoBackground,
            primary = parseAccentHex(surface.accent) ?: autoPrimary
        )
    }
    return listOf(auto) + palettes
}

private fun paletteLabelRes(palette: AppPalette): Int = when (palette) {
    AppPalette.INDIGO -> R.string.palette_indigo
    AppPalette.PAPEL -> R.string.palette_papel
    AppPalette.GRAFITE -> R.string.palette_grafite
    AppPalette.FLORESTA -> R.string.palette_floresta
    AppPalette.AMEIXA -> R.string.palette_ameixa
    AppPalette.AMOLED -> R.string.palette_amoled
}

@Composable
fun PalettePicker(
    selectedId: String,
    choices: List<PaletteChoice>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        choices.forEach { choice ->
            PaletteSwatch(
                choice = choice,
                selected = choice.id == selectedId,
                onClick = { onSelect(choice.id) }
            )
        }
    }
}

@Composable
private fun PaletteSwatch(
    choice: PaletteChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(choice.labelRes)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(SWATCH_SIZE.dp)
                .clip(CircleShape)
                .background(choice.background)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape
                )
        ) {
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE.dp / 2)
                    .clip(CircleShape)
                    .background(choice.primary)
                    .semantics { contentDescription = label }
            )
        }
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
