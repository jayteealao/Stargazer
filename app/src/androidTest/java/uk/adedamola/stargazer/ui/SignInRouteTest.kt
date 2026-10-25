package uk.adedamola.stargazer.ui

import android.content.ClipboardManager
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.DeviceCodeResponse
import uk.adedamola.stargazer.data.auth.DeviceFlowSignIn
import uk.adedamola.stargazer.data.auth.FIXTURE_DEVICE_CODE
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_CODE
import uk.adedamola.stargazer.data.auth.FakeDeviceFlowApi
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.SignInCompleter
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.answer
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.ui.screens.ACCESS_ENDED_DESCRIPTION
import uk.adedamola.stargazer.ui.screens.SignInRoute
import uk.adedamola.stargazer.ui.screens.SignInViewModel
import uk.adedamola.stargazer.ui.theme.StargazerTheme
import java.util.concurrent.CopyOnWriteArrayList

/** The sign-in screen with its view model, clipboard and browser (AC-2, AC-3, AC-6, S4-1). */
class SignInRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
    private val api = FakeDeviceFlowApi(expiresIn = 900, interval = 1)
    private val opened = CopyOnWriteArrayList<Uri>()
    private var browserFound = true

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

    private fun show() {
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        }
        // Built once outside composition, so a recomposition never creates a second view model.
        val viewModel = SignInViewModel(
            DeviceFlowSignIn(api, "Iv-test-client"),
            SignInCompleter(apiService(keys.tokenManager, ScriptedNetwork(), session), db.accountDao(), keys.tokenManager),
            session
        )
        composeRule.setContent {
            StargazerTheme {
                SignInRoute(
                    viewModel = viewModel,
                    openBrowser = { _, uri -> opened += uri; browserFound },
                    cursorBlinks = false
                )
            }
        }
    }

    private fun waitForText(text: String, timeout: Long = 10_000) =
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun tapSignInShowsCodeButtonAndCountdown() {
        show()
        composeRule.onNodeWithText("SIGN IN WITH GITHUB").assertIsDisplayed().performClick()

        waitForText(FIXTURE_USER_CODE)
        composeRule.onNodeWithText(FIXTURE_USER_CODE).assertIsDisplayed()
        composeRule.onNodeWithText("COPY CODE & OPEN GITHUB").assertIsDisplayed().assertIsEnabled()
        val hint = composeRule.onNodeWithText("left", substring = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assertTrue(hint, hint == "enter on github.com · 15:00 left" || hint == "enter on github.com · 14:59 left")
    }

    @Test
    fun copyAndOpenPutsCodeOnClipboardAndOpensDevicePage() {
        show()
        composeRule.onNodeWithText("SIGN IN WITH GITHUB").performClick()
        waitForText(FIXTURE_USER_CODE)

        composeRule.onNodeWithText("COPY CODE & OPEN GITHUB").performClick()
        composeRule.waitForIdle()

        var clip: String? = null
        composeRule.runOnUiThread {
            val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
            clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        }
        assertEquals(FIXTURE_USER_CODE, clip)
        assertEquals(listOf(Uri.parse("https://github.com/login/device")), opened.toList())
    }

    @Test
    fun expiredShowsGetNewCodeAndDisablesItInFlight() {
        api.script({ answer("expired_token") })
        show()
        composeRule.onNodeWithText("SIGN IN WITH GITHUB").performClick()

        waitForText("GET A NEW CODE")
        composeRule.onNodeWithText("CODE_EXPIRED — this code is older than 15 minutes.").assertIsDisplayed()
        val held = CompletableDeferred<DeviceCodeResponse>()
        api.onRequest = { held.await() }

        composeRule.onNodeWithText("GET A NEW CODE").performClick()

        waitForText("requesting a code from github.com…")
        composeRule.onNodeWithText("COPY CODE & OPEN GITHUB").assertIsNotEnabled()
        composeRule.onNodeWithText("COPY CODE & OPEN GITHUB").performClick()
        assertEquals("a disabled button sends nothing", 2, api.requestCount)
        held.complete(DeviceCodeResponse(FIXTURE_DEVICE_CODE, FIXTURE_USER_CODE, "https://github.com/login/device", 900, 1))
    }

    @Test
    fun deniedShowsDenied() {
        api.script({ answer("access_denied") })
        show()
        composeRule.onNodeWithText("SIGN IN WITH GITHUB").performClick()

        waitForText("ACCESS_DENIED")
        composeRule.onNodeWithText("ACCESS_DENIED — GitHub reports that the request was cancelled.").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing was linked. Start again when you are ready.").assertIsDisplayed()
        composeRule.onNodeWithText("SIGN IN WITH GITHUB").assertIsEnabled()
    }

    @Test
    fun noBrowserKeepsCodeAndAddressVisible() {
        browserFound = false
        show()
        composeRule.onNodeWithText("SIGN IN WITH GITHUB").performClick()
        waitForText(FIXTURE_USER_CODE)

        composeRule.onNodeWithText("COPY CODE & OPEN GITHUB").performClick()

        waitForText("no browser found on this phone")
        composeRule.onNodeWithText(FIXTURE_USER_CODE).assertIsDisplayed()
        composeRule.onNodeWithText("github.com/login/device").assertIsDisplayed()
        composeRule.onNodeWithText("COPY CODE").assertIsDisplayed()
        val polls = api.pollTimes.size
        composeRule.waitUntil(5_000) { api.pollTimes.size > polls }
    }

    @Test
    fun accessEndedBannerClearsOnSignIn() {
        val key = fakeKey()
        runBlocking { keys.tokenManager.saveToken(key) }
        session.onUnauthorized(key)
        assertTrue(session.accessEnded.value)
        show()

        composeRule.onNodeWithText("GITHUB_ACCESS_ENDED", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(ACCESS_ENDED_DESCRIPTION)).assertIsDisplayed()

        composeRule.onNodeWithText("SIGN IN WITH GITHUB").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("GITHUB_ACCESS_ENDED"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertFalse(session.accessEnded.value)
    }
}
