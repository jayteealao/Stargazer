package uk.adedamola.stargazer.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.di.MIGRATION_4_5
import uk.adedamola.stargazer.data.local.di.MIGRATION_5_6
import uk.adedamola.stargazer.data.local.di.MIGRATION_6_7

/** Proves the version 5 to 6 upgrade keeps every existing row and adds the new tables. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate5To6_keepsEveryRow() {
        helper.createDatabase(TEST_DB, 5).use { seed(it, version = 5) }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        assertSeededRows(db, version = 5)
        assertNewTablesEmpty(db)
        assertNoForeignKeyViolations(db)
    }

    @Test
    fun migrate4To6() {
        helper.createDatabase(TEST_DB, 4).use { seed(it, version = 4) }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_4_5, MIGRATION_5_6)

        assertSeededRows(db, version = 4)
        assertNewTablesEmpty(db)
        assertNoForeignKeyViolations(db)
    }

    @Test
    fun migrate6To7_keepsEveryRow() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            seed(db, version = 6)
            db.insert("account", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("userId", 5901851L); put("login", "ada-builds"); put("avatarUrl", ""); put("recordedAt", 1L)
            })
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        assertSeededRows(db, version = 6)
        assertEquals(1, count(db, "SELECT COUNT(*) FROM account"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM pending_unstars"))
        assertNoForeignKeyViolations(db)
    }

    @Test
    fun migrate4To7() {
        helper.createDatabase(TEST_DB, 4).use { seed(it, version = 4) }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        assertSeededRows(db, version = 4)
        assertNewTablesEmpty(db)
        assertEquals(0, count(db, "SELECT COUNT(*) FROM pending_unstars"))
        assertNoForeignKeyViolations(db)
    }

    @Test
    fun openWithRoomAfterMigration() {
        helper.createDatabase(TEST_DB, 5).use { seed(it, version = 5) }

        val room = openWithRoom()
        try {
            val db = room.openHelper.writableDatabase
            assertSeededRows(db, version = 5)
            assertNewTablesEmpty(db)
        } finally {
            room.close()
        }
    }

    @Test
    fun cascadeStillWorks() {
        helper.createDatabase(TEST_DB, 5).use { seed(it, version = 5) }

        val room = openWithRoom()
        try {
            val db = room.openHelper.writableDatabase
            db.execSQL("DELETE FROM repositories WHERE id = 1")
            assertEquals(0, count(db, "SELECT COUNT(*) FROM repository_tags WHERE repositoryId = 1"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM repository_tags"))
            assertEquals(2, count(db, "SELECT COUNT(*) FROM repositories"))
        } finally {
            room.close()
        }
    }

    private fun openWithRoom(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    // Seed: three repositories (one with every nullable column null, one favorite and pinned,
    // one plain), two tags, three tag assignments, a preset with null filters, sync state and
    // a Stargazer row.
    private fun seed(db: SupportSQLiteDatabase, version: Int) {
        listOf(
            repo(id = 1, name = "nulls", filled = false, favorite = false, pinned = false, version = version),
            repo(id = 2, name = "fav-pin", filled = true, favorite = true, pinned = true, version = version),
            repo(id = 3, name = "plain", filled = true, favorite = false, pinned = false, version = version),
        ).forEach { db.insert("repositories", SQLiteDatabase.CONFLICT_ABORT, it) }

        db.insert("tags", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("id", 1); put("name", "kotlin"); put("color", "#FF6600"); put("createdAt", 1000L)
        })
        db.insert("tags", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("id", 2); put("name", "tools"); put("color", "#00CCFF"); put("createdAt", 2000L)
        })
        listOf(1 to 1, 1 to 2, 2 to 1).forEach { (repoId, tagId) ->
            db.insert("repository_tags", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("repositoryId", repoId); put("tagId", tagId)
            })
        }

        db.insert("search_presets", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("id", 1); put("name", "My preset"); put("sortBy", "STARRED_DESC")
            putNull("filterLanguage"); putNull("filterMinStars"); putNull("filterMaxStars")
            put("filterHasDescription", 0); put("filterHasHomepage", 0); put("filterHasTopics", 0)
            put("filterFavoritesOnly", 1); put("filterPinnedOnly", 0); putNull("searchQuery")
            put("createdAt", 3000L)
        })

        db.insert("sync_metadata", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("dataType", "starred_repos"); put("lastSyncTimestamp", 4000L); put("lastItemCreatedAt", "2024-01-01T00:00:00Z")
            if (version >= 5) { put("isInitialSyncComplete", 1); put("newestStarredAt", 5000L) }
        })

        db.insert("Stargazer", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("uid", 1); put("name", "legacy")
        })
    }

    private fun repo(id: Int, name: String, filled: Boolean, favorite: Boolean, pinned: Boolean, version: Int) =
        ContentValues().apply {
            put("id", id); put("name", name); put("fullName", "owner/$name"); put("ownerLogin", "owner")
            put("ownerAvatarUrl", "https://example.com/a.png"); put("htmlUrl", "https://github.com/owner/$name")
            if (filled) put("description", "desc $name") else putNull("description")
            put("fork", 0); put("createdAt", "2020-01-01T00:00:00Z"); put("updatedAt", "2021-01-01T00:00:00Z")
            if (filled) put("pushedAt", "2022-01-01T00:00:00Z") else putNull("pushedAt")
            if (filled) put("homepage", "https://example.com") else putNull("homepage")
            put("size", 10); put("stargazersCount", 100 + id); put("watchersCount", 5);
            if (filled) put("language", "Kotlin") else putNull("language")
            put("forksCount", 2); put("openIssuesCount", 1); put("defaultBranch", "main")
            put("topics", "[]"); put("visibility", "public")
            if (filled) put("licenseName", "MIT") else putNull("licenseName")
            put("cachedAt", 6000L); put("isFavorite", if (favorite) 1 else 0); put("isPinned", if (pinned) 1 else 0)
            if (version >= 5) { if (filled) put("starredAt", 7000L + id) else putNull("starredAt") }
        }

    private fun assertSeededRows(db: SupportSQLiteDatabase, version: Int) {
        assertEquals(3, count(db, "SELECT COUNT(*) FROM repositories"))
        assertEquals(2, count(db, "SELECT COUNT(*) FROM tags"))
        assertEquals(3, count(db, "SELECT COUNT(*) FROM repository_tags"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM search_presets"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM sync_metadata"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM Stargazer"))

        db.query("SELECT description, homepage, language, licenseName, starredAt, pushedAt FROM repositories WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            for (i in 0 until c.columnCount) assertTrue("column $i of the null row", c.isNull(i))
        }
        db.query("SELECT name, isFavorite, isPinned, description, starredAt, stargazersCount FROM repositories WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("fav-pin", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(1, c.getInt(2))
            assertEquals("desc fav-pin", c.getString(3))
            if (version >= 5) assertEquals(7002L, c.getLong(4)) else assertTrue(c.isNull(4))
            assertEquals(102, c.getInt(5))
        }
        db.query("SELECT isFavorite, isPinned, language FROM repositories WHERE id = 3").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0)); assertEquals(0, c.getInt(1)); assertEquals("Kotlin", c.getString(2))
        }
        db.query("SELECT name, color, createdAt FROM tags ORDER BY id").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("kotlin", c.getString(0)); assertEquals("#FF6600", c.getString(1)); assertEquals(1000L, c.getLong(2))
            assertTrue(c.moveToNext()); assertEquals("tools", c.getString(0)); assertEquals("#00CCFF", c.getString(1)); assertEquals(2000L, c.getLong(2))
        }
        db.query("SELECT repositoryId, tagId FROM repository_tags ORDER BY repositoryId, tagId").use { c ->
            val rows = mutableListOf<Pair<Int, Int>>()
            while (c.moveToNext()) rows += c.getInt(0) to c.getInt(1)
            assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), rows)
        }
        db.query("SELECT name, sortBy, filterLanguage, filterMinStars, filterMaxStars, searchQuery, filterFavoritesOnly FROM search_presets").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("My preset", c.getString(0)); assertEquals("STARRED_DESC", c.getString(1))
            assertTrue(c.isNull(2)); assertTrue(c.isNull(3)); assertTrue(c.isNull(4)); assertTrue(c.isNull(5))
            assertEquals(1, c.getInt(6))
        }
        db.query("SELECT lastSyncTimestamp, lastItemCreatedAt, isInitialSyncComplete, newestStarredAt FROM sync_metadata").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4000L, c.getLong(0)); assertEquals("2024-01-01T00:00:00Z", c.getString(1))
            if (version >= 5) {
                assertEquals(1, c.getInt(2)); assertEquals(5000L, c.getLong(3))
            } else {
                assertEquals(0, c.getInt(2)); assertTrue(c.isNull(3))
            }
        }
        db.query("SELECT name FROM Stargazer WHERE uid = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("legacy", c.getString(0))
        }
    }

    private fun assertNewTablesEmpty(db: SupportSQLiteDatabase) {
        for (table in listOf("account", "github_profile", "star_lists", "star_list_items")) {
            assertEquals("rows in $table", 0, count(db, "SELECT COUNT(*) FROM $table"))
        }
    }

    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { c -> assertEquals(0, c.count) }
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { c -> c.moveToFirst(); c.getInt(0) }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
