package com.novelreader.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object AppDimens {
    val NavBarHeight = 46.dp
    val TopBarHeight = 48.dp
    val StatsLineHeight = 20.dp
    val CardRadius = 12.dp
    val GridSpacing = 6.dp
}

object AppShapes {
    val Card = RoundedCornerShape(AppDimens.CardRadius)
    val NavIndicator = RoundedCornerShape(percent = 50)
}

object AppMotion {
    const val FadeMillis = 150
    const val SlideMillis = 220
    const val PressedScale = 0.97f
    private const val MomentumDamping = 0.8f

    fun <T> press(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> settle(hasMomentum: Boolean): SpringSpec<T> = spring(
        dampingRatio = if (hasMomentum) MomentumDamping else Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun reduced(scale: Float): Boolean = scale == 0f

    fun fadeMillis(reduced: Boolean): Int = if (reduced) 0 else FadeMillis

    fun slideMillis(reduced: Boolean): Int = if (reduced) 0 else SlideMillis

    fun pressScaleTarget(pressed: Boolean): Float = if (pressed) PressedScale else 1f
}

@Composable
fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var animatorScale by remember { mutableFloatStateOf(readAnimatorScale(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                animatorScale = readAnimatorScale(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return AppMotion.reduced(animatorScale)
}

private fun readAnimatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
}.getOrDefault(1f)

@Composable
internal fun rememberPressScale(
    interactionSource: InteractionSource,
    reduced: Boolean
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = AppMotion.pressScaleTarget(pressed),
        animationSpec = if (reduced) snap() else AppMotion.press(),
        label = "pressScale"
    )
}

@Composable
fun Modifier.pressedScale(
    interactionSource: InteractionSource,
    reduced: Boolean = rememberReducedMotion()
): Modifier {
    val scale = rememberPressScale(interactionSource, reduced)
    return graphicsLayer {
        val pressedScaleValue = scale.value
        scaleX = pressedScaleValue
        scaleY = pressedScaleValue
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val reduced = rememberReducedMotion()
    return this
        .pressedScale(interactionSource, reduced)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick
        )
}
