package com.novelreader.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.entity.FolderEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.db.entity.NovelFolderCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FolderDaoTest {
    private lateinit var db: NovelDatabase
    private lateinit var folderDao: FolderDao
    private lateinit var novelDao: NovelDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).build()
        folderDao = db.folderDao()
        novelDao = db.novelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getAll returns folders pinned first then by name`() = runTest {
        folderDao.insert(FolderEntity(name = "Zebra"))
        folderDao.insert(FolderEntity(name = "Apple"))
        folderDao.insert(FolderEntity(name = "Mango", isPinned = true))
        val all = folderDao.getAll().first()
        assertThat(all.map { it.name }).containsExactly("Mango", "Apple", "Zebra").inOrder()
    }

    @Test
    fun `insert rename and delete folder`() = runTest {
        val id = folderDao.insert(FolderEntity(name = "Old"))
        folderDao.rename(id, "New")
        assertThat(folderDao.getFolder(id)?.name).isEqualTo("New")
        folderDao.delete(id)
        assertThat(folderDao.getFolder(id)).isNull()
    }

    @Test
    fun `setPinned toggles pin and countPinned reflects it`() = runTest {
        val a = folderDao.insert(FolderEntity(name = "A"))
        val b = folderDao.insert(FolderEntity(name = "B"))
        assertThat(folderDao.countPinned()).isEqualTo(0)
        folderDao.setPinned(a, true)
        folderDao.setPinned(b, true)
        assertThat(folderDao.countPinned()).isEqualTo(2)
        folderDao.setPinned(a, false)
        assertThat(folderDao.countPinned()).isEqualTo(1)
    }

    @Test
    fun `addNovelToFolder and getNovelsInFolder returns the novels`() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "F"))
        val n1 = novelDao.insert(NovelEntity(title = "Novel 1", sourceFolder = "", totalChapters = 1))
        val n2 = novelDao.insert(NovelEntity(title = "Novel 2", sourceFolder = "", totalChapters = 1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(folderId, n1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(folderId, n2))
        val novels = folderDao.getNovelsInFolder(folderId).first()
        assertThat(novels.map { it.title }).containsExactly("Novel 1", "Novel 2")
        assertThat(folderDao.getFolderCounts().first().first().count).isEqualTo(2)
    }

    @Test
    fun `addNovelToFolder ignores duplicates`() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "F"))
        val n1 = novelDao.insert(NovelEntity(title = "N", sourceFolder = "", totalChapters = 1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(folderId, n1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(folderId, n1))
        assertThat(folderDao.getNovelsInFolder(folderId).first()).hasSize(1)
    }

    @Test
    fun `removeNovelFromFolder removes only the link`() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "F"))
        val n1 = novelDao.insert(NovelEntity(title = "N", sourceFolder = "", totalChapters = 1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(folderId, n1))
        folderDao.removeNovelFromFolder(folderId, n1)
        assertThat(folderDao.getNovelsInFolder(folderId).first()).isEmpty()
        assertThat(novelDao.getNovelById(n1)).isNotNull()
    }

    @Test
    fun `setNovelFolders diffs insert and remove`() = runTest {
        val f1 = folderDao.insert(FolderEntity(name = "F1"))
        val f2 = folderDao.insert(FolderEntity(name = "F2"))
        val f3 = folderDao.insert(FolderEntity(name = "F3"))
        val n1 = novelDao.insert(NovelEntity(title = "N", sourceFolder = "", totalChapters = 1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(f1, n1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(f2, n1))
        folderDao.setNovelFolders(n1, setOf(f2, f3))
        val ids = folderDao.getFolderIdsForNovel(n1).toSet()
        assertThat(ids).containsExactly(f2, f3)
    }

    @Test
    fun `addNovelsToFolder adds multiple at once`() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "F"))
        val n1 = novelDao.insert(NovelEntity(title = "N1", sourceFolder = "", totalChapters = 1))
        val n2 = novelDao.insert(NovelEntity(title = "N2", sourceFolder = "", totalChapters = 1))
        folderDao.addNovelsToFolder(folderId, listOf(n1, n2))
        assertThat(folderDao.getNovelsInFolder(folderId).first()).hasSize(2)
    }

    @Test
    fun `deleting a folder cascade deletes its links but not novels`() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "F"))
        val n1 = novelDao.insert(NovelEntity(title = "N", sourceFolder = "", totalChapters = 1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(folderId, n1))
        folderDao.delete(folderId)
        assertThat(folderDao.getFolderIdsForNovel(n1)).isEmpty()
        assertThat(novelDao.getNovelById(n1)).isNotNull()
    }

    @Test
    fun `deleting a novel cascade deletes its folder links`() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "F"))
        val n1 = novelDao.insert(NovelEntity(title = "N", sourceFolder = "", totalChapters = 1))
        folderDao.addNovelToFolder(NovelFolderCrossRef(folderId, n1))
        novelDao.deleteById(n1)
        assertThat(folderDao.getNovelsInFolder(folderId).first()).isEmpty()
    }
}
