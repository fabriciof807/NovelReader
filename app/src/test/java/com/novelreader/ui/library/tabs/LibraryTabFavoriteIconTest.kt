package com.novelreader.ui.library.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryTabFavoriteIconTest {

    @Test
    fun `favorite icon is filled for favorites and outlined otherwise`() {
        assertThat(favoriteIcon(true)).isEqualTo(Icons.Filled.Star)
        assertThat(favoriteIcon(false)).isEqualTo(Icons.Outlined.Star)
    }
}
