package com.novelreader.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterDaoTest {

    private lateinit var db: NovelDatabase
    private lateinit var novelDao: NovelDao
    private lateinit var characterDao: CharacterDao
    private var novelId: Long = 0L

    @Before
    fun createDb() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).allowMainThreadQueries().build()
        novelDao = db.novelDao()
        characterDao = db.characterDao()
        novelId = novelDao.insert(NovelEntity(title = "The Lost Kingdom", totalChapters = 0))
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetByNovel() = runTest {
        characterDao.insert(CharacterEntity(novelId = novelId, name = "King Arthur"))
        val characters = characterDao.getByNovel(novelId).first()
        assertThat(characters).hasSize(1)
        assertThat(characters[0].name).isEqualTo("King Arthur")
    }

    @Test
    fun deleteById_removes() = runTest {
        val id = characterDao.insert(CharacterEntity(novelId = novelId, name = "Merlin"))
        characterDao.deleteById(id)
        val characters = characterDao.getByNovel(novelId).first()
        assertThat(characters).isEmpty()
    }

    @Test
    fun toggleFavorite_persists() = runTest {
        val id = characterDao.insert(CharacterEntity(novelId = novelId, name = "Morgana", isFavorite = false))
        characterDao.toggleFavorite(id, true)
        var character = characterDao.getByNovelSync(novelId).first()
        assertThat(character.isFavorite).isTrue()

        characterDao.toggleFavorite(id, false)
        character = characterDao.getByNovelSync(novelId).first()
        assertThat(character.isFavorite).isFalse()
    }

    @Test
    fun updatePhoto_persists() = runTest {
        val id = characterDao.insert(CharacterEntity(novelId = novelId, name = "Lancelot"))
        characterDao.updatePhoto(id, "/photos/lancelot.jpg")
        val character = characterDao.getByNovelSync(novelId).first()
        assertThat(character.photoPath).isEqualTo("/photos/lancelot.jpg")
    }

    @Test
    fun updateName_persists() = runTest {
        val id = characterDao.insert(CharacterEntity(novelId = novelId, name = "Unknown"))
        characterDao.updateName(id, "Sir Lancelot")
        val character = characterDao.getByNovelSync(novelId).first()
        assertThat(character.name).isEqualTo("Sir Lancelot")
    }

    @Test
    fun updateNotes_nullAndText() = runTest {
        val id = characterDao.insert(CharacterEntity(novelId = novelId, name = "Gawain"))
        characterDao.updateNotes(id, "Brave knight")
        var character = characterDao.getByNovelSync(novelId).first()
        assertThat(character.notes).isEqualTo("Brave knight")

        characterDao.updateNotes(id, null)
        character = characterDao.getByNovelSync(novelId).first()
        assertThat(character.notes).isNull()
    }

    @Test
    fun getByNovel_orderedByFavoriteDesc() = runTest {
        characterDao.insert(CharacterEntity(novelId = novelId, name = "Villain", isFavorite = false))
        val heroId = characterDao.insert(CharacterEntity(novelId = novelId, name = "Hero", isFavorite = true))
        characterDao.insert(CharacterEntity(novelId = novelId, name = "Sidekick", isFavorite = false))
        characterDao.toggleFavorite(heroId, true)

        val characters = characterDao.getByNovel(novelId).first()
        assertThat(characters[0].isFavorite).isTrue()
    }

    @Test
    fun cascadeDelete_novelDeleted() = runTest {
        characterDao.insert(CharacterEntity(novelId = novelId, name = "Percival"))
        novelDao.deleteById(novelId)
        val characters = characterDao.getByNovel(novelId).first()
        assertThat(characters).isEmpty()
    }

    @Test
    fun getByNovelSync_matchesFlow() = runTest {
        characterDao.insert(CharacterEntity(novelId = novelId, name = "Tristan"))
        val sync = characterDao.getByNovelSync(novelId)
        val flow = characterDao.getByNovel(novelId).first()
        assertThat(sync).isEqualTo(flow)
    }

    @Test
    fun insertMultiple_allReturned() = runTest {
        repeat(5) { i ->
            characterDao.insert(CharacterEntity(novelId = novelId, name = "Character $i"))
        }
        val characters = characterDao.getByNovel(novelId).first()
        assertThat(characters).hasSize(5)
    }
}
