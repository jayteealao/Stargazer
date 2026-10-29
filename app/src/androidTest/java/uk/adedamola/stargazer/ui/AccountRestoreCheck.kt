package uk.adedamola.stargazer.ui

import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.LegacyAccountStore
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.SignInOutcome
import uk.adedamola.stargazer.data.auth.TokenManager
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.completer
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.localCounts
import uk.adedamola.stargazer.data.auth.seedLocalData
import uk.adedamola.stargazer.data.auth.userJson
import uk.adedamola.stargazer.data.local.database.AppDatabase
import javax.inject.Inject

/**
 * The backup-and-restore check on an emulator, in two halves around `bmgr backupnow`,
 * an uninstall and a reinstall. Each half runs only with its argument; a normal run skips both.
 *
 * - `-e restoreSeed true`: records the fixture account, seeds favorites, pins, a tag with an
 *   assignment and a preset in the app's real database, and stores a plain fixture key.
 * - `-e restoreCheck true`: after the restore, the data is back, the key is not, and a sign-in
 *   by the same account (answered by a scripted `GET /user`) keeps the data; then signs out.
 */
@HiltAndroidTest
class AccountRestoreCheck {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var legacyStore: LegacyAccountStore
    @Inject lateinit var session: AccessSession

    private val args = InstrumentationRegistry.getArguments()
    private val seeding = args.getString("restoreSeed") == "true"
    private val checking = args.getString("restoreCheck") == "true"

    @Before
    fun setUp() {
        // Skip before injecting: each injection builds the app's singletons again, and a second
        // live store over the key file in one process is refused.
        assumeTrue(seeding || checking)
        hiltRule.inject()
    }

    @Test
    fun seed() = runBlocking<Unit> {
        assumeTrue(seeding)
        seedLocalData(db)
        tokenManager.saveToken("restore-fixture-not-real")
    }

    @Test
    fun restored() = runBlocking<Unit> {
        assumeTrue(checking)
        assertNull("the key is not restored", tokenManager.currentKey())
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        assertTrue("favorite restored", db.repositoryDao().getRepositoryById(900_001)!!.isFavorite)
        assertTrue("pin restored", db.repositoryDao().getRepositoryById(900_002)!!.isPinned)
        assertTrue("tag restored", db.tagDao().count() >= 1)
        assertTrue("preset restored", db.searchPresetDao().count() >= 1)
        val before = localCounts(db)
        assertTrue("tag assignment restored", before.getValue("repository_tags") >= 1)

        val network = ScriptedNetwork { request ->
            if (request.url.encodedPath == "/user") Scripted(200, userJson(FIXTURE_USER_ID, "fixture-login")) else Scripted(404)
        }
        val api = apiService(tokenManager, network, session)
        val outcome = completer(db, tokenManager, api, legacyStore).complete(fakeKey()).getOrThrow()

        assertEquals(SignInOutcome.Done, outcome)
        assertEquals("the same account keeps its data", before, localCounts(db))
        session.signOut()
    }
}
