package com.novelreader.ui.reader

import android.content.Intent
import android.os.BatteryManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BatteryStateTest {

    private fun batteryIntent(
        level: Int? = 42,
        scale: Int? = 100,
        status: Int? = BatteryManager.BATTERY_STATUS_DISCHARGING
    ): Intent {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
        level?.let { intent.putExtra(BatteryManager.EXTRA_LEVEL, it) }
        scale?.let { intent.putExtra(BatteryManager.EXTRA_SCALE, it) }
        status?.let { intent.putExtra(BatteryManager.EXTRA_STATUS, it) }
        return intent
    }

    @Test
    fun `reads the battery percentage from level and scale`() {
        val state = batteryStateFromIntent(batteryIntent(level = 42, scale = 100))

        assertThat(state).isEqualTo(BatteryState(percent = 42, charging = false))
    }

    @Test
    fun `marks charging while the battery is charging`() {
        val state = batteryStateFromIntent(
            batteryIntent(status = BatteryManager.BATTERY_STATUS_CHARGING)
        )

        assertThat(state?.charging).isTrue()
    }

    @Test
    fun `marks charging when the battery is full`() {
        val state = batteryStateFromIntent(
            batteryIntent(level = 100, status = BatteryManager.BATTERY_STATUS_FULL)
        )

        assertThat(state).isEqualTo(BatteryState(percent = 100, charging = true))
    }

    @Test
    fun `clamps a level above the scale to one hundred`() {
        val state = batteryStateFromIntent(batteryIntent(level = 120, scale = 100))

        assertThat(state?.percent).isEqualTo(100)
    }

    @Test
    fun `returns null when level or scale is missing`() {
        assertThat(batteryStateFromIntent(batteryIntent(level = null))).isNull()
        assertThat(batteryStateFromIntent(batteryIntent(scale = null))).isNull()
        assertThat(batteryStateFromIntent(null)).isNull()
    }
}
