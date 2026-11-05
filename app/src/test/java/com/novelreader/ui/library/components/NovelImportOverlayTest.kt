package com.novelreader.ui.library.components

import com.novelreader.domain.usecase.BackgroundImportState
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelImportOverlayTest {

    private val running = BackgroundImportState(
        running = true,
        novelTitle = "Alpha",
        importedCount = 3,
        totalToImport = 10,
        queuedNovelTitles = listOf("Beta")
    )

    private fun overlay(title: String, state: BackgroundImportState = running) =
        novelImportOverlay(title, state, queuedTitleKeys(state.queuedNovelTitles))

    @Test
    fun `the novel being imported carries its progress`() {
        assertEquals(NovelImportOverlay.Importing(3, 10), overlay("Alpha"))
    }

    @Test
    fun `a queued novel is matched ignoring case`() {
        assertEquals(NovelImportOverlay.Queued, overlay("BETA"))
    }

    @Test
    fun `other novels get the same value while progress changes`() {
        val before = overlay("Gamma")
        val after = overlay("Gamma", running.copy(importedCount = 9))
        assertEquals(NovelImportOverlay.None, before)
        assertEquals(before, after)
    }

    @Test
    fun `nothing is running means no overlay for the title`() {
        assertEquals(NovelImportOverlay.None, overlay("Alpha", running.copy(running = false)))
    }

    @Test
    fun `a novel that is both running and queued shows the progress`() {
        val state = running.copy(queuedNovelTitles = listOf("alpha"))
        assertEquals(NovelImportOverlay.Importing(3, 10), overlay("Alpha", state))
    }
}
