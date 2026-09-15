package com.novelreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.preferences.ReaderConfig
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.local.preferences.SavedTheme
import com.novelreader.data.storage.WallpaperStorage
import com.novelreader.ui.customization.AccentColorPicker
import com.novelreader.ui.customization.BlurSlider
import com.novelreader.ui.customization.VeilSlider
import com.novelreader.ui.customization.WallpaperBehindBarsRow
import com.novelreader.ui.customization.WallpaperChoiceRow
import com.novelreader.ui.customization.PalettePicker
import com.novelreader.ui.customization.READER_AUTO
import com.novelreader.ui.customization.SavedThemesSection
import com.novelreader.ui.customization.readerPaletteChoices
import com.novelreader.ui.theme.parseAccentHex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    config: ReaderConfig,
    themeSelection: String = ReaderTheme.DEFAULT,
    onThemeChange: (String) -> Unit,
    onAccentChange: (String?) -> Unit = {},
    onPickWallpaper: () -> Unit = {},
    onWallpaperChange: (String) -> Unit = {},
    onWallpaperBlurChange: (Int) -> Unit = {},
    onWallpaperBlurPreview: (Int) -> Unit = {},
    onVeilChange: (Int) -> Unit = {},
    onVeilPreview: (Int) -> Unit = {},
    wallpaperBehindBars: Boolean = true,
    onWallpaperBehindBarsChange: (Boolean) -> Unit = {},
    savedThemes: List<SavedTheme> = emptyList(),
    onSaveTheme: (String) -> Unit = {},
    onApplyTheme: (SavedTheme) -> Unit = {},
    onDeleteTheme: (SavedTheme) -> Unit = {},
    onResetAppearance: () -> Unit = {},
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onSwipeDirectionChange: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val readerBackdrop = parseAccentHex(readerSurfaceOf(config).bg)
        ?: MaterialTheme.colorScheme.background
    val readerAccent = parseAccentHex(readerAccentOf(config))
        ?: MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            PalettePicker(
                selectedId = ReaderTheme.paletteId(themeSelection) ?: READER_AUTO,
                choices = readerPaletteChoices(
                    dark = config.themeDark,
                    autoBackground = readerBackdrop,
                    autoPrimary = readerAccent
                ),
                onSelect = { onThemeChange(ReaderTheme.withPalette(themeSelection, it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                stringResource(R.string.reader_variant),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOption(
                    icon = Icons.Default.BrightnessAuto,
                    label = stringResource(R.string.reader_variant_auto),
                    selected = ReaderTheme.variant(themeSelection) == null,
                    onClick = { onThemeChange(ReaderTheme.withVariant(themeSelection, null)) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = Icons.Default.LightMode,
                    label = stringResource(R.string.reader_variant_light),
                    selected = ReaderTheme.variant(themeSelection) == ReaderTheme.LIGHT,
                    onClick = { onThemeChange(ReaderTheme.withVariant(themeSelection, ReaderTheme.LIGHT)) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = Icons.Default.DarkMode,
                    label = stringResource(R.string.reader_variant_dark),
                    selected = ReaderTheme.variant(themeSelection) == ReaderTheme.DARK,
                    onClick = { onThemeChange(ReaderTheme.withVariant(themeSelection, ReaderTheme.DARK)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.accent_color),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            AccentColorPicker(
                selected = config.accentColor,
                background = readerBackdrop,
                fallback = readerAccent,
                onSelect = onAccentChange
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            var fontSize by remember(config.fontSize) {
                mutableFloatStateOf(config.fontSize.toFloat())
            }
            Text(
                stringResource(R.string.font_size, fontSize.toInt()),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = { onFontSizeChange(fontSize.toInt()) },
                valueRange = 14f..40f,
                steps = 12,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            var lineHeight by remember(config.lineHeight) { mutableFloatStateOf(config.lineHeight) }
            Text(
                stringResource(R.string.line_spacing, lineHeight),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = lineHeight,
                onValueChange = { lineHeight = it },
                onValueChangeFinished = { onLineHeightChange(lineHeight) },
                valueRange = 1.2f..2.5f,
                steps = 12,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.reader_keep_screen_on),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = config.keepScreenOn,
                    onCheckedChange = onKeepScreenOnChange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.reader_swipe_direction),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwipeOption(
                    icon = Icons.Default.ArrowUpward,
                    label = stringResource(R.string.reader_swipe_vertical),
                    selected = config.swipeDirection == "vertical",
                    onClick = { onSwipeDirectionChange("vertical") },
                    modifier = Modifier.weight(1f)
                )
                SwipeOption(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = stringResource(R.string.reader_swipe_horizontal),
                    selected = config.swipeDirection == "horizontal",
                    onClick = { onSwipeDirectionChange("horizontal") },
                    modifier = Modifier.weight(1f)
                )
                SwipeOption(
                    icon = Icons.Default.SwapHoriz,
                    label = stringResource(R.string.reader_swipe_both),
                    selected = config.swipeDirection == "both",
                    onClick = { onSwipeDirectionChange("both") },
                    modifier = Modifier.weight(1f)
                )
                SwipeOption(
                    icon = Icons.Default.Close,
                    label = stringResource(R.string.reader_swipe_none),
                    selected = config.swipeDirection == "none",
                    onClick = { onSwipeDirectionChange("none") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.wallpaper_reader),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            WallpaperChoiceRow(
                selectedRef = config.wallpaper,
                hasImage = WallpaperStorage.fileNameOf(config.wallpaper) != null,
                onPickImage = onPickWallpaper,
                onSelectBuiltin = onWallpaperChange,
                onRemove = { onWallpaperChange(PreferenceAllowlists.WALLPAPER_NONE) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            SavedThemesSection(
                themes = savedThemes,
                dark = config.themeDark,
                onSave = onSaveTheme,
                onApply = onApplyTheme,
                onDelete = onDeleteTheme,
                onReset = onResetAppearance
            )

            Spacer(modifier = Modifier.height(12.dp))
            BlurSlider(
                initial = config.wallpaperBlur,
                onCommit = onWallpaperBlurChange,
                onPreview = onWallpaperBlurPreview
            )
            VeilSlider(
                initial = config.veil,
                onCommit = onVeilChange,
                onPreview = onVeilPreview
            )
            Spacer(modifier = Modifier.height(8.dp))
            WallpaperBehindBarsRow(
                checked = wallpaperBehindBars,
                onCheckedChange = onWallpaperBehindBarsChange
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.reader_auto_scroll),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (config.autoScrollSpeed > 0f) {
                    Text(
                        text = "%.1f".format(config.autoScrollSpeed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            var autoScrollSpeed by remember(config.autoScrollSpeed) {
                mutableFloatStateOf(config.autoScrollSpeed)
            }
            Slider(
                value = autoScrollSpeed,
                onValueChange = { autoScrollSpeed = it },
                onValueChangeFinished = { onAutoScrollSpeedChange(autoScrollSpeed) },
                valueRange = 0f..3f,
                steps = 11,
                modifier = Modifier.fillMaxWidth()
            )
            if (config.autoScrollSpeed > 0f) {
                Text(
                    stringResource(R.string.reader_auto_scroll_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SwipeOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
