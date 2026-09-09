package com.novelreader.ui.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class BatteryState(val percent: Int, val charging: Boolean)

private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

internal fun batteryStateFromIntent(intent: Intent?): BatteryState? {
    if (intent == null) return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    return BatteryState(
        percent = (level * 100 / scale).coerceIn(0, 100),
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
    )
}

@Composable
fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember(context) {
        mutableStateOf(
            batteryStateFromIntent(
                ContextCompat.registerReceiver(
                    context,
                    null,
                    batteryFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            ) ?: BatteryState(percent = 0, charging = false)
        )
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                batteryStateFromIntent(intent)?.let { state = it }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            batteryFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
