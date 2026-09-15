package com.novelreader.ui.customization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h800dp")
class SliderCommitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    private fun setBlurSlider(
        initial: Int,
        onPreview: (Int) -> Unit = {},
        onCommit: (Int) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Column(Modifier.fillMaxWidth()) {
                    BlurSlider(initial = initial, onCommit = onCommit, onPreview = onPreview)
                }
            }
        }
    }

    private fun setVeilSlider(
        initial: Int,
        onPreview: (Int) -> Unit = {},
        onCommit: (Int) -> Unit = {}
    ) {
        composeTestRule.setContent {
            NovelReaderTheme {
                Column(Modifier.fillMaxWidth()) {
                    VeilSlider(initial = initial, onCommit = onCommit, onPreview = onPreview)
                }
            }
        }
    }

    @Test
    fun `a blur drag previews live and persists exactly once, on release`() {
        val previews = mutableListOf<Int>()
        val commits = mutableListOf<Int>()
        setBlurSlider(initial = 0, onPreview = { previews += it }, onCommit = { commits += it })

        composeTestRule.onAllNodes(slider)[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }

        assertThat(previews).contains(60)
        assertThat(commits).containsExactly(60)
    }

    @Test
    fun `a veil drag previews live and persists exactly once, on release`() {
        val previews = mutableListOf<Int>()
        val commits = mutableListOf<Int>()
        setVeilSlider(initial = 50, onPreview = { previews += it }, onCommit = { commits += it })

        composeTestRule.onAllNodes(slider)[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }

        assertThat(previews).contains(100)
        assertThat(commits).containsExactly(100)
    }

    @Test
    fun `the label follows the drag`() {
        setBlurSlider(initial = 0, onCommit = {})

        composeTestRule.onAllNodes(slider)[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }

        composeTestRule.onNodeWithText("Desfoque: 42").assertExists()
    }

    @Test
    fun `reopening with the committed value keeps the slider in place`() {
        setVeilSlider(initial = 65, onCommit = {})

        composeTestRule.onNodeWithText("Véu de leitura: 65%").assertExists()
    }
}
