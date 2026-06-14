package com.novelreader.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterPhotoDaoTest {

    private lateinit var db: NovelDatabase
    private lateinit var novelDao: NovelDao
    private lateinit var characterDao: CharacterDao
    private lateinit var photoDao: CharacterPhotoDao
    private var characterId: Long = 0L

    @Before
    fun createDb() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        ).allowMainThreadQueries().build()
        novelDao = db.novelDao()
        characterDao = db.characterDao()
        photoDao = db.characterPhotoDao()

        val novelId = novelDao.insert(NovelEntity(title = "The Lost Kingdom", totalChapters = 0))
        characterId = characterDao.insert(CharacterEntity(novelId = novelId, name = "King Arthur"))
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetByCharacter() = runTest {
        photoDao.insert(CharacterPhotoEntity(characterId = characterId, photoPath = "/photos/arthur_1.jpg"))
        val photos = photoDao.getByCharacter(characterId).first()
        assertThat(photos).hasSize(1)
        assertThat(photos[0].photoPath).isEqualTo("/photos/arthur_1.jpg")
    }

    @Test
    fun deleteById_removes() = runTest {
        val id = photoDao.insert(CharacterPhotoEntity(characterId = characterId, photoPath = "/photos/delete_me.jpg"))
        photoDao.deleteById(id)
        val photos = photoDao.getByCharacter(characterId).first()
        assertThat(photos).isEmpty()
    }

    @Test
    fun getByCharacterIds_multiple() = runTest {
        val novelId = novelDao.getAllNovels().first().first().id
        val charId2 = characterDao.insert(CharacterEntity(novelId = novelId, name = "Merlin"))

        photoDao.insert(CharacterPhotoEntity(characterId = characterId, photoPath = "/photos/a1.jpg"))
        photoDao.insert(CharacterPhotoEntity(characterId = charId2, photoPath = "/photos/m1.jpg"))

        val photos = photoDao.getByCharacterIds(listOf(characterId, charId2))
        assertThat(photos).hasSize(2)
    }

    @Test
    fun cascadeDelete_characterDeleted() = runTest {
        photoDao.insert(CharacterPhotoEntity(characterId = characterId, photoPath = "/photos/will_delete.jpg"))
        characterDao.deleteById(characterId)
        val photos = photoDao.getByCharacter(characterId).first()
        assertThat(photos).isEmpty()
    }

    @Test
    fun orderByIndex_ascending() = runTest {
        photoDao.insert(CharacterPhotoEntity(characterId = characterId, photoPath = "/photos/second.jpg", orderIndex = 2))
        photoDao.insert(CharacterPhotoEntity(characterId = characterId, photoPath = "/photos/first.jpg", orderIndex = 1))
        val photos = photoDao.getByCharacter(characterId).first()
        assertThat(photos[0].orderIndex).isLessThan(photos[1].orderIndex)
    }

    @Test
    fun insertMultiple_allReturned() = runTest {
        repeat(4) { i ->
            photoDao.insert(CharacterPhotoEntity(characterId = characterId, photoPath = "/photos/p$i.jpg", orderIndex = i))
        }
        val photos = photoDao.getByCharacter(characterId).first()
        assertThat(photos).hasSize(4)
    }
}
