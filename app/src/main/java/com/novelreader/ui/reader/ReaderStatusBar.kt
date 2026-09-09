package com.novelreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novelreader.R

internal fun readPercent(ratio: Float): Int = (ratio.coerceIn(0f, 1f) * 100).toInt()

@Composable
fun ReaderStatusBar(
    battery: BatteryState,
    readRatio: Float,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val batteryDescription = if (battery.charging) {
        stringResource(R.string.reader_battery_charging_label, battery.percent)
    } else {
        stringResource(R.string.reader_battery_label, battery.percent)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (battery.charging) {
                Icons.Outlined.BatteryChargingFull
            } else {
                Icons.Outlined.BatteryFull
            },
            contentDescription = batteryDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${battery.percent}%",
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${readPercent(readRatio)}%",
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
