package uk.adedamola.stargazer.data.auth

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.ui.AuthState
import uk.adedamola.stargazer.ui.NavigationViewModel
import uk.adedamola.stargazer.ui.screens.SignInPhase
import uk.adedamola.stargazer.ui.screens.SignInUiState
import uk.adedamola.stargazer.ui.screens.SignInViewModel

/** After approval: `GET /user`, the account record, the encrypted key, then Home. */
@RunWith(AndroidJUnit4::class)
class SignInCompletionTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
    private lateinit var legacy: LegacyStoreFixture
    private val key = fakeKey()
    private val userJson = """{"id":$FIXTURE_USER_ID,"login":"fixture-login","avatar_url":"https://avatars.example/u.png","name":null}"""
    private val network = ScriptedNetwork { request ->
        if (request.url.encodedPath == "/user") Scripted(200, userJson) else Scripted(404)
    }

    @Before
    fun setUp() {
        keys = TokenManagerFixture()
        db = inMemoryDatabase()
        session = AccessSession(keys.tokenManager)
        legacy = LegacyStoreFixture()
    }

    @After
    fun tearDown() {
        db.close()
        keys.close()
        legacy.close()
    }

    private fun newCompleter() =
        completer(db, keys.tokenManager, apiService(keys.tokenManager, network, session), legacy.store)

    @Test
    fun approvalStoresAccountThenKey() = runBlocking<Unit> {
        val navigation = NavigationViewModel(keys.tokenManager)
        withTimeout(10_000) { navigation.authState.first { it == AuthState.Unauthenticated } }

        val result = newCompleter().complete(key)

        assertEquals(SignInOutcome.Done, result.getOrNull())
        val request = network.requests.single()
        assertEquals("/user", request.url.encodedPath)
        assertEquals("Bearer $key", request.header("Authorization"))
        assertEquals(AccountRecord(FIXTURE_USER_ID, "fixture-login", "https://avatars.example/u.png", 42L), db.accountDao().get())
        assertEquals(key, keys.tokenManager.currentKey())
        val raw = keys.file.readBytes()
        assertFalse("plain key in file", String(raw, Charsets.ISO_8859_1).contains(key))
        assertFalse(
            "Base64 key in file",
            String(raw, Charsets.ISO_8859_1).contains(Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP))
        )
        withTimeout(10_000) { navigation.authState.first { it == AuthState.Authenticated } }
    }

    @Test
    fun userFailureStoresNothing() = runBlocking<Unit> {
        network.answer = { Scripted(503) }

        val result = newCompleter().complete(key)

        assertEquals(503, (result.exceptionOrNull() as HttpException).code())
        assertNull(db.accountDao().get())
        assertNull(keys.tokenManager.currentKey())
    }

    @Test
    fun user401DuringSignInRaisesNoAccessEnded() = runBlocking<Unit> {
        network.answer = { Scripted(401) }

        val result = newCompleter().complete(key)

        assertTrue(result.isFailure)
        assertFalse(session.accessEnded.value)
        assertNull(keys.tokenManager.currentKey())
        assertNull(db.accountDao().get())
    }

    @Test
    fun viewModelApprovalOpensHomeWithNoFurtherInput() = runBlocking<Unit> {
        val api = FakeDeviceFlowApi(interval = 1)
        api.script({ token(key) })
        val viewModel = SignInViewModel(DeviceFlowSignIn(api, "Iv-test-client"), newCompleter(), session)
        val navigation = NavigationViewModel(keys.tokenManager)

        viewModel.signIn()

        withTimeout(15_000) { navigation.authState.first { it == AuthState.Authenticated } }
        assertEquals(key, keys.tokenManager.currentKey())
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        withTimeout(5_000) { viewModel.uiState.first { it.phase == SignInPhase.Idle && it.userCode == null } }
        assertEquals(1, api.requestCount)
    }

    @Test
    fun uncheckedEarlierSignInIsRetriedWithTheSameApprovalAndOneCode() = runBlocking<Unit> {
        val oldToken = "old-pasted-token-fixture"
        legacy.seedOldToken(oldToken)
        var oldAnswer = Scripted(503)
        network.answer = { request ->
            when {
                request.url.encodedPath != "/user" -> Scripted(404)
                request.header("Authorization") == "Bearer $oldToken" -> oldAnswer
                else -> Scripted(200, userJson)
            }
        }
        val api = FakeDeviceFlowApi(interval = 1)
        api.script({ token(key) })
        val viewModel = SignInViewModel(DeviceFlowSignIn(api, "Iv-test-client"), newCompleter(), session)
        val navigation = NavigationViewModel(keys.tokenManager)

        viewModel.signIn()

        withTimeout(15_000) { viewModel.uiState.first { it.phase is SignInPhase.CheckPending } }
        assertEquals(SignInPhase.CheckPending(login = "fixture-login", repeated = false), viewModel.uiState.value.phase)
        assertTrue("Try again is ready", viewModel.uiState.value.actionEnabled)
        assertNull("nothing stored yet", keys.tokenManager.currentKey())

        viewModel.retryCheck()

        withTimeout(10_000) { viewModel.uiState.first { it.phase == SignInPhase.CheckPending("fixture-login", repeated = true) } }
        assertNull(keys.tokenManager.currentKey())

        oldAnswer = Scripted(200, userJson)
        viewModel.retryCheck()

        withTimeout(10_000) { navigation.authState.first { it == AuthState.Authenticated } }
        assertEquals(key, keys.tokenManager.currentKey())
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        assertNull("old token deleted", legacy.store.oldToken())
        withTimeout(5_000) { viewModel.uiState.first { it.phase == SignInPhase.Idle && it.userCode == null } }
        assertEquals("one device code for the whole sign-in", 1, api.requestCount)
    }

    @Test
    fun aRetriedCheckThatFailsKeepsTheApprovalAndAsksForNoCode() = runBlocking<Unit> {
        val oldToken = "old-pasted-token-fixture"
        legacy.seedOldToken(oldToken)
        var oldAnswer = Scripted(503)
        network.answer = { request ->
            when {
                request.url.encodedPath != "/user" -> Scripted(404)
                request.header("Authorization") == "Bearer $oldToken" -> oldAnswer
                else -> Scripted(200, userJson)
            }
        }
        // The key cannot be stored while the Keystore is busy: the retried check fails.
        val keyStore = FlakyKeyStore()
        val saving = keyStore.manager()
        val api = FakeDeviceFlowApi(interval = 1)
        api.script({ token(key) })
        val completer = completer(db, saving, apiService(saving, network, session), legacy.store)
        val viewModel = SignInViewModel(DeviceFlowSignIn(api, "Iv-test-client"), completer, session)
        try {
            viewModel.signIn()
            withTimeout(15_000) { viewModel.uiState.first { it.phase is SignInPhase.CheckPending } }

            oldAnswer = Scripted(200, userJson)
            keyStore.failingSaves = true
            viewModel.retryCheck()

            withTimeout(10_000) { viewModel.uiState.first { it.phase == SignInPhase.CheckPending("fixture-login", repeated = true) } }
            assertTrue("Try again is ready again", viewModel.uiState.value.actionEnabled)
            assertNull("nothing stored", saving.currentKey())

            keyStore.failingSaves = false
            viewModel.retryCheck()

            withTimeout(10_000) { viewModel.uiState.first { it.phase == SignInPhase.Idle && it.userCode == null } }
            assertEquals(key, saving.currentKey())
            assertEquals("the kept approval finished the sign-in", 1, api.requestCount)
        } finally {
            keyStore.close()
        }
    }

    @Test
    fun recheckingDisablesTheButton() {
        assertFalse(SignInUiState(phase = SignInPhase.Rechecking("fixture-login")).actionEnabled)
        assertTrue(SignInUiState(phase = SignInPhase.CheckPending("fixture-login", repeated = false)).actionEnabled)
    }
}
