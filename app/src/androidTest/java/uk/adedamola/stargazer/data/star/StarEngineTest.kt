package uk.adedamola.stargazer.data.star

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.FakeConnectivity
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.WindowGate
import uk.adedamola.stargazer.data.auth.calls
import uk.adedamola.stargazer.data.auth.eventually
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.fixtureRepo
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.rateLimitReset
import uk.adedamola.stargazer.data.auth.rateLimited
import uk.adedamola.stargazer.data.auth.repoJson
import uk.adedamola.stargazer.data.auth.starEngine
import uk.adedamola.stargazer.data.auth.starPath
import uk.adedamola.stargazer.data.di.NetworkModule
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.PendingUnstarEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.Attempt
import uk.adedamola.stargazer.data.repository.RepoRef
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.StarMessage
import uk.adedamola.stargazer.data.repository.UndoOffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Star and unstar against a scripted GitHub: the DELETE goes out at once, Undo re-stars, local
 * data goes only after GitHub confirms and the window ends, every failure restores the repo,
 * offline and rate-limited taps send nothing, and one request runs per repo.
 */
@RunWith(AndroidJUnit4::class)
class StarEngineTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private val gate = WindowGate()
    private val connectivity = FakeConnectivity()
    private var now = 1_800_000_000_000L
    private var tagId = 0

    /** GitHub's answer per method, for every star endpoint. */
    private var onDelete: () -> Scripted = { Scripted(204, "") }
    private var onPut: () -> Scripted = { Scripted(204, "") }
    private var onGet: () -> Scripted = { Scripted(404, "") }
    private val network = ScriptedNetwork { request ->
        when (request.method) {
            "DELETE" -> onDelete()
            "PUT" -> onPut()
            else -> onGet()
        }
    }

    private val repo1 = RepoRef(900_001, "fixture", "repo-1")
    private val path1 = starPath("fixture", "repo-1")
    private lateinit var engine: StarEngine

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
        @Suppress("DEPRECATION") // sdlc-debt: test seed uses the deprecated insert-or-replace DAO call (ceiling: test-only; upgrade path: an upsert helper if the DAO drops it)
        db.repositoryDao().insertRepositories((1..3).map(::fixtureRepo))
        db.repositoryDao().updateFavorite(repo1.id, true)
        db.repositoryDao().updatePinned(repo1.id, true)
        tagId = db.tagDao().insertTag(Tag(name = "kept", color = "#FF6600")).toInt()
        db.repositoryTagDao().addTagToRepository(RepositoryTag(repo1.id, tagId))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engine = build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        keys.close()
    }

    private fun build(session: AccessSession? = null) =
        starEngine(db, network, keys.tokenManager, scope, connectivity, { now }, gate.sleep, session)

    private suspend fun pending(id: Int = repo1.id) = db.pendingUnstarDao().get(id)

    private suspend fun assertRepo1Intact() {
        val row = db.repositoryDao().getRepositoryById(repo1.id)
        assertNotNull("repo row kept", row)
        assertTrue("favorite kept", row!!.isFavorite)
        assertTrue("pin kept", row.isPinned)
        assertEquals("tag kept", listOf(tagId), db.repositoryTagDao().getTagsForRepository(repo1.id).first().map { it.id })
    }

    private suspend fun waitConfirmed(id: Int = repo1.id) =
        eventually("unstar confirmed and window open") {
            pending(id)?.state == PendingUnstarEntity.CONFIRMED && gate.waits > 0
        }

    @Test
    fun unstarSendsDeleteAtOnceAndHides() = runBlocking<Unit> {
        assertEquals(Attempt.Sent, engine.unstar(repo1))

        eventually("one DELETE") { network.calls("DELETE", path1).size == 1 }
        val delete = network.calls("DELETE", path1).single()
        assertTrue("sent with the stored key", delete.header("Authorization")!!.startsWith("Bearer "))
        assertNotNull("pending record", pending())
        assertTrue("hidden from Home", repo1.id in engine.hiddenIds.first())
        assertEquals(UndoOffer(repo1.id, "fixture/repo-1"), engine.undoOffer.value)
        waitConfirmed()
        assertRepo1Intact()
    }

    @Test
    fun undoSendsPutAndRestoresEverything() = runBlocking<Unit> {
        engine.unstar(repo1)
        waitConfirmed()

        assertEquals(Attempt.Sent, engine.undo(repo1.id))

        eventually("one PUT") { network.calls("PUT", path1).size == 1 }
        eventually("record gone") { pending() == null }
        assertEquals("empty PUT body", 0L, network.calls("PUT", path1).single().body!!.contentLength())
        assertRepo1Intact()
        assertFalse(repo1.id in engine.hiddenIds.first())
        assertNull(engine.undoOffer.value)
        gate.release()
        delay(200)
        assertRepo1Intact()
    }

    @Test
    fun confirmedThenWindowEndsDeletesLocalData() = runBlocking<Unit> {
        engine.unstar(repo1)
        waitConfirmed()
        assertRepo1Intact()

        gate.release()

        eventually("repo deleted") { db.repositoryDao().getRepositoryById(repo1.id) == null }
        assertEquals(0, db.repositoryTagDao().getTagsForRepository(repo1.id).first().size)
        assertNull(pending())
        assertEquals("the tag itself stays", 1, db.tagDao().getAllTags().first().size)
        assertNull(engine.undoOffer.value)
        assertEquals(2, db.repositoryDao().getRepositoryCount())
    }

    /** With the real clock and the real wait, Undo is offered for about five seconds. */
    @Test
    fun undoIsOfferedForAboutFiveSecondsOnTheRealClock() = runBlocking<Unit> {
        val realTime = starEngine(db, network, keys.tokenManager, scope, connectivity, System::currentTimeMillis, { delay(it) })
        val start = System.currentTimeMillis()
        assertEquals(Attempt.Sent, realTime.unstar(repo1))

        delay(3_000)
        assertEquals("still offered after 3 s", UndoOffer(repo1.id, "fixture/repo-1"), realTime.undoOffer.value)
        assertNotNull("data kept inside the window", db.repositoryDao().getRepositoryById(repo1.id))

        eventually("offer ends", timeoutMillis = 8_000) { realTime.undoOffer.value == null }
        val shown = System.currentTimeMillis() - start
        assertTrue("Undo offered for $shown ms", shown in 4_500..6_500)
        eventually("data deleted after the window") { db.repositoryDao().getRepositoryById(repo1.id) == null }
    }

    @Test
    fun rejectedOrFailedRestores() = runBlocking<Unit> {
        for ((answer, expected) in listOf(
            Scripted(500) to StarMessage.UnstarFailed(repo1),
            Scripted(404) to StarMessage.UnstarFailed(repo1),
            Scripted(-1) to StarMessage.ConnectionNeeded(repo1.id),
        )) {
            onDelete = { answer }
            val before = network.calls("DELETE", path1).size
            assertEquals(Attempt.Sent, engine.unstar(repo1))
            eventually("answer ${answer.code} handled") {
                network.calls("DELETE", path1).size == before + 1 && pending() == null &&
                    engine.lastFailure.value[repo1.id] == expected
            }
            assertRepo1Intact()
            assertNull(engine.undoOffer.value)
            assertFalse(repo1.id in engine.hiddenIds.first())
        }
        assertEquals("no window ever started", 0, gate.waits)
    }

    @Test
    fun undoDuringInFlightDeleteQueues() = runBlocking<Unit> {
        val answerDelete = CountDownLatch(1)
        onDelete = { answerDelete.await(10, TimeUnit.SECONDS); Scripted(204, "") }
        engine.unstar(repo1)
        eventually("DELETE in flight") { network.calls("DELETE", path1).size == 1 }

        engine.undo(repo1.id)
        delay(300)
        assertEquals("PUT waits for the DELETE answer", 0, network.calls("PUT", path1).size)

        answerDelete.countDown()
        eventually("PUT after the DELETE") { network.calls("PUT", path1).size == 1 && pending() == null }
        assertEquals(1, network.calls("DELETE", path1).size)
        val order = network.requests.filter { it.url.encodedPath == path1 }.map { it.method }
        assertEquals(listOf("DELETE", "PUT"), order)
        assertRepo1Intact()
    }

    @Test
    fun restarFailsDeletesData() = runBlocking<Unit> {
        engine.unstar(repo1)
        waitConfirmed()
        onPut = { Scripted(500) }

        engine.undo(repo1.id)

        eventually("repo deleted after the failed re-star") { db.repositoryDao().getRepositoryById(repo1.id) == null }
        assertNull(pending())
        assertEquals(StarMessage.RestarFailed(repo1), engine.lastFailure.value[repo1.id])
    }

    @Test
    fun offlineChangesNothing() = runBlocking<Unit> {
        connectivity.set(false)
        val before = counts()

        assertEquals(Attempt.Offline, engine.unstar(repo1))
        assertEquals(Attempt.Offline, engine.star(RepoRef(42, "someone", "else"), null))
        assertEquals(Attempt.Offline, engine.undo(repo1.id))
        delay(200)

        assertEquals("no request", 0, network.requests.size)
        assertEquals("no table changed", before, counts())
        assertEquals(StarMessage.ConnectionNeeded(repo1.id), engine.lastFailure.value[repo1.id])
        assertEquals(StarMessage.ConnectionNeeded(repo1.id), engine.controlState(repo1.id).first().status)
        assertFalse(engine.controlState(repo1.id).first().enabled)
    }

    @Test
    fun repeatedTapsSendOneRequest() = runBlocking<Unit> {
        val answer = CountDownLatch(1)
        onDelete = { answer.await(10, TimeUnit.SECONDS); Scripted(204, "") }
        onPut = { answer.await(10, TimeUnit.SECONDS); Scripted(204, "") }

        assertEquals(Attempt.Sent, engine.unstar(repo1))
        eventually("DELETE in flight") { network.calls("DELETE", path1).size == 1 }
        repeat(3) { assertEquals(Attempt.Busy, engine.unstar(repo1)) }

        val other = RepoRef(77, "someone", "else")
        assertEquals(Attempt.Sent, engine.star(other, null))
        eventually("PUT in flight") { network.calls("PUT", starPath("someone", "else")).size == 1 }
        repeat(2) { assertEquals(Attempt.Busy, engine.star(other, null)) }

        delay(300)
        assertEquals(1, network.calls("DELETE", path1).size)
        assertEquals(1, network.calls("PUT", starPath("someone", "else")).size)
        assertTrue(engine.controlState(repo1.id).first().inFlight)
        answer.countDown()
        eventually("both answered") { engine.inFlight.value.isEmpty() }
        assertEquals(1, network.calls("DELETE", path1).size)
    }

    @Test
    fun rateLimitBlocksUntilReset() = runBlocking<Unit> {
        val t = now
        onDelete = { rateLimited(403, retryAfterSeconds = 60) }
        engine.unstar(repo1)
        eventually("limited") { engine.lastFailure.value[repo1.id] == StarMessage.RateLimited(repo1.id, t + 60_000) }
        assertNull("repo restored", pending())
        assertRepo1Intact()

        now = t + 30_000
        assertEquals(Attempt.RateLimited(t + 60_000), engine.unstar(repo1))
        assertFalse(engine.controlState(repo1.id).first().enabled)
        assertEquals(1, network.calls("DELETE", path1).size)

        now = t + 61_000
        onDelete = { rateLimitReset(429, epochSeconds = now / 1000 + 120) }
        assertEquals(Attempt.Sent, engine.unstar(repo1))
        val resetAt = (now / 1000 + 120) * 1000
        eventually("limited by the reset header") {
            engine.lastFailure.value[repo1.id] == StarMessage.RateLimited(repo1.id, resetAt)
        }
        assertEquals(2, network.calls("DELETE", path1).size)

        now = resetAt - 1
        assertEquals(Attempt.RateLimited(resetAt), engine.star(RepoRef(5, "a", "b"), null))
        assertEquals(2, network.requests.size)

        now = resetAt + 1
        onDelete = { Scripted(204, "") }
        assertEquals(Attempt.Sent, engine.unstar(repo1))
        eventually("third DELETE") { network.calls("DELETE", path1).size == 3 }
    }

    @Test
    fun starFromSearchEntersStarredList() = runBlocking<Unit> {
        val json = NetworkModule.provideJson()
        val result = json.decodeFromString(GitHubRepository.serializer(), repoJson(4242, "JetBrains", "kotlin").toString())
        val ref = RepoRef(4242, "JetBrains", "kotlin")

        assertEquals(Attempt.Sent, engine.star(ref, result))

        eventually("row stored") { db.repositoryDao().getRepositoryById(4242) != null }
        assertEquals(1, network.calls("PUT", starPath("JetBrains", "kotlin")).size)
        assertEquals(now, db.repositoryDao().getRepositoryById(4242)!!.starredAt)
        assertTrue(engine.controlState(4242).first().starred)

        // Starring again keeps what the person set on this phone.
        db.repositoryDao().updateFavorite(4242, true)
        engine.star(ref, result)
        eventually("second PUT") { network.calls("PUT", starPath("JetBrains", "kotlin")).size == 2 && engine.inFlight.value.isEmpty() }
        assertTrue(db.repositoryDao().getRepositoryById(4242)!!.isFavorite)

        // A failed star stores nothing.
        onPut = { Scripted(500) }
        val other = RepoRef(4343, "square", "retrofit")
        engine.star(other, json.decodeFromString(GitHubRepository.serializer(), repoJson(4343, "square", "retrofit").toString()))
        eventually("star failed") { engine.lastFailure.value[4343] == StarMessage.StarFailed(other) }
        assertNull(db.repositoryDao().getRepositoryById(4343))
    }

    @Test
    fun unauthorizedRestoresAndEndsAccess() = runBlocking<Unit> {
        val session = AccessSession(keys.tokenManager)
        engine = build(session)
        onDelete = { Scripted(401, """{"message":"Bad credentials"}""") }

        engine.unstar(repo1)

        eventually("key deleted") { keys.tokenManager.token.first() == null }
        eventually("repo restored") { pending() == null }
        assertTrue(session.accessEnded.value)
        assertRepo1Intact()
        assertEquals(1, network.calls("DELETE", path1).size)
    }

    private fun counts(): Map<String, Int> =
        listOf("repositories", "repository_tags", "tags", "pending_unstars").associateWith { table ->
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) }
        }
}
