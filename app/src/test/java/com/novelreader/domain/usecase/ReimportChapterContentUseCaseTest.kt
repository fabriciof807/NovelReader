package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.data.parser.ParsedChapter
import com.novelreader.domain.usecase.importnovel.FileCharsetDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ReimportChapterContentUseCaseTest {
    private val fileCharsetDetector: FileCharsetDetector = mockk()
    private val parserRegistry: ParserRegistry = mockk()
    private val mhtParser: MhtParser = mockk()
    private val chapterDao: ChapterDao = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private lateinit var useCase: ReimportChapterContentUseCase

    @Before
    fun setUp() {
        useCase = ReimportChapterContentUseCase(fileCharsetDetector, parserRegistry, mhtParser, chapterDao, context)
    }

    @Test
    fun `importFile with MHT file parses via parseRaw and calls updateContent`() = runTest {
        val uri: Uri = mockk()
        every { fileCharsetDetector.getFileName(uri, context) } returns "f.mht"
        coEvery { fileCharsetDetector.readContent(uri, context) } returns "raw mht"
        every { mhtParser.isMhtFile("f.mht") } returns true
        every { parserRegistry.parseRaw("raw mht", "f.mht") } returns ParsedChapter(novelTitle = "", chapterTitle = "Ch1", content = "<p>hello</p>")

        val result = useCase.importFile(42L, 1L, uri)

        assertThat(result.isSuccess).isTrue()
        coVerify { chapterDao.updateContent(42L, "<p>hello</p>", "Ch1") }
    }

    @Test
    fun `importFile with HTML file parses via parse and calls updateContent`() = runTest {
        val uri: Uri = mockk()
        every { fileCharsetDetector.getFileName(uri, context) } returns "f.html"
        coEvery { fileCharsetDetector.readContent(uri, context) } returns "<html><body><p>content</p></body></html>"
        every { mhtParser.isMhtFile("f.html") } returns false
        every { parserRegistry.parse("<html><body><p>content</p></body></html>", "f.html") } returns ParsedChapter(novelTitle = "", chapterTitle = "Ch2", content = "<p>content</p>")

        val result = useCase.importFile(43L, 2L, uri)

        assertThat(result.isSuccess).isTrue()
        coVerify { chapterDao.updateContent(43L, "<p>content</p>", "Ch2") }
    }

    @Test
    fun `importFile returns failure on read error`() = runTest {
        val uri: Uri = mockk()
        every { fileCharsetDetector.getFileName(uri, context) } returns "f.html"
        coEvery { fileCharsetDetector.readContent(uri, context) } throws IOException("file error")

        val result = useCase.importFile(44L, 3L, uri)

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { chapterDao.updateContent(any(), any(), any()) }
    }
}
