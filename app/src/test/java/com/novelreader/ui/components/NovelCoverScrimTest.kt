package com.novelreader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.MinContrast
import com.novelreader.ui.theme.contrastRatio
import org.junit.Test

class NovelCoverScrimTest {

    @Test
    fun `white overlay text clears the minimum contrast over the lightest possible cover`() {
        val scrim = Color.Black.copy(alpha = OverlayScrimAlpha)
        val overWhiteCover = scrim.compositeOver(Color.White)

        assertThat(contrastRatio(Color.White, overWhiteCover)).isAtLeast(MinContrast)
    }
}
