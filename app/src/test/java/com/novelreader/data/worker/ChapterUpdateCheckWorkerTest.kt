package com.novelreader.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.dao.NovelSourceDao
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.db.entity.NovelSourceEntity
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.FetchResult
import com.novelreader.domain.usecase.WebImportUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The automatic update check used to schedule its import straight through
 * [ImportWorkScheduler], past the manager that owns the import state the library banner reads. The
 * banner then named whatever novel the reader had started by hand while this job downloaded, with a
 * counter that never moved. The check has to enter through the manager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChapterUpdateCheckWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `an update check starts its import through the import manager`() = runTest {
        val novelDao = mockk<NovelDao>(relaxed = true)
        val chapterDao = mockk<ChapterDao>(relaxed = true)
        val novelSourceDao = mockk<NovelSourceDao>(relaxed = true)
        val webImportUseCase = mockk<WebImportUseCase>()
        val notificationHelper = mockk<UpdateNotificationHelper>(relaxed = true)
        val importManager = mockk<BackgroundImportManager>(relaxed = true)

        val novel = NovelEntity(
            id = 7L,
            title = "Child of Destiny",
            sourceUrl = "https://www.freewebnovel.com/novel/child-of-destiny",
            autoUpdate = true
        )
        coEvery { novelSourceDao.getAllForAutoUpdate() } returns listOf(
            NovelSourceEntity(
                id = 1L,
                novelId = 7L,
                sourceUrl = novel.sourceUrl,
                domain = "www.freewebnovel.com",
                autoUpdate = true
            )
        )
        coEvery { novelDao.getNovelById(7L) } returns novel
        coEvery { chapterDao.getChaptersByNovelSync(7L) } returns emptyList()
        coEvery { chapterDao.getEmptyChapters(7L) } returns emptyList()
        coEvery { webImportUseCase.fetchChapterList(novel.sourceUrl) } returns kotlin.Result.success(
            FetchResult(
                chapters = listOf(ChapterLink(title = "Chapter 1600", url = "https://x/1600.html", chapterNumber = 1600)),
                novelTitle = novel.title
            )
        )

        val result = worker(novelDao, chapterDao, novelSourceDao, webImportUseCase, notificationHelper, importManager)
            .doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val started = slot<List<ChapterLink>>()
        coVerify(exactly = 1) {
            importManager.startImport(
                novelTitle = novel.title,
                links = capture(started),
                coverUrl = null,
                sourceUrl = novel.sourceUrl,
                domain = "www.freewebnovel.com",
                targetNovelId = 7L
            )
        }
        assertThat(started.captured.map { it.chapterNumber }).containsExactly(1600)
    }

    @Test
    fun `a novel with nothing new does not start an import`() = runTest {
        val novelDao = mockk<NovelDao>(relaxed = true)
        val chapterDao = mockk<ChapterDao>(relaxed = true)
        val novelSourceDao = mockk<NovelSourceDao>(relaxed = true)
        val webImportUseCase = mockk<WebImportUseCase>()
        val notificationHelper = mockk<UpdateNotificationHelper>(relaxed = true)
        val importManager = mockk<BackgroundImportManager>(relaxed = true)

        val novel = NovelEntity(id = 7L, title = "Child of Destiny", sourceUrl = "https://x", autoUpdate = true)
        coEvery { novelSourceDao.getAllForAutoUpdate() } returns listOf(
            NovelSourceEntity(id = 1L, novelId = 7L, sourceUrl = "https://x", autoUpdate = true)
        )
        coEvery { novelDao.getNovelById(7L) } returns novel
        coEvery { chapterDao.getChaptersByNovelSync(7L) } returns emptyList()
        coEvery { chapterDao.getEmptyChapters(7L) } returns emptyList()
        coEvery { webImportUseCase.fetchChapterList("https://x") } returns kotlin.Result.success(
            FetchResult(chapters = emptyList())
        )

        worker(novelDao, chapterDao, novelSourceDao, webImportUseCase, notificationHelper, importManager)
            .doWork()

        coVerify(exactly = 0) { importManager.startImport(any(), any(), any(), any(), any(), any()) }
    }

    private fun worker(
        novelDao: NovelDao,
        chapterDao: ChapterDao,
        novelSourceDao: NovelSourceDao,
        webImportUseCase: WebImportUseCase,
        notificationHelper: UpdateNotificationHelper,
        importManager: BackgroundImportManager
    ): ChapterUpdateCheckWorker {
        val params = mockk<WorkerParameters>(relaxed = true)
        return ChapterUpdateCheckWorker(
            appContext = context,
            params = params,
            novelDao = novelDao,
            chapterDao = chapterDao,
            novelSourceDao = novelSourceDao,
            webImportUseCase = webImportUseCase,
            notificationHelper = notificationHelper,
            backgroundImportManager = importManager,
            io = Dispatchers.Unconfined
        )
    }
}
