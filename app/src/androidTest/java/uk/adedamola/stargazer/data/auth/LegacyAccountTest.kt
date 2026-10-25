package uk.adedamola.stargazer.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.local.database.AppDatabase
import java.io.IOException

/**
 * The update from the pasted-token version: a working old token identifies the account once and
 * is removed; a rejected one leaves the owner unknown and the first sign-in asks Keep or
 * Remove. Without an answer from GitHub the old token is kept and nothing is guessed.
 */
@RunWith(AndroidJUnit4::class)
class LegacyAccountTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var legacy: LegacyStoreFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
    private val key = fakeKey()
    private val oldToken = "old-pasted-token-fixture"

    /** `GET /user` with the old token answers [oldAnswer]; with the new key, [newUser]. */
    private var oldAnswer: Scripted = Scripted(200, userJson(FIXTURE_USER_ID, "fixture-login"))
    private var newUser = userJson(FIXTURE_USER_ID, "fixture-login")
    private val network = ScriptedNetwork { request ->
        when {
            request.url.encodedPath != "/user" -> Scripted(404)
            request.header("Authorization") == "Bearer $oldToken" -> oldAnswer
            else -> Scripted(200, newUser)
        }
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        legacy = LegacyStoreFixture()
        db = inMemoryDatabase()
        session = AccessSession(keys.tokenManager)
        // A v1.1 install: local data, no account record (that version had none), the old token.
        seedLocalData(db)
        db.accountDao().deleteAll()
        legacy.seedOldToken(oldToken)
    }

    @After
    fun tearDown() {
        try {
            network.requests.forEach { request ->
                assertFalse("no GitHub-shaped key on the wire", request.header("Authorization").orEmpty().contains("ghu_"))
            }
        } finally {
            db.close()
            keys.close()
            legacy.close()
        }
    }

    private fun api() = apiService(keys.tokenManager, network, session)
    private fun identifier() = LegacyAccountIdentifier(api(), db.accountDao(), legacy.store) { 7L }
    private fun newCompleter() = completer(db, keys.tokenManager, api(), legacy.store)

    @Test
    fun workingOldTokenRecordsTheAccountAndTheSameAccountKeepsItsData() = runBlocking<Unit> {
        val before = localCounts(db)

        assertEquals(LegacyIdentify.Identified, identifier().identify())

        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        assertNull("old token deleted", legacy.store.oldToken())
        assertFalse(legacy.store.ownerUnknown())
        assertNull("sign-in still needed", keys.tokenManager.currentKey())

        val outcome = newCompleter().complete(key).getOrThrow()

        assertEquals(SignInOutcome.Done, outcome)
        assertEquals(before + ("account" to 1), localCounts(db))
        assertEquals(key, keys.tokenManager.currentKey())
    }

    @Test
    fun rejectedOldTokenAsksAndKeepKeepsTheData() = runBlocking<Unit> {
        oldAnswer = Scripted(401)
        newUser = userJson(SECOND_USER_ID, "second-login")
        val before = localCounts(db)

        assertEquals(LegacyIdentify.OwnerUnknown, identifier().identify())

        assertNull(legacy.store.oldToken())
        assertTrue(legacy.store.ownerUnknown())
        assertFalse("a dead old token is not an ended session", session.accessEnded.value)

        val completer = newCompleter()
        val ask = completer.complete(key).getOrThrow() as SignInOutcome.KeepData
        assertEquals("second-login", ask.new.login)
        assertEquals(LocalDataCounts(repos = 3, tags = 1, presets = 1), ask.counts)
        assertNull("no key while the question is open", keys.tokenManager.currentKey())

        completer.keepData(ask.pending).getOrThrow()

        assertEquals(SECOND_USER_ID, db.accountDao().get()!!.userId)
        assertFalse(legacy.store.ownerUnknown())
        assertEquals(before + ("account" to 1), localCounts(db))
        assertEquals(key, keys.tokenManager.currentKey())
    }

    @Test
    fun rejectedOldTokenThenRemoveEmptiesTheData() = runBlocking<Unit> {
        oldAnswer = Scripted(401)
        newUser = userJson(SECOND_USER_ID, "second-login")
        identifier().identify()

        val completer = newCompleter()
        val ask = completer.complete(key).getOrThrow() as SignInOutcome.KeepData
        completer.removeData(ask.pending).getOrThrow()

        val counts = localCounts(db)
        WIPE_TABLES.forEach { table -> assertEquals("$table empty", 0, counts[table]) }
        assertEquals(SECOND_USER_ID, db.accountDao().get()!!.userId)
        assertFalse(legacy.store.ownerUnknown())
        assertEquals(key, keys.tokenManager.currentKey())
    }

    @Test
    fun offlineAtLaunchKeepsTheOldTokenAndSignInIdentifiesFirst() = runBlocking<Unit> {
        oldAnswer = Scripted(-1)

        assertEquals(LegacyIdentify.Unresolved, identifier().identify())
        assertEquals(oldToken, legacy.store.oldToken())
        assertNull(db.accountDao().get())

        oldAnswer = Scripted(200, userJson(FIXTURE_USER_ID, "fixture-login"))
        val outcome = newCompleter().complete(key).getOrThrow()

        assertEquals(SignInOutcome.Done, outcome)
        assertNull(legacy.store.oldToken())
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        val oldTokenRequest = network.requests.indexOfLast { it.header("Authorization") == "Bearer $oldToken" }
        val newKeyRequest = network.requests.indexOfFirst { it.header("Authorization") == "Bearer $key" }
        assertTrue("both were asked", oldTokenRequest >= 0 && newKeyRequest >= 0)
    }

    @Test
    fun oldTokenStillUnanswerableFailsTheSignInAndChangesNothing() = runBlocking<Unit> {
        oldAnswer = Scripted(503)
        newUser = userJson(SECOND_USER_ID, "second-login")
        val before = localCounts(db)

        val result = newCompleter().complete(key)

        assertTrue(result.exceptionOrNull() is IOException)
        assertNull(keys.tokenManager.currentKey())
        assertEquals(before, localCounts(db))
        assertEquals(oldToken, legacy.store.oldToken())
        assertFalse(legacy.store.ownerUnknown())
    }
}
