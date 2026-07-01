package com.novelreader.domain.usecase

private val STALE_CONTENT_MARKERS = listOf(
    "Page not found",
    "Not Found",
    "404 page not found",
    "404 Not Found",
    "The address you accessed is incorrect"
)

internal fun looksLikeStaleContent(content: String): Boolean {
    if (content.isBlank()) return true
    if (content.length < 200) return true
    val lower = content.lowercase()
    for (marker in STALE_CONTENT_MARKERS) {
        if (lower.contains(marker.lowercase())) return true
    }
    return false
}
