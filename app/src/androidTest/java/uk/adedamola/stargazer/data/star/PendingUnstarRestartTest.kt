package uk.adedamola.stargazer.data.star

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.FakeConnectivity
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.WindowGate
import uk.adedamola.stargazer.data.auth.calls
import uk.adedamola.stargazer.data.auth.eventually
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.fixtureRepo
import uk.adedamola.stargazer.data.auth.starEngine
import uk.adedamola.stargazer.data.auth.starPath
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.PendingUnstarEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.local.di.MIGRATION_4_5
import uk.adedamola.stargazer.data.local.di.MIGRATION_5_6
import uk.adedamola.stargazer.data.local.di.MIGRATION_6_7
import uk.adedamola.stargazer.data.repository.RepoRef
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The app process dies during an unstar's Undo window. On the next start the pending record is
 * settled against GitHub: still starred brings the repo back with everything, not starred
 * finishes the unstar, no answer keeps it for the next try.
 */
@RunWith(AndroidJUnit4::class)
class PendingUnstarRestartTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "restart-" + UUID.randomUUID()
    private lateinit var keys: TokenManagerFixture
    private val repo = RepoRef(900_001, "fixture", "repo-1")
    private val path = starPath("fixture", "repo-1")
    private val releaseDelete = CountDownLatch(1)
    private var onDelete: () -> Scripted = { Scripted(204, "") }
    private var onGet: () -> Scripted = { Scripted(404, "") }
    private val network = ScriptedNetwork { r -> if (r.method == "DELETE") onDelete() else onGet() }
    private var tagId = 0

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .build()

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        val db = open()
        @Suppress("DEPRECATION") // sdlc-debt: test seed uses the deprecated insert-or-replace DAO call (ceiling: test-only; upgrade path: an upsert helper if the DAO drops it)
        db.repositoryDao().insertRepositories(listOf(fixtureRepo(1), fixtureRepo(2)))
        db.repositoryDao().updateFavorite(repo.id, true)
        db.repositoryDao().updatePinned(repo.id, true)
        tagId = db.tagDao().insertTag(Tag(name = "kept", color = "#FF6600")).toInt()
        db.repositoryTagDao().addTagToRepository(RepositoryTag(repo.id, tagId))
        db.close()
    }

    @After
    fun tearDown() {
        releaseDelete.countDown()
        context.deleteDatabase(dbName)
        keys.close()
    }

    /** Engine A unstars, then its process dies: its scope is cancelled and its database closed. */
    private fun unstarThenDie(waitFor: suspend (AppDatabase) -> Boolean) = runBlocking<Unit> {
        val db = open()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = starEngine(db, network, keys.tokenManager, scope, FakeConnectivity(), sleep = WindowGate().sleep)
        engine.unstar(repo)
        eventually("state before death") { waitFor(db) }
        scope.cancel()
        db.close()
    }

    /** Engine B, the next start, over the same file. */
    private fun <T> restart(block: suspend (AppDatabase, uk.adedamola.stargazer.data.repository.StarEngine) -> T): T = runBlocking {
        val db = open()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(db, starEngine(db, network, keys.tokenManager, scope, FakeConnectivity()))
        } finally {
            scope.cancel()
            db.close()
        }
    }

    private suspend fun assertIntact(db: AppDatabase) {
        val row = db.repositoryDao().getRepositoryById(repo.id)
        assertNotNull(row)
        assertTrue(row!!.isFavorite)
        assertTrue(row.isPinned)
        assertEquals(listOf(tagId), db.repositoryTagDao().getTagsForRepository(repo.id).first().map { it.id })
    }

    @Test
    fun deleteNeverLandedRestoresEverything() {
        // The DELETE never gets its answer before the process dies.
        onDelete = { releaseDelete.await(20, TimeUnit.SECONDS); Scripted(204, "") }
        unstarThenDie { db -> network.calls("DELETE", path).size == 1 && db.pendingUnstarDao().get(repo.id) != null }
        onGet = { Scripted(204, "") } // GitHub: still starred

        restart { db, engine ->
            assertEquals(PendingUnstarEntity.SENDING, db.pendingUnstarDao().get(repo.id)!!.state)
            engine.resumePending()
            assertNull(db.pendingUnstarDao().get(repo.id))
            assertIntact(db)
            val check = network.calls("GET", path).single()
            assertEquals("GET", check.method)
        }
    }

    @Test
    fun confirmedUnstarFinishesOnRestart() {
        unstarThenDie { db -> db.pendingUnstarDao().get(repo.id)?.state == PendingUnstarEntity.CONFIRMED }
        restart { db, _ -> assertIntact(db) } // nothing is lost before GitHub is asked
        onGet = { Scripted(404, "") } // GitHub: not starred

        restart { db, engine ->
            engine.resumePending()
            assertNull(db.repositoryDao().getRepositoryById(repo.id))
            assertEquals(0, db.repositoryTagDao().getTagsForRepository(repo.id).first().size)
            assertNull(db.pendingUnstarDao().get(repo.id))
            assertNotNull("other repos untouched", db.repositoryDao().getRepositoryById(900_002))
        }
    }

    @Test
    fun noAnswerKeepsTheRecordForTheNextTry() {
        unstarThenDie { db -> db.pendingUnstarDao().get(repo.id)?.state == PendingUnstarEntity.CONFIRMED }
        onGet = { Scripted(-1) }

        restart { db, engine ->
            engine.resumePending()
            assertNotNull("kept", db.pendingUnstarDao().get(repo.id))
            assertTrue("still hidden", repo.id in engine.hiddenIds.first())
            assertIntact(db)
        }
        onGet = { Scripted(404, "") }
        restart { db, engine ->
            engine.resumePending()
            assertNull(db.pendingUnstarDao().get(repo.id))
            assertNull(db.repositoryDao().getRepositoryById(repo.id))
        }
        assertEquals(2, network.calls("GET", path).size)
    }
}
