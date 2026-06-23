package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.FailedChapterErrorType
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.webimport.NovelImporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScanMissingChaptersUseCaseTest {
    private val novelDao = mockk<NovelDao>(relaxed = true)
    private val chapterDao = mockk<ChapterDao>(relaxed = true)
    private val failedChapterDao = mockk<FailedChapterDao>(relaxed = true)
    private val webImportUseCase = mockk<WebImportUseCase>(relaxed = true)
    private val novelImporter = mockk<NovelImporter>(relaxed = true)

    private val useCase = ScanMissingChaptersUseCase(
        novelDao = novelDao,
        chapterDao = chapterDao,
        failedChapterDao = failedChapterDao,
        webImportUseCase = webImportUseCase,
        novelImporter = novelImporter
    )

    @Test
    fun `scanLocal writes FailedChapterEntity with fileName matching chapter_N pattern`() = runTest {
        val novel = NovelEntity(id = 1, title = "X", sourceUrl = "")
        coEvery { novelDao.getNovelById(1) } returns novel
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns emptyList<ChapterEntity>()

        useCase.scanLocal(1, from = 5, to = 5)

        coVerify {
            failedChapterDao.insert(match {
                it.chapterNumber == 5 &&
                    it.errorType == FailedChapterErrorType.MISSING_NUMBER &&
                    it.fileName == "chapter_5"
            })
        }
    }

    @Test
    fun `scanLocal flags multiple missing chapters in range`() = runTest {
        val novel = NovelEntity(id = 1, title = "X", sourceUrl = "")
        coEvery { novelDao.getNovelById(1) } returns novel
        coEvery { chapterDao.getChaptersByNovelSync(1) } returns emptyList<ChapterEntity>()

        val result = useCase.scanLocal(1, from = 1, to = 3)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.missing).isEqualTo(3)
    }
}
