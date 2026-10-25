package uk.adedamola.stargazer.data.star

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.FakeConnectivity
import uk.adedamola.stargazer.data.auth.SECOND_USER_ID
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.calls
import uk.adedamola.stargazer.data.auth.eventually
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.seedRepos
import uk.adedamola.stargazer.data.auth.starEngine
import uk.adedamola.stargazer.data.auth.starPath
import uk.adedamola.stargazer.data.auth.starredPages
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.PendingUnstarEntity
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.repository.Attempt
import uk.adedamola.stargazer.data.repository.Outcome
import uk.adedamola.stargazer.data.repository.Prepared
import uk.adedamola.stargazer.data.repository.RepoRef
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.StarReconciler

/**
 * The reconcile against a scripted GitHub starred list: a repo GitHub no longer lists leaves with
 * its favorite, pin and tags while the others keep theirs; background sync removes nothing; a
 * failed page, a 401 or an account change removes nothing; a pending unstar or a star made during
 * the read keeps its repo; the first reconcile after a sign-in that would remove repos waits for
 * Continue, and a reconcile that removes nothing asks nothing.
 */
@RunWith(AndroidJUnit4::class)
class ReconcileTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var engine: StarEngine
    private var tagId = 0

    private val a = 900_001
    private val b = 900_002
    private val c = 900_003

    /** GitHub's starred list, page by page, and its answer to star requests. */
    private var listed: (okhttp3.Request) -> Scripted? = starredPages(emptyList())
    private val network = ScriptedNetwork { request ->
        listed(request) ?: if (request.url.encodedPath.startsWith("/user/starred/")) Scripted(204, "") else Scripted(404, "{}")
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "", SIGNED_IN_AT))
        tagId = db.tagDao().insertTag(Tag(name = "kept", color = "#FF6600")).toInt()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engine = starEngine(db, network, keys.tokenManager, scope, FakeConnectivity())
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        keys.close()
    }

    private fun reconciler() = StarReconciler(apiService(keys.tokenManager, network), db, engine, System::currentTimeMillis)

    /** The reconcile already finished once for this sign-in, so nothing asks first. */
    private suspend fun markReconciled(recordedAt: Long = SIGNED_IN_AT) =
        db.syncMetadataDao().updateMetadata(SyncMetadata(StarReconciler.MARKER, lastSyncTimestamp = recordedAt, isInitialSyncComplete = true))

    private suspend fun marker() = db.syncMetadataDao().getMetadata(StarReconciler.MARKER)?.lastSyncTimestamp

    private suspend fun row(id: Int): RepositoryEntity? = db.repositoryDao().getRepositoryById(id)

    private suspend fun tags(id: Int) = db.repositoryTagDao().getTagsForRepository(id).first().map { it.id }

    private suspend fun assertIntact(id: Int) {
        val row = row(id)
        assertNotNull("repo $id is still stored", row)
        assertTrue("repo $id keeps its favorite", row!!.isFavorite)
        assertTrue("repo $id keeps its pin", row.isPinned)
        assertEquals("repo $id keeps its tag", listOf(tagId), tags(id))
    }

    private suspend fun assertGone(id: Int) {
        assertNull("repo $id is removed", row(id))
        assertEquals("repo $id has no tag assignment left", emptyList<Int>(), tags(id))
    }

    private suspend fun count() = db.repositoryDao().getRepositoryCount()

    private suspend fun tagAssignments(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM repository_tags").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun aRepoGitHubNoLongerListsLeavesWithItsDataAndTheOthersKeepTheirs() = runBlocking<Unit> {
        seedRepos(db, listOf(a, b, c), favorite = true, pinned = true, tagId = tagId)
        markReconciled()
        listed = starredPages(listOf(a, c))

        assertEquals(Outcome.Done(1), reconciler().reconcile())

        assertGone(b)
        assertIntact(a)
        assertIntact(c)
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun backgroundSyncRemovesNoRow() = runBlocking<Unit> {
        seedRepos(db, listOf(a, b, c), favorite = true, pinned = true, tagId = tagId)
        db.syncMetadataDao().updateMetadata(SyncMetadata(dataType = "starred_repos", isInitialSyncComplete = true))
        listed = starredPages(listOf(a, c))

        val result = StarredReposRemoteMediator(apiService(keys.tokenManager, network), db)
            .load(LoadType.REFRESH, PagingState(emptyList(), null, PagingConfig(pageSize = 20), 0))

        assertTrue("the sync itself succeeded", result is RemoteMediator.MediatorResult.Success)
        assertTrue("it read the list", network.calls("GET", "/user/starred").isNotEmpty())
        assertEquals(3, count())
        assertIntact(b)
    }

    @Test
    fun aFailedPageRemovesNothing() = runBlocking<Unit> {
        val local = (1..450).map { 900_000 + it }
        val remote = (51..500).map { 900_000 + it } // 5 pages; the first 50 stored repos are gone
        seedRepos(db, local, favorite = true, pinned = true, tagId = tagId)
        markReconciled(recordedAt = 7L) // an earlier sign-in: a finished reconcile would rewrite it

        for (failure in listOf(-1, 500)) {
            listed = starredPages(remote, failPage = 3, failWith = failure)
            val outcome = reconciler().reconcile()
            assertTrue("answer $failure fails the reconcile: $outcome", outcome is Outcome.Failed)
            assertTrue("pages after the failed one are not read", network.requests.none { it.url.queryParameter("page") == "4" })
            assertEquals(450, count())
            assertEquals(450, tagAssignments())
            assertEquals(7L, marker())
            network.requests.clear()
        }

        // The same list read in full removes the 50.
        listed = starredPages(remote)
        markReconciled()
        assertEquals(Outcome.Done(50), reconciler().reconcile())
        assertEquals(400, count())
    }

    @Test
    fun aRejectedKeyRemovesNothing() = runBlocking<Unit> {
        seedRepos(db, listOf(a, b, c), favorite = true, pinned = true, tagId = tagId)
        markReconciled()
        listed = { request -> if (request.url.encodedPath == "/user/starred") Scripted(401, """{"message":"Bad credentials"}""") else null }

        assertTrue(reconciler().reconcile() is Outcome.Failed)
        assertEquals(3, count())
    }

    @Test
    fun aPendingUnstarIsLeftToItsUndoWindow() = runBlocking<Unit> {
        seedRepos(db, listOf(a, b, c), favorite = true, pinned = true, tagId = tagId)
        markReconciled()
        db.pendingUnstarDao().insert(PendingUnstarEntity(b, "fixture", "repo-2", PendingUnstarEntity.CONFIRMED, Long.MAX_VALUE, 1L))
        listed = starredPages(listOf(a, c))

        assertEquals(Outcome.Done(0), reconciler().reconcile())
        assertIntact(b)
    }

    @Test
    fun aRepoStarredDuringTheReadStays() = runBlocking<Unit> {
        seedRepos(db, listOf(a, b, c), favorite = true, pinned = true, tagId = tagId)
        markReconciled()
        listed = starredPages(listOf(a, c))
        val reconciler = reconciler()

        val prepared = reconciler.prepare() as Prepared.Ready
        assertEquals(setOf(b), prepared.candidates)
        // Starred again here after GitHub's list was read.
        assertEquals(Attempt.Sent, engine.star(RepoRef(b, "fixture", "repo-2"), null))
        eventually("the star request went out") { network.calls("PUT", starPath("fixture", "repo-2")).size == 1 }

        assertEquals(Outcome.Done(0), reconciler.apply(prepared))
        assertIntact(b)
    }

    @Test
    fun anAccountChangeDuringTheReadRemovesNothing() = runBlocking<Unit> {
        seedRepos(db, listOf(a, b, c), favorite = true, pinned = true, tagId = tagId)
        markReconciled()
        listed = starredPages(listOf(a, c))
        val reconciler = reconciler()

        val prepared = reconciler.prepare() as Prepared.Ready
        db.accountDao().replace(AccountRecord(SECOND_USER_ID, "someone-else", "", SIGNED_IN_AT + 1))

        assertEquals(Outcome.AccountChanged, reconciler.apply(prepared))
        assertIntact(b)
        assertEquals(SIGNED_IN_AT, marker())
    }

    @Test
    fun theFirstReconcileAfterASignInWaitsAndALaterOneDoesNot() = runBlocking<Unit> {
        val local = (1..30).map { 900_000 + it }
        val kept = local.take(7)
        seedRepos(db, local, favorite = true, pinned = true, tagId = tagId)
        listed = starredPages(kept)
        val reconciler = reconciler()

        // No reconcile finished for this sign-in: the count, and nothing removed yet.
        val first = reconciler.reconcile()
        assertTrue("$first", first is Outcome.NoticeNeeded)
        first as Outcome.NoticeNeeded
        assertEquals(23, first.count)
        assertEquals(30, count())
        assertNull(marker())

        // Continue.
        assertEquals(Outcome.Done(23), reconciler.apply(first.prepared))
        assertEquals(7, count())
        kept.forEach { assertIntact(it) }
        assertEquals(SIGNED_IN_AT, marker())

        // Two more unstarred elsewhere: the same sign-in removes them without asking.
        listed = starredPages(kept.take(5))
        assertEquals(Outcome.Done(2), reconciler.reconcile())
        assertEquals(5, count())

        // A new sign-in with nothing missing: no notice, and the sign-in is recorded.
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "", SIGNED_IN_AT + 100))
        assertEquals(Outcome.Done(0), reconciler.reconcile())
        assertEquals(SIGNED_IN_AT + 100, marker())

        // That sign-in already had its first reconcile, so a later removal does not ask.
        listed = starredPages(kept.take(4))
        assertEquals(Outcome.Done(1), reconciler.reconcile())
    }

    @Test
    fun aSecondCallWhileOneRunsJoinsIt() = runBlocking<Unit> {
        seedRepos(db, listOf(a, b, c), favorite = true, pinned = true, tagId = tagId)
        markReconciled()
        listed = starredPages(listOf(a, c))
        val reconciler = reconciler()

        val outcomes = listOf(
            async(Dispatchers.IO) { reconciler.reconcile() },
            async(Dispatchers.IO) { reconciler.reconcile() },
        ).map { it.await() }

        assertTrue("$outcomes", Outcome.Done(1) in outcomes)
        assertGone(b)
    }

    private companion object {
        const val SIGNED_IN_AT = 1_800_000_000_000L
    }
}
