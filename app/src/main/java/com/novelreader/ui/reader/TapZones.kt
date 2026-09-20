package com.novelreader.ui.reader

enum class TapZone { LEFT, CENTER, RIGHT }

enum class TapAction { TOGGLE_CONTROLS, PREV_CHAPTER, NEXT_CHAPTER }

/**
 * Which third of the viewport a tap landed in. A width the WebView has not measured and a coordinate
 * outside it both fall back to the centre: a tap we cannot place must not turn a page on its own.
 */
fun tapZoneFor(x: Int, width: Int): TapZone {
    if (width <= 0 || x < 0 || x >= width) return TapZone.CENTER
    return when {
        x < width / 3 -> TapZone.LEFT
        x < width * 2 / 3 -> TapZone.CENTER
        else -> TapZone.RIGHT
    }
}

fun tapActionFor(zone: TapZone, enabled: Boolean): TapAction = when {
    !enabled -> TapAction.TOGGLE_CONTROLS
    zone == TapZone.LEFT -> TapAction.PREV_CHAPTER
    zone == TapZone.RIGHT -> TapAction.NEXT_CHAPTER
    else -> TapAction.TOGGLE_CONTROLS
}
