package com.novelreader.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TitleExtractorTest {

    @Test
    fun `null novel title returns title unchanged`() {
        assertThat(TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", null)).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `blank novel title returns title unchanged`() {
        assertThat(TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", "")).isEqualTo("Chapter 5 - Title")
        assertThat(TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", "   ")).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `strips novel name prefix with ASCII hyphen separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name - Chapter 5 - Title", "Novel Name")
        ).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `strips novel name prefix with en-dash separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name – Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `strips novel name prefix with em-dash separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name — Chapter 5 - Title", "Novel Name")
        ).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `strips novel name prefix with pipe separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name | Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `strips novel name prefix with colon separator`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Novel Name: Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `match is case-insensitive`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("novel name - Chapter 5", "Novel Name")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `no prefix match returns title unchanged`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("Chapter 5 - Title", "Some Other Novel")
        ).isEqualTo("Chapter 5 - Title")
    }

    @Test
    fun `false positive guard - prefix match without separator is not stripped`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("The Beginning of the End - Chapter 5", "The Beginning")
        ).isEqualTo("The Beginning of the End - Chapter 5")
    }

    @Test
    fun `prefix with separator is stripped even when novel title is short`() {
        assertThat(
            TitleExtractor.cleanChapterTitleForDisplay("A - Chapter 5", "A")
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `extractChapterTitleFromTag strips novel prefix and site suffix for canonical pattern`() {
        assertThat(
            TitleExtractor.extractChapterTitleFromTag(
                "My Novel - Chapter 1 | FreeWebNovel",
                "My Novel"
            )
        ).isEqualTo("Chapter 1")
    }

    @Test
    fun `extractChapterTitleFromTag strips novel prefix and trailing pipe for pipe pattern`() {
        assertThat(
            TitleExtractor.extractChapterTitleFromTag(
                "Cultivation Novel | Chapter 5 | The Trial",
                "Cultivation Novel"
            )
        ).isEqualTo("Chapter 5")
    }

    @Test
    fun `extractChapterTitleFromTag keeps em-dash content when no pipe present`() {
        assertThat(
            TitleExtractor.extractChapterTitleFromTag(
                "Martial Peak — Chapter 5 — The Trial",
                "Martial Peak"
            )
        ).isEqualTo("Chapter 5 — The Trial")
    }

    @Test
    fun `extractChapterTitleFromTag returns null for blank title tag`() {
        assertThat(TitleExtractor.extractChapterTitleFromTag("", "My Novel")).isNull()
        assertThat(TitleExtractor.extractChapterTitleFromTag("   ", "My Novel")).isNull()
    }

    @Test
    fun `extractChapterTitleFromTag with null novel title splits on first pipe`() {
        assertThat(
            TitleExtractor.extractChapterTitleFromTag(
                "My Novel - Chapter 1 | FreeWebNovel",
                null
            )
        ).isEqualTo("My Novel - Chapter 1")
    }

    @Test
    fun `extractChapterTitleFromTag falls back to full title when prefix has no separator`() {
        assertThat(
            TitleExtractor.extractChapterTitleFromTag(
                "The Beginning of the End - Chapter 5",
                "The Beginning"
            )
        ).isEqualTo("The Beginning of the End - Chapter 5")
    }

    @Test
    fun `extractChapterTitleFromTag returns remainder when no pipe present`() {
        assertThat(
            TitleExtractor.extractChapterTitleFromTag(
                "Martial Peak - Chapter 1",
                "Martial Peak"
            )
        ).isEqualTo("Chapter 1")
    }
}
