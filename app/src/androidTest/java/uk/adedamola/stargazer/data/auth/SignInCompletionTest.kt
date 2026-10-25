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
import uk.adedamola.stargazer.ui.screens.SignInViewModel

/** After approval: `GET /user`, the account record, the encrypted key, then Home (AC-5). */
@RunWith(AndroidJUnit4::class)
class SignInCompletionTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
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
    }

    @After
    fun tearDown() {
        db.close()
        keys.close()
    }

    private fun completer() = SignInCompleter(
        apiService(keys.tokenManager, network, session), db.accountDao(), keys.tokenManager, clock = { 42L }
    )

    @Test
    fun approvalStoresAccountThenKey() = runBlocking {
        val navigation = NavigationViewModel(keys.tokenManager)
        withTimeout(10_000) { navigation.authState.first { it == AuthState.Unauthenticated } }

        val result = completer().complete(key)

        assertTrue(result.isSuccess)
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
    fun userFailureStoresNothing() = runBlocking {
        network.answer = { Scripted(503) }

        val result = completer().complete(key)

        assertEquals(503, (result.exceptionOrNull() as HttpException).code())
        assertNull(db.accountDao().get())
        assertNull(keys.tokenManager.currentKey())
    }

    @Test
    fun user401DuringSignInRaisesNoAccessEnded() = runBlocking {
        network.answer = { Scripted(401) }

        val result = completer().complete(key)

        assertTrue(result.isFailure)
        assertFalse(session.accessEnded.value)
        assertNull(keys.tokenManager.currentKey())
        assertNull(db.accountDao().get())
    }

    @Test
    fun viewModelApprovalOpensHomeWithNoFurtherInput() = runBlocking {
        val api = FakeDeviceFlowApi(interval = 1)
        api.script({ token(key) })
        val viewModel = SignInViewModel(DeviceFlowSignIn(api, "Iv-test-client"), completer(), session)
        val navigation = NavigationViewModel(keys.tokenManager)

        viewModel.signIn()

        withTimeout(15_000) { navigation.authState.first { it == AuthState.Authenticated } }
        assertEquals(key, keys.tokenManager.currentKey())
        assertEquals(FIXTURE_USER_ID, db.accountDao().get()!!.userId)
        withTimeout(5_000) { viewModel.uiState.first { it.phase == SignInPhase.Idle && it.userCode == null } }
        assertEquals(1, api.requestCount)
    }
}
