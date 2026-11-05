package com.novelreader.ui.library.components

import com.novelreader.domain.usecase.BackgroundImportState

sealed interface NovelImportOverlay {
    data object None : NovelImportOverlay
    data object Queued : NovelImportOverlay
    data class Importing(val imported: Int, val total: Int) : NovelImportOverlay
}

internal fun queuedTitleKeys(titles: List<String>): Set<String> =
    titles.mapTo(HashSet(titles.size)) { it.lowercase() }

internal fun novelImportOverlay(
    title: String,
    state: BackgroundImportState,
    queuedKeys: Set<String>
): NovelImportOverlay = when {
    state.running && state.novelTitle == title ->
        NovelImportOverlay.Importing(state.importedCount, state.totalToImport)
    title.lowercase() in queuedKeys -> NovelImportOverlay.Queued
    else -> NovelImportOverlay.None
}
