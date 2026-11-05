package com.novelreader.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
}
