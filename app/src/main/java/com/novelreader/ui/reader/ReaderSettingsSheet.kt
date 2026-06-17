package com.novelreader.ui.reader

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.R
import com.novelreader.data.local.preferences.ReaderConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    config: ReaderConfig,
    onThemeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeOption(
                    icon = Icons.Default.LightMode,
                    label = stringResource(R.string.light),
                    selected = config.theme == "light",
                    onClick = { onThemeChange("light") },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = Icons.Default.DarkMode,
                    label = stringResource(R.string.dark),
                    selected = config.theme == "dark",
                    onClick = { onThemeChange("dark") },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = Icons.Default.Palette,
                    label = stringResource(R.string.sepia),
                    selected = config.theme == "sepia",
                    onClick = { onThemeChange("sepia") },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = Icons.Default.DarkMode,
                    label = stringResource(R.string.reader_theme_gray),
                    selected = config.theme == "gray",
                    onClick = { onThemeChange("gray") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.font_size, config.fontSize),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = config.fontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 14f..40f,
                steps = 12,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.line_spacing, config.lineHeight),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = config.lineHeight,
                onValueChange = { onLineHeightChange(it) },
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
            Slider(
                value = config.autoScrollSpeed,
                onValueChange = { onAutoScrollSpeedChange(it) },
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
            .clickable(onClick = onClick)
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
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
