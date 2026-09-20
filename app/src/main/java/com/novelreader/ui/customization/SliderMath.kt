package com.novelreader.ui.customization

import kotlin.math.roundToInt

/**
 * Fraction of the track a pointer at [x] px sits at on a slider [totalWidth] px wide whose thumb is
 * [thumbWidth] px wide.
 *
 * The thumb travels from `thumbWidth / 2` to `totalWidth - thumbWidth / 2`; that travel, not the
 * whole width, is what the value maps onto, which is why a press on either edge lands exactly on an
 * end of the range. [rtl] mirrors the mapping, since the range grows to the left there.
 */
internal fun sliderFractionAt(
    x: Float,
    totalWidth: Float,
    thumbWidth: Float,
    rtl: Boolean = false
): Float {
    val travel = totalWidth - thumbWidth
    if (travel <= 0f) return 0f
    val fraction = ((x - thumbWidth / 2f) / travel).coerceIn(0f, 1f)
    return if (rtl) 1f - fraction else fraction
}

/**
 * The value [fraction] of the way along [range], snapped to the nearest step when [steps] is
 * positive.
 *
 * [steps] counts the admissible values *between* the ends, as the Material slider does: 0 is
 * continuous, and 4 over 0..10 admits 2, 4, 6 and 8.
 */
internal fun sliderValueAt(
    fraction: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val span = range.endInclusive - range.start
    val coerced = fraction.coerceIn(0f, 1f)
    if (steps <= 0) return range.start + coerced * span
    val ticks = steps + 1
    return range.start + (coerced * ticks).roundToInt() / ticks.toFloat() * span
}

/** [value] moved onto the nearest admissible step of [range]. */
internal fun snapSliderValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val coerced = value.coerceIn(range.start, range.endInclusive)
    val span = range.endInclusive - range.start
    if (steps <= 0 || span == 0f) return coerced
    return sliderValueAt((coerced - range.start) / span, range, steps)
}

/** How far one key press moves the value: a step when there are steps, 1% of the range otherwise. */
internal fun sliderStepSize(range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val span = range.endInclusive - range.start
    return if (steps > 0) span / (steps + 1) else span / 100f
}
