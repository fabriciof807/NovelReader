package com.novelreader.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportDataUseCaseTest {

    private val novelDao: NovelDao = mockk()
    private val chapterDao: ChapterDao = mockk()
    private val bookmarkDao: BookmarkDao = mockk()
    private val characterDao: CharacterDao = mockk()
    private val characterPhotoDao: CharacterPhotoDao = mockk()

    private lateinit var useCase: ExportDataUseCase

    @Before
    fun setUp() {
        useCase = ExportDataUseCase(
            novelDao = novelDao,
            chapterDao = chapterDao,
            bookmarkDao = bookmarkDao,
            characterDao = characterDao,
            characterPhotoDao = characterPhotoDao,
            ioDispatcher = Dispatchers.Unconfined
        )
        every { novelDao.getAllNovels() } returns flowOf(
            listOf(
                NovelEntity(
                    id = 1,
                    title = "Favorite novel",
                    sourceUrl = "https://example.com/novel",
                    isFavorite = true
                )
            )
        )
        coEvery { bookmarkDao.getAllSync() } returns listOf(
            BookmarkEntity(id = 1, chapterId = 10, title = "Bookmark")
        )
        coEvery { chapterDao.getChapterById(10) } returns ChapterEntity(
            id = 10,
            novelId = 1,
            title = "Chapter 1",
            fileName = "chapter-1",
            orderIndex = 1,
            content = "content"
        )
        coEvery { characterDao.getAllCharactersSync() } returns listOf(
            CharacterEntity(id = 20, novelId = 1, name = "Character")
        )
        coEvery { characterPhotoDao.getByCharacterIds(any()) } returns emptyList()
    }

    @Test
    fun `default export writes version 2 and novel favorite`() = runTest {
        val root = JSONObject(useCase.execute())

        assertThat(root.getInt("version")).isEqualTo(2)
        assertThat(root.getJSONArray("novels").getJSONObject(0).getBoolean("isFavorite"))
            .isTrue()
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(1)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(1)
    }

    @Test
    fun `excluding novels writes an explicit empty novels array`() = runTest {
        val root = JSONObject(useCase.execute(ExportOptions(novels = false)))

        assertThat(root.getJSONArray("novels").length()).isEqualTo(0)
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(1)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(1)
    }

    @Test
    fun `excluding bookmarks writes an explicit empty bookmarks array`() = runTest {
        val root = JSONObject(useCase.execute(ExportOptions(bookmarks = false)))

        assertThat(root.getJSONArray("novels").length()).isEqualTo(1)
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(0)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(1)
    }

    @Test
    fun `excluding characters writes an explicit empty characters array`() = runTest {
        val root = JSONObject(useCase.execute(ExportOptions(characters = false)))

        assertThat(root.getJSONArray("novels").length()).isEqualTo(1)
        assertThat(root.getJSONArray("bookmarks").length()).isEqualTo(1)
        assertThat(root.getJSONArray("characters").length()).isEqualTo(0)
    }
}
