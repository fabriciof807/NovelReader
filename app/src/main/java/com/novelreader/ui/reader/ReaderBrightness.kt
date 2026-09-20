package com.novelreader.ui.reader

import com.novelreader.data.local.preferences.PreferenceAllowlists

private const val SYSTEM_BRIGHTNESS_MAX = 255

/**
 * The window needs a 0..1 fraction of the display maximum, or no override at all when the reader
 * is following the device. `null` is that "no override", which the screen maps onto
 * `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE`.
 */
fun windowBrightnessFor(value: Int): Float? {
    if (value == PreferenceAllowlists.BRIGHTNESS_SYSTEM) return null
    val clamped = value.coerceIn(PreferenceAllowlists.BRIGHTNESS_MIN, PreferenceAllowlists.MAX_BRIGHTNESS)
    return clamped / 100f
}

/** `Settings.System.SCREEN_BRIGHTNESS` is a 0..255 level; the slider speaks percent. */
fun systemBrightnessPercent(raw: Int): Int =
    (raw * PreferenceAllowlists.MAX_BRIGHTNESS / SYSTEM_BRIGHTNESS_MAX)
        .coerceIn(PreferenceAllowlists.BRIGHTNESS_MIN, PreferenceAllowlists.MAX_BRIGHTNESS)
