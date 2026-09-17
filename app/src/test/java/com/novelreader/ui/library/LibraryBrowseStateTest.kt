package com.novelreader.ui.library

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryBrowseStateTest {

    private fun save(state: LibraryBrowseState): List<Any>? =
        with(LibraryBrowseState.Saver) { SaverScope { true }.save(state) }

    @Test
    fun `the search, its query and the filter survive being saved and restored`() {
        val state = LibraryBrowseState(
            isSearchActive = true,
            searchQuery = "martial",
            filterChip = NovelFilter.READING
        )

        val restored = LibraryBrowseState.Saver.restore(requireNotNull(save(state)))!!

        assertThat(restored.isSearchActive).isTrue()
        assertThat(restored.searchQuery).isEqualTo("martial")
        assertThat(restored.filterChip).isEqualTo(NovelFilter.READING)
    }

    @Test
    fun `a fresh browse keeps the defaults`() {
        val restored = LibraryBrowseState.Saver.restore(
            requireNotNull(save(LibraryBrowseState()))
        )!!

        assertThat(restored.isSearchActive).isFalse()
        assertThat(restored.searchQuery).isEmpty()
        assertThat(restored.filterChip).isEqualTo(NovelFilter.ALL)
    }

    @Test
    fun `the saved value is plain enough for a bundle`() {
        val saved = requireNotNull(save(LibraryBrowseState(isSearchActive = true, searchQuery = "x")))

        assertThat(saved.filter { it !is String && it !is Boolean }).isEmpty()
    }
}
