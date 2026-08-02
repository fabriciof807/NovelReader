package com.novelreader.di

import android.content.Context
import androidx.room.Room
import com.novelreader.data.local.db.NovelDatabase
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.dao.NovelSourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NovelDatabase {
        return Room.databaseBuilder(
            context,
            NovelDatabase::class.java,
            "novel_reader.db"
        ).addMigrations(
            NovelDatabase.MIGRATION_1_2,
            NovelDatabase.MIGRATION_2_3,
            NovelDatabase.MIGRATION_3_4,
            NovelDatabase.MIGRATION_4_5,
            NovelDatabase.MIGRATION_5_6,
            NovelDatabase.MIGRATION_6_7,
            NovelDatabase.MIGRATION_7_8,
            NovelDatabase.MIGRATION_8_9,
            NovelDatabase.MIGRATION_9_10
        ).build()
    }

    @Provides
    fun provideNovelDao(database: NovelDatabase): NovelDao = database.novelDao()

    @Provides
    fun provideChapterDao(database: NovelDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideBookmarkDao(database: NovelDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideCharacterDao(database: NovelDatabase): CharacterDao = database.characterDao()

    @Provides
    fun provideCharacterPhotoDao(database: NovelDatabase): CharacterPhotoDao = database.characterPhotoDao()

    @Provides
    fun provideFailedChapterDao(database: NovelDatabase): FailedChapterDao = database.failedChapterDao()

    @Provides
    fun provideNovelSourceDao(database: NovelDatabase): NovelSourceDao = database.novelSourceDao()
}
