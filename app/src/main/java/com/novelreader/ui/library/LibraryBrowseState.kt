package com.novelreader.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * What the reader is browsing with: the open search box, its query and the filter chip.
 *
 * Kept together as one saveable value so leaving the library — opening a novel and coming back — does
 * not throw the search away. Plain `remember` used to lose it, because the library is disposed while
 * the reader is on screen (the scroll position survives because it is `rememberSaveable`).
 */
@Stable
class LibraryBrowseState(
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    filterChip: NovelFilter = NovelFilter.ALL
) {
    var isSearchActive by mutableStateOf(isSearchActive)
    var searchQuery by mutableStateOf(searchQuery)
    var filterChip by mutableStateOf(filterChip)

    companion object {
        val Saver: Saver<LibraryBrowseState, List<Any>> = Saver(
            save = { listOf(it.isSearchActive, it.searchQuery, it.filterChip.name) },
            restore = {
                LibraryBrowseState(
                    isSearchActive = it[0] as Boolean,
                    searchQuery = it[1] as String,
                    filterChip = NovelFilter.valueOf(it[2] as String)
                )
            }
        )
    }
}
