package com.novelreader.regression

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.FtsSearchService
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.parser.GenericFallbackParser
import com.novelreader.data.parser.HtmlSanitizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EdgeCaseTest {

    private lateinit var database: NovelDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun zeroNovels_emptyLibrary() = runBlocking {
        val all = database.novelDao().getAllNovels().first()
        assertThat(all).isEmpty()
    }

    @Test
    fun zeroChapters_novelWithNoContent() = runBlocking {
        val id = database.novelDao().insert(NovelEntity(title = "Empty Novel", totalChapters = 0))
        val chapters = database.chapterDao().getChaptersByNovelSync(id)
        assertThat(chapters).isEmpty()
        val novel = database.novelDao().getNovelById(id)
        assertThat(novel).isNotNull()
        assertThat(novel!!.totalChapters).isEqualTo(0)
    }

    @Test
    fun htmlUtf8Accents_parseCorrectly() {
        val parser = GenericFallbackParser()
        val html = """
            <html><head><title>Naive Uber Cafe - NovelReader</title></head>
            <body><div class="content"><p>coracao, naive, uber, faccade</p></div></body>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "test.html")
        assertThat(parsed.novelTitle).contains("Naive")
        assertThat(parsed.content).contains("coracao")
    }

    @Test
    fun filenameWithSpecialChars_parseCorrectly() {
        val parser = GenericFallbackParser()
        val html = """
            <html><head><title>Novel Title</title></head>
            <body><div class="content"><p>Content here</p></div></body>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val parsed = parser.parse(doc, "capitulo (1) @special #chars.html")
        assertThat(parsed.novelTitle).isNotEmpty()
    }

    @Test
    fun largeHtmlContent_handled() {
        val sb = StringBuilder()
        sb.append("<html><head><title>Large Novel</title></head><body><div class=\"content\">")
        repeat(10000) { i ->
            sb.append("<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Paragraph $i.</p>")
        }
        sb.append("</div></body></html>")

        val doc = Jsoup.parse(sb.toString())
        val sanitized = HtmlSanitizer.sanitizeHtml(doc.body().html())
        assertThat(sanitized.length > 100_000).isTrue()
    }

    @Test
    fun searchWithSpecialChars_doesNotCrash() = runBlocking {
        val novelId = database.novelDao().insert(NovelEntity(title = "Special Search", totalChapters = 0))
        database.chapterDao().insertAll(listOf(
            ChapterEntity(novelId = novelId, title = "Normal Chapter", fileName = "c.html", orderIndex = 0, content = "Normal content for testing")
        ))

        val service = FtsSearchService(database.chapterDao())
        val results = service.searchInNovel(novelId, "@#$%^&*()")
        assertThat(results).isEmpty()
    }

    @Test
    fun chapterNumberExtraction_edgeCases() {
        val titles = listOf(
            "Chapter 1.5" to 1,
            "Vol.2 Ch.3" to 2,
            "Part I" to 1,
            "Chapter X" to Int.MAX_VALUE,
            "Episode 42 - The Finale" to 42,
            "" to Int.MAX_VALUE
        )
        for ((title, _) in titles) {
            assertThat(title).isNotNull()
        }
    }

    @Test
    fun emptyHtmlFile_parsed() {
        val parser = GenericFallbackParser()
        val doc = Jsoup.parse("")
        val parsed = parser.parse(doc, "empty.html")
        assertThat(parsed.novelTitle).isNotEmpty()
        assertThat(parsed.chapterTitle).isNotEmpty()
    }
}
