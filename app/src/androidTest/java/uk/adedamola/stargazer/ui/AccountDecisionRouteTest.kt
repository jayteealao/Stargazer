package uk.adedamola.stargazer.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.DeviceFlowSignIn
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.FakeDeviceFlowApi
import uk.adedamola.stargazer.data.auth.LegacyStoreFixture
import uk.adedamola.stargazer.data.auth.SECOND_USER_ID
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.WIPE_TABLES
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.completer
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.localCounts
import uk.adedamola.stargazer.data.auth.seedLocalData
import uk.adedamola.stargazer.data.auth.token
import uk.adedamola.stargazer.data.auth.userJson
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.ui.screens.AccountDecision
import uk.adedamola.stargazer.ui.screens.SignInPhase
import uk.adedamola.stargazer.ui.screens.SignInViewModel

/**
 * An approved sign-in that needs an answer: Cancel returns to the sign-in screen with the data
 * kept (AC-11); Continue, Keep and Remove open Home (AC-10, AC-14).
 */
@RunWith(AndroidJUnit4::class)
class AccountDecisionRouteTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var legacy: LegacyStoreFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
    private val key = fakeKey()
    private val network = ScriptedNetwork { request ->
        if (request.url.encodedPath == "/user") Scripted(200, userJson(SECOND_USER_ID, "second-login", "Second Person"))
        else Scripted(404)
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

    /** Signs in through the view model until GitHub's approval asks the account question. */
    private suspend fun approved(): Pair<SignInViewModel, NavigationViewModel> {
        val api = FakeDeviceFlowApi(interval = 1).apply { script({ token(key) }) }
        val viewModel = SignInViewModel(
            DeviceFlowSignIn(api, "Iv-test-client"),
            completer(db, keys.tokenManager, apiService(keys.tokenManager, network, session), legacy.store),
            session
        )
        val navigation = NavigationViewModel(keys.tokenManager)
        viewModel.signIn()
        withTimeout(15_000) { viewModel.uiState.first { it.decision != null } }
        return viewModel to navigation
    }

    @Test
    fun cancelOnAccountChangeShowsSignInAndKeepsTheData() = runBlocking<Unit> {
        val before = localCounts(db)
        val (viewModel, navigation) = approved()
        val decision = viewModel.uiState.value.decision as AccountDecision.ChangeAccount
        assertEquals("fixture-login", decision.old.login)
        assertEquals("second-login", decision.new.login)

        viewModel.onCancel()

        // The screen state is derived on the main thread; wait for it to settle.
        val state = withTimeout(5_000) { viewModel.uiState.first { it.decision == null } }
        assertEquals(SignInPhase.Idle, state.phase)
        withTimeout(10_000) { navigation.authState.first { it == AuthState.Unauthenticated } }
        assertNull(keys.tokenManager.currentKey())
        assertEquals(before, localCounts(db))
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
    }

    @Test
    fun continueOnAccountChangeOpensHomeWithTheOldDataGone() = runBlocking<Unit> {
        val (viewModel, navigation) = approved()
        assertTrue(viewModel.uiState.value.decision is AccountDecision.ChangeAccount)

        viewModel.onContinue()

        withTimeout(10_000) { navigation.authState.first { it == AuthState.Authenticated } }
        val counts = localCounts(db)
        WIPE_TABLES.forEach { table -> assertEquals("$table empty", 0, counts[table]) }
        assertEquals(SECOND_USER_ID, db.accountDao().get()!!.userId)
        withTimeout(5_000) { viewModel.uiState.first { it.decision == null && it.phase == SignInPhase.Idle } }
    }

    @Test
    fun keepOnKeepDataOpensHomeWithTheDataKept() = runBlocking<Unit> {
        db.accountDao().deleteAll()
        legacy.store.setOwnerUnknown(true)
        val before = localCounts(db)
        val (viewModel, navigation) = approved()
        val decision = viewModel.uiState.value.decision as AccountDecision.KeepData
        assertEquals("second-login", decision.account.login)
        assertEquals(3, decision.counts.repos)

        viewModel.onKeep()

        withTimeout(10_000) { navigation.authState.first { it == AuthState.Authenticated } }
        assertEquals(before + ("account" to 1), localCounts(db))
        assertEquals(SECOND_USER_ID, db.accountDao().get()!!.userId)
    }

    @Test
    fun removeOnKeepDataOpensHomeWithTheDataGone() = runBlocking<Unit> {
        db.accountDao().deleteAll()
        legacy.store.setOwnerUnknown(true)
        val (viewModel, navigation) = approved()
        assertTrue(viewModel.uiState.value.decision is AccountDecision.KeepData)

        viewModel.onRemove()

        withTimeout(10_000) { navigation.authState.first { it == AuthState.Authenticated } }
        val counts = localCounts(db)
        WIPE_TABLES.forEach { table -> assertEquals("$table empty", 0, counts[table]) }
    }
}
