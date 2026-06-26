package com.novelreader.ui.library.tabs

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.ui.theme.NovelReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class PersonagensTabFabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `PersonagensTab renders ExtendedFAB labels for add and import`() {
        composeTestRule.setContent {
            NovelReaderTheme {
                PersonagensTab(
                    characters = emptyList(),
                    characterPhotos = emptyMap(),
                    selectedNovel = null,
                    onAddCharacter = { _, _ -> },
                    onDeleteCharacter = { },
                    onAddCharacterPhoto = { _, _ -> },
                    onBatchAddCharacterPhotos = { _, _ -> },
                    onDeleteCharacterPhoto = { _, _ -> },
                    onUpdateCharacterName = { _, _ -> },
                    onUpdateCharacterNotes = { _, _ -> },
                    onToggleCharacterFavorite = { _, _ -> },
                    onImportCharacters = { }
                )
            }
        }
        composeTestRule.onNodeWithText("Adicionar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Importar").assertIsDisplayed()
    }
}
