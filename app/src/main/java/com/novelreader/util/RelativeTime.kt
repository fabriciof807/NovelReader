package com.novelreader.util

import android.text.format.DateUtils

fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (timestampMs <= 0L) return null
    if (nowMs < timestampMs) return null
    return DateUtils.getRelativeTimeSpanString(
        timestampMs,
        nowMs,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
