package com.novelreader.ui.customization

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelreader.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

const val ACCENT_WHEEL_TAG = "accent_wheel"

/** Step the accessibility actions move the selection by. */
const val WHEEL_HUE_STEP = 10f
const val WHEEL_SATURATION_STEP = 10

data class WheelSelection(val hue: Float, val saturation: Int)

/**
 * Angle picks the hue (0° to the right, growing clockwise because y grows downwards) and the distance
 * from the centre picks the saturation. A press on the exact centre keeps [currentHue]: the angle is
 * undefined there and the handle should not jump.
 */
fun wheelSelectionAt(
    x: Float,
    y: Float,
    radius: Float,
    currentHue: Float = 0f
): WheelSelection {
    if (radius <= 0f) return WheelSelection(normalizeHue(currentHue), 0)
    val distance = kotlin.math.sqrt(x * x + y * y)
    val saturation = ((distance / radius) * 100f).roundToInt().coerceIn(0, 100)
    if (distance < 1f) return WheelSelection(normalizeHue(currentHue), saturation)
    val degrees = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
    return WheelSelection(normalizeHue(degrees), saturation)
}

/** Where the handle sits for a selection, in pixels relative to the centre of the wheel. */
fun wheelPointFor(hue: Float, saturationPercent: Int, radius: Float): Offset {
    val radians = Math.toRadians(normalizeHue(hue).toDouble())
    val saturation = saturationPercent.coerceIn(0, 100) / 100f
    return Offset(
        x = (cos(radians) * radius * saturation).toFloat(),
        y = (sin(radians) * radius * saturation).toFloat()
    )
}

fun normalizeHue(hue: Float): Float = ((hue % 360f) + 360f) % 360f

@Composable
fun HueWheel(
    hue: Float,
    saturation: Int,
    background: Color,
    onPreview: (hue: Float, saturation: Int) -> Unit,
    onCommit: (hue: Float, saturation: Int) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp
) {
    val density = LocalDensity.current
    val outline = with(density) { 8.dp.toPx() }
    val handle = with(density) { 12.dp.toPx() }
    val handleRing = with(density) { 3.dp.toPx() }
    // The gesture handlers outlive recompositions, so they must read the current selection: built from
    // the parameters, `onDragEnd` committed the selection the gesture started with.
    val latestHue by rememberUpdatedState(hue)
    val latestSaturation by rememberUpdatedState(saturation)

    fun updateAt(point: Offset, sizePx: Size, commit: Boolean) {
        val radius = (sizePx.minDimension - outline) / 2f
        val selection = wheelSelectionAt(
            x = point.x - sizePx.width / 2f,
            y = point.y - sizePx.height / 2f,
            radius = radius,
            currentHue = latestHue
        )
        if (commit) onCommit(selection.hue, selection.saturation)
        else onPreview(selection.hue, selection.saturation)
    }

    val description = stringResource(
        R.string.accent_wheel_description,
        hue.roundToInt(),
        saturation
    )
    val moreHue = stringResource(R.string.accent_wheel_more_hue)
    val lessHue = stringResource(R.string.accent_wheel_less_hue)
    val moreSaturation = stringResource(R.string.accent_wheel_more_saturation)
    val lessSaturation = stringResource(R.string.accent_wheel_less_saturation)

    Canvas(
        modifier = modifier
            .size(diameter)
            .testTag(ACCENT_WHEEL_TAG)
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(moreHue) {
                        onCommit(normalizeHue(hue + WHEEL_HUE_STEP), saturation)
                        true
                    },
                    CustomAccessibilityAction(lessHue) {
                        onCommit(normalizeHue(hue - WHEEL_HUE_STEP), saturation)
                        true
                    },
                    CustomAccessibilityAction(moreSaturation) {
                        onCommit(hue, (saturation + WHEEL_SATURATION_STEP).coerceAtMost(100))
                        true
                    },
                    CustomAccessibilityAction(lessSaturation) {
                        onCommit(hue, (saturation - WHEEL_SATURATION_STEP).coerceAtLeast(0))
                        true
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { point -> updateAt(point, size.toSize(), commit = true) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onCommit(latestHue, latestSaturation) },
                    onDrag = { change, _ -> updateAt(change.position, size.toSize(), commit = false) }
                )
            }
    ) {
        val radius = (size.minDimension - outline) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = List(37) { index -> Color.hsl(index * 10f % 360f, 1f, 0.5f) },
                center = center
            ),
            radius = radius,
            center = center
        )
        // Saturation is the distance from the middle, so the middle fades to the palette background.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(background, background.copy(alpha = 0f)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        val point = center + wheelPointFor(hue, saturation, radius)
        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = handle + handleRing,
            center = point
        )
        drawCircle(color = Color.White, radius = handle, center = point)
        drawCircle(
            color = Color.hsl(normalizeHue(hue), saturation / 100f, 0.5f),
            radius = handle - handleRing,
            center = point
        )
    }
}
