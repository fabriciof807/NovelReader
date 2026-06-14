package com.novelreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.NovelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CharacterRepositoryTest {

    private lateinit var database: NovelDatabase
    private lateinit var repository: CharacterRepository
    private lateinit var photoRepository: CharacterPhotoRepository
    private var novelId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CharacterRepository(database.characterDao())
        photoRepository = CharacterPhotoRepository(database.characterPhotoDao())
        novelId = database.novelDao().insert(
            NovelEntity(title = "The Lost Kingdom", totalChapters = 0)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve() = runBlocking {
        val id = repository.insert(
            CharacterEntity(novelId = novelId, name = "King Arthur")
        )
        val characters = repository.getByNovel(novelId).first()
        assertThat(characters).hasSize(1)
        assertThat(characters[0].name).isEqualTo("King Arthur")
    }

    @Test
    fun deleteById() = runBlocking {
        val id = repository.insert(
            CharacterEntity(novelId = novelId, name = "Merlin")
        )
        repository.deleteById(id)
        val characters = repository.getByNovel(novelId).first()
        assertThat(characters).isEmpty()
    }

    @Test
    fun toggleFavorite_true() = runBlocking {
        val id = repository.insert(
            CharacterEntity(novelId = novelId, name = "Morgana", isFavorite = false)
        )
        repository.toggleFavorite(id, true)
        val character = repository.getByNovelSync(novelId).first()
        assertThat(character.isFavorite).isTrue()
    }

    @Test
    fun toggleFavorite_false() = runBlocking {
        val id = repository.insert(
            CharacterEntity(novelId = novelId, name = "Guinevere", isFavorite = true)
        )
        repository.toggleFavorite(id, false)
        val character = repository.getByNovelSync(novelId).first()
        assertThat(character.isFavorite).isFalse()
    }

    @Test
    fun updatePhoto() = runBlocking {
        val id = repository.insert(
            CharacterEntity(novelId = novelId, name = "Lancelot")
        )
        repository.updatePhoto(id, "/photos/lancelot.jpg")
        val character = repository.getByNovelSync(novelId).first()
        assertThat(character.photoPath).isEqualTo("/photos/lancelot.jpg")
    }

    @Test
    fun updateName() = runBlocking {
        val id = repository.insert(
            CharacterEntity(novelId = novelId, name = "Unknown")
        )
        repository.updateName(id, "Sir Lancelot")
        val character = repository.getByNovelSync(novelId).first()
        assertThat(character.name).isEqualTo("Sir Lancelot")
    }

    @Test
    fun updateNotes_nullAndText() = runBlocking {
        val id = repository.insert(
            CharacterEntity(novelId = novelId, name = "Gawain")
        )
        repository.updateNotes(id, "Brave knight of the round table")
        var character = repository.getByNovelSync(novelId).first()
        assertThat(character.notes).isEqualTo("Brave knight of the round table")

        repository.updateNotes(id, null)
        character = repository.getByNovelSync(novelId).first()
        assertThat(character.notes).isNull()
    }

    @Test
    fun getByNovel_orderedByFavorite() = runBlocking {
        val id1 = repository.insert(
            CharacterEntity(novelId = novelId, name = "Villain", isFavorite = false)
        )
        val id2 = repository.insert(
            CharacterEntity(novelId = novelId, name = "Hero", isFavorite = true)
        )
        val id3 = repository.insert(
            CharacterEntity(novelId = novelId, name = "Sidekick", isFavorite = false)
        )
        repository.toggleFavorite(id1, true)

        val characters = repository.getByNovel(novelId).first()
        assertThat(characters[0].isFavorite).isTrue()
    }

    @Test
    fun cascadeDelete() = runBlocking {
        val charId = repository.insert(
            CharacterEntity(novelId = novelId, name = "Percival")
        )
        photoRepository.insert(
            CharacterPhotoEntity(characterId = charId, photoPath = "/photos/p.jpg")
        )
        database.novelDao().deleteById(novelId)
        val characters = repository.getByNovel(novelId).first()
        assertThat(characters).isEmpty()
    }

    @Test
    fun getByNovelSync_matchesFlow() = runBlocking {
        repository.insert(CharacterEntity(novelId = novelId, name = "Tristan"))
        val sync = repository.getByNovelSync(novelId)
        val flow = repository.getByNovel(novelId).first()
        assertThat(sync).isEqualTo(flow)
    }
}
