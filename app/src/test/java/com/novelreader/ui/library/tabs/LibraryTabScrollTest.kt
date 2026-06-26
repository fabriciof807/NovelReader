package com.novelreader.ui.library.tabs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.theme.NovelReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp")
class LibraryTabScrollTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `rememberSaveable LazyListState persists scroll index across state restoration`() {
        val restorationTester = StateRestorationTester(composeTestRule)
        var state: LazyListState? = null
        restorationTester.setContent {
            NovelReaderTheme {
                val s = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                state = s
                SimpleLazyColumn(state = s, count = 50)
            }
        }
        composeTestRule.runOnUiThread { runBlocking { state!!.scrollToItem(15) } }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { assertThat(state!!.firstVisibleItemIndex).isEqualTo(15) }

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.runOnUiThread { assertThat(state!!.firstVisibleItemIndex).isEqualTo(15) }
    }
}

@Composable
private fun SimpleLazyColumn(state: LazyListState, count: Int) {
    androidx.compose.foundation.lazy.LazyColumn(state = state) {
        items(count) { i ->
            androidx.compose.material3.Text("Item $i")
        }
    }
}
