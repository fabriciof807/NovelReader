package com.novelreader.util

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun Modifier.hapticClickable(
    haptic: HapticFeedback?,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.clickable(enabled = enabled) {
    haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    onClick()
}
