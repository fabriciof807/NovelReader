package com.novelreader.domain.usecase

import android.content.Context
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.FolderDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.NovelFolderCrossRef
import com.novelreader.data.storage.PendingBookmark
import com.novelreader.data.storage.PendingCharacter
import com.novelreader.data.storage.PendingCollectionLink
import com.novelreader.data.storage.PendingNovel
import com.novelreader.data.storage.PendingPhoto
import com.novelreader.data.storage.PendingRestoreStore
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class AppliedCounts(
    val bookmarks: Int = 0,
    val characters: Int = 0,
    val collectionLinks: Int = 0,
    val novels: Int = 0
) {
    operator fun plus(other: AppliedCounts) = AppliedCounts(
        bookmarks = bookmarks + other.bookmarks,
        characters = characters + other.characters,
        collectionLinks = collectionLinks + other.collectionLinks,
        novels = novels + other.novels
    )
}

@Singleton
class PendingRestoreApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PendingRestoreStore,
    private val backgroundImportManager: BackgroundImportManager,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val characterDao: CharacterDao,
    private val characterPhotoDao: CharacterPhotoDao,
    private val folderDao: FolderDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)
    private val privateRoot: String by lazy { context.filesDir.canonicalPath }

    private fun isOwnedPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val canonical = File(path).canonicalPath
            canonical == privateRoot || canonical.startsWith(privateRoot + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { applyPending() }
        scope.launch {
            backgroundImportManager.state.collect { state ->
                if (!state.running && state.completed) applyPending()
            }
        }
    }

    suspend fun applyPending(): AppliedCounts = mutex.withLock {
        val pending = store.load()
        if (pending.isEmpty()) return AppliedCounts()

        var counts = AppliedCounts()
        val keepBookmarks = mutableListOf<PendingBookmark>()
        for (bm in pending.bookmarks) {
            val applied = restoreBookmark(bm)
            if (applied) counts += AppliedCounts(bookmarks = 1) else keepBookmarks += bm
        }

        val keepCharacters = mutableListOf<PendingCharacter>()
        for (c in pending.characters) {
            val applied = restoreCharacter(c)
            if (applied) counts += AppliedCounts(characters = 1) else keepCharacters += c
        }

        val keepLinks = mutableListOf<PendingCollectionLink>()
        for (link in pending.collectionLinks) {
            val applied = restoreCollectionLink(link)
            if (applied) counts += AppliedCounts(collectionLinks = 1) else keepLinks += link
        }

        val keepNovels = mutableListOf<PendingNovel>()
        for (n in pending.novels) {
            val applied = restoreNovelMetadata(n)
            if (applied) counts += AppliedCounts(novels = 1) else keepNovels += n
        }

        store.update {
            it.copy(
                bookmarks = keepBookmarks,
                characters = keepCharacters,
                collectionLinks = keepLinks,
                novels = keepNovels
            )
        }
        counts
    }

    private suspend fun restoreBookmark(bm: PendingBookmark): Boolean {
        val novel = novelDao.getNovelByTitleIgnoreCase(bm.novelTitle) ?: return false
        val chapter = resolveChapter(novel.id, bm.chapterFileName, bm.chapterOrderIndex) ?: return false
        val duplicate = bookmarkDao.getAllSync().any {
            it.chapterId == chapter.id && it.title == bm.title && it.page == bm.page
        }
        if (duplicate) return true
        bookmarkDao.insert(
            BookmarkEntity(
                chapterId = chapter.id,
                title = bm.title,
                note = bm.note,
                page = bm.page,
                scrollPosition = bm.scrollPosition,
                createdAt = bm.createdAt
            )
        )
        return true
    }

    private suspend fun restoreCharacter(c: PendingCharacter): Boolean {
        val novel = novelDao.getNovelByTitleIgnoreCase(c.novelTitle) ?: return false
        val exists = characterDao.getByNovelSync(novel.id).any { it.name.equals(c.name, ignoreCase = true) }
        if (exists) return true
        val id = characterDao.insert(
            CharacterEntity(
                novelId = novel.id,
                name = c.name,
                photoPath = c.photoPath?.takeIf { isOwnedPath(it) && File(it).exists() },
                notes = c.notes,
                isFavorite = c.isFavorite,
                createdAt = c.createdAt
            )
        )
        for (photo in c.photos) {
            if (isOwnedPath(photo.photoPath) && File(photo.photoPath).exists()) {
                characterPhotoDao.insert(
                    CharacterPhotoEntity(
                        characterId = id,
                        photoPath = photo.photoPath,
                        orderIndex = photo.orderIndex
                    )
                )
            }
        }
        return true
    }

    private suspend fun restoreCollectionLink(link: PendingCollectionLink): Boolean {
        val folder = folderDao.getAll().first().firstOrNull {
            it.name.equals(link.folderName, ignoreCase = true)
        } ?: return false
        val novel = novelDao.getNovelByTitleIgnoreCase(link.novelTitle) ?: return false
        folderDao.addNovelToFolder(NovelFolderCrossRef(folder.id, novel.id))
        return true
    }

    private suspend fun restoreNovelMetadata(n: PendingNovel): Boolean {
        val novel = novelDao.getNovelByTitleIgnoreCase(n.title) ?: return false
        // ponytail: no chapters yet means the import hasn't landed — retry later
        val chapter = resolveChapter(novel.id, n.lastChapterFileName, n.lastChapterOrderIndex)
            ?: return false
        if (novel.lastChapterId != chapter.id) {
            novelDao.updateLastRead(novel.id, chapter.id, n.lastReadAt)
        }
        if (novel.autoUpdate != n.autoUpdate) {
            novelDao.updateAutoUpdate(novel.id, n.autoUpdate)
        }
        if (n.author != null && novel.author != n.author) {
            novelDao.updateAuthor(novel.id, n.author)
        }
        return true
    }

    private suspend fun resolveChapter(novelId: Long, fileName: String, orderIndex: Int) =
        chapterDao.getChapterByNovelAndFileName(novelId, fileName)
            ?: chapterDao.getChaptersByNovelSync(novelId).firstOrNull { it.orderIndex == orderIndex }
}
