package com.novelreader.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class ReaderTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setTopBar(
        isSearchActive: Boolean = false,
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                ReaderTopBar(
                    title = "Chapter 11: 10x Critical Hit",
                    isSearchActive = isSearchActive,
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onBack = onBack,
                    onCloseSearch = {},
                    onActivateSearch = {}
                )
            }
        }
    }

    @Test
    fun `centers the chapter title with the navigation icon`() {
        setTopBar()

        val titleBounds = composeTestRule.onNodeWithText("Chapter 11: 10x Critical Hit")
            .getUnclippedBoundsInRoot()
        val backBounds = composeTestRule.onNodeWithContentDescription("Voltar")
            .getUnclippedBoundsInRoot()
        val titleCenter = (titleBounds.top.value + titleBounds.bottom.value) / 2f
        val backCenter = (backBounds.top.value + backBounds.bottom.value) / 2f

        assertThat(Math.abs(titleCenter - backCenter)).isLessThan(1f)
    }

    @Test
    fun `shows the search field instead of the title while searching`() {
        setTopBar(isSearchActive = true)

        composeTestRule.onNodeWithText("Buscar nos capítulos…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 11: 10x Critical Hit").assertDoesNotExist()
    }

    @Test
    fun `invokes onBack when the navigation icon is tapped`() {
        var backs = 0
        setTopBar(onBack = { backs++ })

        composeTestRule.onNodeWithContentDescription("Voltar").performClick()

        assertThat(backs).isEqualTo(1)
    }
}
