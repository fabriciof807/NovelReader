package com.novelreader.util

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60L * MINUTE_MS
private const val DAY_MS = 24L * HOUR_MS
private const val MONTH_MS = 30L * DAY_MS

fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (timestampMs <= 0L) return null
    val delta = nowMs - timestampMs
    if (delta < 0L) return null
    return when {
        delta < MINUTE_MS -> "agora"
        delta < HOUR_MS -> "há ${delta / MINUTE_MS} min"
        delta < DAY_MS -> "há ${delta / HOUR_MS} h"
        delta < MONTH_MS -> "há ${delta / DAY_MS} dias"
        else -> null
    }
}
