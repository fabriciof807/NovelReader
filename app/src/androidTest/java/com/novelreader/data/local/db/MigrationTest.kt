package com.novelreader.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NovelDatabase::class.java
    )

    @Test
    fun migrateAll() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 7, true,
            NovelDatabase.MIGRATION_1_2,
            NovelDatabase.MIGRATION_2_3,
            NovelDatabase.MIGRATION_3_4,
            NovelDatabase.MIGRATION_4_5,
            NovelDatabase.MIGRATION_5_6,
            NovelDatabase.MIGRATION_6_7
        ).close()
    }

    @Test
    fun migrate2to3_charactersTableCreated() {
        val db = helper.createDatabase(TEST_DB, 2)
        db.execSQL("INSERT INTO novels (title, sourceFolder, totalChapters, lastReadAt, createdAt) VALUES ('Test', '', 0, 0, 0)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 3, true, NovelDatabase.MIGRATION_2_3
        )

        val cursor = migratedDb.query("SELECT * FROM characters")
        assert(cursor.count == 0)
        cursor.close()

        val novelCursor = migratedDb.query("SELECT title FROM novels")
        assert(novelCursor.moveToFirst())
        assert(novelCursor.getString(0) == "Test")
        novelCursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate3to4_characterPhotosTableCreated() {
        val db = helper.createDatabase(TEST_DB, 3)
        db.execSQL("INSERT INTO novels (title, sourceFolder, totalChapters, lastReadAt, createdAt) VALUES ('T', '', 0, 0, 0)")
        db.execSQL("INSERT INTO characters (novelId, name, createdAt) VALUES (1, 'Hero', 0)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, NovelDatabase.MIGRATION_3_4
        )

        val cursor = migratedDb.query("SELECT * FROM character_photos")
        assert(cursor.count == 0)
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate4to5_addsNotesAndIsFavorite() {
        val db = helper.createDatabase(TEST_DB, 4)
        db.execSQL("INSERT INTO novels (title, sourceFolder, totalChapters, lastReadAt, createdAt) VALUES ('T', '', 0, 0, 0)")
        db.execSQL("INSERT INTO characters (novelId, name, createdAt) VALUES (1, 'Hero', 0)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 5, true, NovelDatabase.MIGRATION_4_5
        )

        val cursor = migratedDb.query("SELECT notes, isFavorite FROM characters")
        assert(cursor.moveToFirst())
        assert(cursor.isNull(0))
        assert(cursor.getInt(1) == 0)
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate5to6_ftsTablePopulated() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL("INSERT INTO novels (title, sourceFolder, totalChapters, lastReadAt, createdAt) VALUES ('T', '', 0, 0, 0)")
        db.execSQL("INSERT INTO chapters (novelId, title, fileName, orderIndex, content, isRead, lastScrollPosition) VALUES (1, 'Ch1', 'f.html', 0, 'dragon awakens', 0, 0)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 6, true, NovelDatabase.MIGRATION_5_6
        )

        val cursor = migratedDb.query(
            "SELECT c.title FROM chapters c JOIN chapters_fts f ON c.rowid = f.rowid WHERE chapters_fts MATCH 'dragon'"
        )
        assert(cursor.moveToFirst())
        assert(cursor.getString(0) == "Ch1")
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate6to7_addsWebImportColumns() {
        val db = helper.createDatabase(TEST_DB, 6)
        db.execSQL("INSERT INTO novels (title, sourceFolder, totalChapters, lastReadAt, createdAt) VALUES ('T', '', 0, 0, 0)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 7, true, NovelDatabase.MIGRATION_6_7
        )

        val cursor = migratedDb.query("SELECT sourceUrl, lastCheckedAt, autoUpdate FROM novels")
        assert(cursor.moveToFirst())
        assert(cursor.getString(0) == "")
        assert(cursor.getLong(1) == 0L)
        assert(cursor.getInt(2) == 0)
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate9to10_addsFavoriteColumnDefaultFalse() {
        val db = helper.createDatabase(TEST_DB, 9)
        db.execSQL(
            "INSERT INTO novels (title, sourceFolder, totalChapters, lastReadAt, createdAt, sourceUrl, lastCheckedAt, autoUpdate, hasUpdates) " +
                "VALUES ('T', '', 0, 0, 0, '', 0, 0, 0)"
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 10, true, NovelDatabase.MIGRATION_9_10
        )

        val cursor = migratedDb.query("SELECT isFavorite FROM novels")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("isFavorite"))).isEqualTo(0)
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate11to12_createsFoldersAndNovelFolderTables() {
        val db = helper.createDatabase(TEST_DB, 11)
        db.execSQL(
            "INSERT INTO novels (title, sourceFolder, totalChapters, lastReadAt, createdAt, sourceUrl, lastCheckedAt, autoUpdate, hasUpdates, isFavorite) " +
                "VALUES ('T', '', 0, 0, 0, '', 0, 0, 0, 0)"
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 12, true, NovelDatabase.MIGRATION_11_12
        )

        val foldersCursor = migratedDb.query("SELECT * FROM folders")
        assertThat(foldersCursor.count).isEqualTo(0)
        foldersCursor.close()

        migratedDb.execSQL(
            "INSERT INTO folders (name, isPinned, createdAt) VALUES ('Reading', 1, 0)"
        )
        migratedDb.execSQL(
            "INSERT INTO novel_folder (folderId, novelId) VALUES (1, 1)"
        )
        val linkCursor = migratedDb.query("SELECT folderId, novelId FROM novel_folder")
        assertThat(linkCursor.moveToFirst()).isTrue()
        assertThat(linkCursor.getLong(0)).isEqualTo(1L)
        assertThat(linkCursor.getLong(1)).isEqualTo(1L)
        linkCursor.close()
        migratedDb.close()
    }
}
