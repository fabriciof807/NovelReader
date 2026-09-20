package com.novelreader.ui.customization

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A slider that lets a vertical drag through to whatever scrolls behind it.
 *
 * Material's own slider watches two gestures — a press, which jumps the value to wherever the finger
 * landed, and a horizontal drag — and its horizontal detector claims any drag whose *sideways*
 * travel crosses the touch slop, however small that is. Inside a scrolling column the slider is the
 * inner node, so its detector runs first: one diagonal finger drag and the slider owns the gesture
 * to the end, the column never scrolls, and the setting changes under the reader's finger (issue
 * #21). A dash of sideways jitter is enough, which is why a perfectly straight injected drag does
 * not reproduce it.
 *
 * This owns the gesture instead and settles the axis once, at the first slop crossing. A sideways
 * drag changes the value; a drag that is mostly vertical is left *unconsumed*, so the scroll behind
 * it — with its fling, its overscroll and the sheet's drag-to-dismiss — keeps working. A press that
 * never crossed the slop is still a tap and jumps to that point, as Material does.
 *
 * The visuals are Material's own [SliderDefaults.Track] and [SliderDefaults.Thumb], so a value looks
 * the same here as on the sliders that keep the stock component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    contentDescription: String? = null,
    colors: SliderColors = SliderDefaults.colors()
) {
    val state = remember(steps, valueRange) {
        SliderState(value = value, steps = steps, valueRange = valueRange)
    }
    state.value = value

    val interactionSource = remember { MutableInteractionSource() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Measured, not derived from the density: the thumb's width is what the range really spans.
    var totalWidth by remember { mutableFloatStateOf(0f) }
    var thumbWidth by remember { mutableFloatStateOf(0f) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    // The pointer input scope is a restricted coroutine scope: interactions have to be emitted from
    // one of our own, not from inside the gesture.
    val scope = rememberCoroutineScope()

    Layout(
        content = {
            Box(
                modifier = Modifier
                    .layoutId(ThumbSlot)
                    .wrapContentWidth()
                    .onSizeChanged { thumbWidth = it.width.toFloat() }
            ) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = enabled
                )
            }
            Box(modifier = Modifier.layoutId(TrackSlot)) {
                SliderDefaults.Track(sliderState = state, colors = colors, enabled = enabled)
            }
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics {
                if (!enabled) disabled()
                contentDescription?.let { this.contentDescription = it }
                setProgress { target ->
                    val resolved = snapSliderValue(target, valueRange, steps)
                    if (resolved == value) {
                        false
                    } else {
                        onValueChange(resolved)
                        onValueChangeFinished()
                        true
                    }
                }
            }
            .progressSemantics(state.value, valueRange.start..valueRange.endInclusive, steps)
            .focusable(enabled)
            .onKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1f
                    Key.DirectionRight -> 1f
                    else -> 0f
                }
                if (!enabled || direction == 0f || event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                val next = snapSliderValue(
                    value + (if (rtl) -direction else direction) *
                        sliderStepSize(valueRange, steps),
                    valueRange,
                    steps
                )
                if (next != value) {
                    onValueChange(next)
                    onValueChangeFinished()
                }
                true
            }
            .pointerInput(enabled, valueRange, steps) {
                if (!enabled) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop

                fun valueAt(x: Float): Float {
                    val fraction = sliderFractionAt(x, totalWidth, thumbWidth, rtl)
                    return sliderValueAt(fraction, valueRange, steps)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    scope.launch { interactionSource.emit(press) }

                    var locked: Orientation? = null
                    var travelled = Offset.Zero
                    var changed = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            if (locked == null) {
                                currentOnValueChange(valueAt(down.position.x))
                                changed = true
                            }
                            change.consume()
                            break
                        }
                        if (change.isConsumed) break
                        travelled += change.positionChange()
                        if (locked == null) {
                            val sideways = abs(travelled.x)
                            val upright = abs(travelled.y)
                            if (sideways < touchSlop && upright < touchSlop) continue
                            // A tie goes to the scroll: moving a setting the reader never meant to
                            // touch is worse than not scrolling.
                            locked =
                                if (sideways > upright) Orientation.Horizontal else Orientation.Vertical
                            if (locked == Orientation.Vertical) break
                        }
                        change.consume()
                        currentOnValueChange(valueAt(change.position.x))
                        changed = true
                    }

                    scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                    if (changed) currentOnFinished()
                }
            }
    ) { measurables, constraints ->
        val thumbPlaceable = measurables.first { it.layoutId == ThumbSlot }.measure(constraints)
        val trackPlaceable = measurables.first { it.layoutId == TrackSlot }
            .measure(constraints.offset(horizontal = -thumbPlaceable.width).copy(minHeight = 0))
        val sliderWidth = thumbPlaceable.width + trackPlaceable.width
        val sliderHeight = max(trackPlaceable.height, thumbPlaceable.height)
        totalWidth = sliderWidth.toFloat()

        layout(sliderWidth, sliderHeight) {
            trackPlaceable.placeRelative(thumbPlaceable.width / 2, (sliderHeight - trackPlaceable.height) / 2)
            val span = valueRange.endInclusive - valueRange.start
            val fraction = if (span <= 0f) {
                0f
            } else {
                ((state.value - valueRange.start) / span).coerceIn(0f, 1f)
            }
            val thumbX = (trackPlaceable.width * fraction).roundToInt()
            thumbPlaceable.placeRelative(thumbX, (sliderHeight - thumbPlaceable.height) / 2)
        }
    }
}

private val ThumbSlot = Any()
private val TrackSlot = Any()
