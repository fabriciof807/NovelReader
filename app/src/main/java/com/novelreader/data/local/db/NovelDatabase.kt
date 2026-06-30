package com.novelreader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.novelreader.data.local.db.dao.BookmarkDao
import com.novelreader.data.local.db.dao.CharacterDao
import com.novelreader.data.local.db.dao.CharacterPhotoDao
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.local.db.dao.FailedChapterDao
import com.novelreader.data.local.db.dao.NovelDao
import com.novelreader.data.local.db.dao.NovelSourceDao
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.ChapterFts
import com.novelreader.data.local.db.entity.FailedChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.local.db.entity.NovelSourceEntity

@Database(
    entities = [NovelEntity::class, ChapterEntity::class, BookmarkEntity::class, CharacterEntity::class, CharacterPhotoEntity::class, ChapterFts::class, FailedChapterEntity::class, NovelSourceEntity::class],
    version = 9,
    exportSchema = true
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun characterDao(): CharacterDao
    abstract fun characterPhotoDao(): CharacterPhotoDao
    abstract fun failedChapterDao(): FailedChapterDao
    abstract fun novelSourceDao(): NovelSourceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `novels` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT,
                        `coverPath` TEXT,
                        `sourceFolder` TEXT NOT NULL DEFAULT '',
                        `totalChapters` INTEGER NOT NULL DEFAULT 0,
                        `lastChapterId` INTEGER,
                        `lastReadAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chapters` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `novelId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `isRead` INTEGER NOT NULL DEFAULT 0,
                        `lastScrollPosition` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (`novelId`) REFERENCES `novels`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_novelId` ON `chapters` (`novelId`)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `chapterId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `note` TEXT,
                        `page` INTEGER NOT NULL DEFAULT 0,
                        `scrollPosition` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY (`chapterId`) REFERENCES `chapters`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_chapterId` ON `bookmarks` (`chapterId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `characters` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `novelId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `photoPath` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY (`novelId`) REFERENCES `novels`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_characters_novelId` ON `characters` (`novelId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_photos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER NOT NULL,
                        `photoPath` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY (`characterId`) REFERENCES `characters`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_character_photos_characterId` ON `character_photos` (`characterId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE characters ADD COLUMN notes TEXT")
                database.execSQL("ALTER TABLE characters ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `chapters_fts` USING fts4(content=`chapters`, `title`, `content`)")
                database.execSQL("INSERT INTO chapters_fts(chapters_fts) VALUES('rebuild')")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE novels ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE novels ADD COLUMN lastCheckedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE novels ADD COLUMN autoUpdate INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `failed_chapters` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `novelId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `url` TEXT,
                        `sourceType` TEXT NOT NULL,
                        `chapterNumber` INTEGER NOT NULL,
                        `errorType` TEXT NOT NULL,
                        `errorMessage` TEXT NOT NULL,
                        `attemptedAt` INTEGER NOT NULL,
                        FOREIGN KEY (`novelId`) REFERENCES `novels`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_failed_chapters_novelId` ON `failed_chapters` (`novelId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_failed_chapters_fileName` ON `failed_chapters` (`fileName`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE novels ADD COLUMN hasUpdates INTEGER NOT NULL DEFAULT 0")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `novel_sources` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `novelId` INTEGER NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `isPrimary` INTEGER NOT NULL DEFAULT 0,
                        `lastCheckedAt` INTEGER NOT NULL DEFAULT 0,
                        `autoUpdate` INTEGER NOT NULL DEFAULT 1,
                        `addedAt` INTEGER NOT NULL,
                        FOREIGN KEY (`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_novel_sources_novelId_sourceUrl` ON `novel_sources` (`novelId`, `sourceUrl`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_sources_novelId` ON `novel_sources` (`novelId`)")
                database.execSQL("""
                    INSERT INTO novel_sources (novelId, sourceUrl, domain, isPrimary, lastCheckedAt, autoUpdate, addedAt)
                    SELECT id, sourceUrl, '', 1, lastCheckedAt, autoUpdate, lastCheckedAt
                    FROM novels WHERE sourceUrl != ''
                """)
            }
        }
    }
}
