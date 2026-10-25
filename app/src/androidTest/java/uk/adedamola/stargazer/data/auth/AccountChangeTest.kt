package uk.adedamola.stargazer.data.auth

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.paging.SyncProgress

/**
 * Two fake GitHub accounts: the same numeric user id keeps every table (AC-9, also after a
 * rename); a different id asks first, Continue empties the wipe set in one transaction (AC-10),
 * Cancel keeps everything (AC-11).
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class AccountChangeTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var legacy: LegacyStoreFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
    private val key = fakeKey()

    /** What `GET /user` answers; every other request is rate limited. */
    private var user = userJson(FIXTURE_USER_ID, "fixture-login")
    private val network = ScriptedNetwork { request ->
        if (request.url.encodedPath == "/user") Scripted(200, user)
        else Scripted(429, headers = mapOf("retry-after" to "60"))
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        legacy = LegacyStoreFixture()
        db = inMemoryDatabase()
        session = AccessSession(keys.tokenManager)
        seedLocalData(db)
    }

    @After
    fun tearDown() {
        db.close()
        keys.close()
        legacy.close()
    }

    private fun api() = apiService(keys.tokenManager, network, session)

    private fun newCompleter(
        mediator: StarredReposRemoteMediator = StarredReposRemoteMediator(api(), db),
        beforeCommit: suspend () -> Unit = {}
    ) = completer(db, keys.tokenManager, api(), legacy.store, mediator, beforeCommit)

    @Test
    fun sameAccountAfterSignOutKeepsEverythingWithNoQuestion() = runBlocking<Unit> {
        keys.tokenManager.saveToken(fakeKey())
        session.signOut()
        val before = localCounts(db)

        val outcome = newCompleter().complete(key).getOrThrow()

        assertEquals(SignInOutcome.Done, outcome)
        assertEquals(before, localCounts(db))
        assertTrue(db.repositoryDao().getRepositoryById(900_001)!!.isFavorite)
        assertTrue(db.repositoryDao().getRepositoryById(900_002)!!.isPinned)
        assertEquals(1, db.tagDao().count())
        assertEquals(1, db.searchPresetDao().count())
        assertEquals(key, keys.tokenManager.currentKey())
    }

    @Test
    fun renamedLoginWithTheSameIdIsTheSameAccount() = runBlocking<Unit> {
        user = userJson(FIXTURE_USER_ID, "renamed-login", "Fixture Person")
        val before = localCounts(db)

        val outcome = newCompleter().complete(key).getOrThrow()

        assertEquals(SignInOutcome.Done, outcome)
        assertEquals(before, localCounts(db))
        assertEquals("renamed-login", db.accountDao().get()!!.login)
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
    }

    @Test
    fun differentAccountAsksFirstAndStoresNoKey() = runBlocking<Unit> {
        user = userJson(SECOND_USER_ID, "second-login", "Second Person")
        val before = localCounts(db)

        val outcome = newCompleter().complete(key).getOrThrow() as SignInOutcome.AccountChange

        assertEquals(AccountSummary(FIXTURE_USER_ID, "fixture-login", "Fixture Person", "https://avatars.example/u.png"), outcome.old)
        assertEquals(AccountSummary(SECOND_USER_ID, "second-login", "Second Person", "https://avatars.example/$SECOND_USER_ID.png"), outcome.new)
        assertNull("no key while the question is open", keys.tokenManager.currentKey())
        assertEquals(before, localCounts(db))
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        assertTrue("key hidden", !outcome.pending.toString().contains(key))
    }

    @Test
    fun continueEmptiesEveryWipeTableAndResetsTheSync() = runBlocking<Unit> {
        val mediator = StarredReposRemoteMediator(api(), db)
        mediator.load(LoadType.REFRESH, PagingState(emptyList(), null, PagingConfig(pageSize = 30), leadingPlaceholderCount = 0))
        assertNotEquals("the old account's sync left progress behind", SyncProgress(), mediator.syncProgress.value)
        user = userJson(SECOND_USER_ID, "second-login")
        val completer = newCompleter(mediator)
        val change = completer.complete(key).getOrThrow() as SignInOutcome.AccountChange

        completer.confirmAccountChange(change.pending).getOrThrow()

        val counts = localCounts(db)
        WIPE_TABLES.forEach { table -> assertEquals("$table empty", 0, counts[table]) }
        assertEquals(SECOND_USER_ID, db.accountDao().get()!!.userId)
        assertEquals(1, counts["account"])
        assertEquals(SyncProgress(), mediator.syncProgress.value)
        assertEquals(key, keys.tokenManager.currentKey())
    }

    @Test
    fun cancelKeepsEverythingAndStoresNoKey() = runBlocking<Unit> {
        user = userJson(SECOND_USER_ID, "second-login")
        val before = localCounts(db)

        val change = newCompleter().complete(key).getOrThrow()
        assertTrue(change is SignInOutcome.AccountChange)
        // Cancel: the pending sign-in is dropped; nothing was stored.

        assertNull(keys.tokenManager.currentKey())
        assertEquals(before, localCounts(db))
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        assertEquals("fixture-login", db.accountDao().get()!!.login)
    }

    @Test
    fun aFailureInsideTheWipeChangesNothing() = runBlocking<Unit> {
        user = userJson(SECOND_USER_ID, "second-login")
        val before = localCounts(db)
        val completer = newCompleter(beforeCommit = { throw IllegalStateException("injected fault") })
        val change = completer.complete(key).getOrThrow() as SignInOutcome.AccountChange

        val result = completer.confirmAccountChange(change.pending)

        assertTrue(result.isFailure)
        assertEquals("every table rolled back", before, localCounts(db))
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        assertNull(keys.tokenManager.currentKey())
    }
}
