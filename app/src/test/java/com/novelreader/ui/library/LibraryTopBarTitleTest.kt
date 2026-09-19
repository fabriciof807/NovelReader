package com.novelreader.ui.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tab 1 of the library is the chapters of the selected novel, but the collections when there is no
 * novel: the top bar answered "Capítulos" over the collections until it stopped guessing from the tab
 * index alone.
 */
class LibraryTopBarTitleTest {

    private val library = "Biblioteca"
    private val collections = "Coleções"

    private fun title(selectedTab: Int, novelTitle: String? = null) =
        libraryTopBarTitle(
            selectedTab = selectedTab,
            novelTitle = novelTitle,
            library = library,
            collections = collections
        )

    @Test
    fun `tab zero is the library`() {
        assertThat(title(selectedTab = 0, novelTitle = "Child of Destiny")).isEqualTo(library)
    }

    @Test
    fun `a selected novel names the tab, whatever it is`() {
        assertThat(title(selectedTab = 1, novelTitle = "Child of Destiny")).isEqualTo("Child of Destiny")
        assertThat(title(selectedTab = 2, novelTitle = "Child of Destiny")).isEqualTo("Child of Destiny")
    }

    @Test
    fun `without a novel, tab one is the collections, not the chapters`() {
        assertThat(title(selectedTab = 1, novelTitle = null)).isEqualTo(collections)
    }

    @Test
    fun `without a novel, tab two stays the collections rather than the novel title`() {
        assertThat(title(selectedTab = 2, novelTitle = null)).isEqualTo(collections)
    }
}
