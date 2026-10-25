package uk.adedamola.stargazer.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.ui.screens.SignInPhase
import uk.adedamola.stargazer.ui.screens.SignInScreen
import uk.adedamola.stargazer.ui.screens.SignInUiState
import uk.adedamola.stargazer.ui.theme.StargazerTheme
import java.io.File

/**
 * Each sign-in state as drawn on the boards: its texts, nothing under the system bars or the
 * cutout, and a screenshot named after its board (AC-6 screens, S4-1, AC-40b).
 */
class SignInScreenStatesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val code = "WDJB-MJHT"

    private fun render(state: SignInUiState) {
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        }
        composeRule.setContent {
            StargazerTheme {
                SignInScreen(state = state, onSignIn = {}, onCopyAndOpen = {}, onCopyCode = {}, cursorBlinks = false)
            }
        }
        composeRule.waitForIdle()
    }

    private fun waiting(phase: SignInPhase) = SignInUiState(phase = phase, userCode = code, secondsLeft = 872)

    /** Every clickable node, the code and the button sit inside the window minus bars and cutout. */
    private fun assertInsideSafeArea(button: String, hasCode: Boolean) {
        var safe = Rect.Zero
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)!!
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            assertTrue("window has a status bar inset", insets.top > 0)
            safe = Rect(
                insets.left.toFloat(),
                insets.top.toFloat(),
                (decor.width - insets.right).toFloat(),
                (decor.height - insets.bottom).toFloat()
            )
        }
        val nodes = mutableListOf<Pair<String, SemanticsNode>>()
        nodes += button to composeRule.onNodeWithText(button).fetchSemanticsNode()
        if (hasCode) nodes += "code" to composeRule.onNodeWithText(code).fetchSemanticsNode()
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { nodes += "clickable" to it }
        nodes.forEach { (name, node) ->
            val b = node.boundsInWindow
            assertTrue(
                "$name $b outside safe area $safe",
                b.left >= safe.left && b.top >= safe.top && b.right <= safe.right && b.bottom <= safe.bottom
            )
        }
    }

    private fun capture(board: String) {
        composeRule.waitForIdle()
        val activity = composeRule.activity
        val widthDp = activity.resources.configuration.screenWidthDp
        val suffix = if (widthDp >= 600) "foldable" else "phone"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(activity.filesDir, "sign-in").apply { mkdirs() }
        File(dir, "$board--$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertShown(vararg texts: String) = texts.forEach { text ->
        assertTrue(
            "missing: $text",
            composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun idle() {
        render(SignInUiState())
        assertShown(
            "Link your GitHub account to sync your stars, lists and profile.",
            "Stargazer asks only for Starring (read and write). No access to your code.",
            "SIGN IN WITH GITHUB"
        )
        assertInsideSafeArea("SIGN IN WITH GITHUB", hasCode = false)
        capture("sign-in--idle")
    }

    @Test
    fun requesting() {
        render(SignInUiState(phase = SignInPhase.Requesting))
        assertShown("requesting a code from github.com…", "COPY CODE & OPEN GITHUB")
        assertInsideSafeArea("COPY CODE & OPEN GITHUB", hasCode = false)
        capture("sign-in--requesting")
    }

    @Test
    fun waiting() {
        render(waiting(SignInPhase.Waiting))
        assertShown(code, "enter on github.com · 14:32 left", "COPY CODE & OPEN GITHUB")
        assertInsideSafeArea("COPY CODE & OPEN GITHUB", hasCode = true)
        capture("sign-in--waiting")
    }

    @Test
    fun expired() {
        render(waiting(SignInPhase.Expired))
        assertShown(code, "CODE_EXPIRED — this code is older than 15 minutes.", "GET A NEW CODE")
        assertInsideSafeArea("GET A NEW CODE", hasCode = true)
        capture("sign-in--expired")
    }

    @Test
    fun denied() {
        render(SignInUiState(phase = SignInPhase.Denied))
        assertShown(
            "ACCESS_DENIED — GitHub reports that the request was cancelled.",
            "Nothing was linked. Start again when you are ready.",
            "SIGN IN WITH GITHUB"
        )
        assertInsideSafeArea("SIGN IN WITH GITHUB", hasCode = false)
        capture("sign-in--denied")
    }

    @Test
    fun offline() {
        render(SignInUiState(phase = SignInPhase.Offline))
        assertShown("NO_CONNECTION — connect to the internet to get a code.", "TRY AGAIN")
        assertInsideSafeArea("TRY AGAIN", hasCode = false)
        capture("sign-in--offline")
    }

    @Test
    fun githubError() {
        render(SignInUiState(phase = SignInPhase.GitHubError("503")))
        assertShown("GITHUB_ERROR — github.com did not answer. Try again in a minute.", "code 503", "TRY AGAIN")
        assertInsideSafeArea("TRY AGAIN", hasCode = false)
        capture("sign-in--github-error")
    }

    @Test
    fun noBrowser() {
        render(waiting(SignInPhase.NoBrowser))
        assertShown(
            code,
            "no browser found on this phone · 14:32 left",
            "On any device, open",
            "github.com/login/device",
            "and enter the code above.",
            "COPY CODE"
        )
        assertInsideSafeArea("COPY CODE", hasCode = true)
        capture("sign-in--no-browser")
    }

    @Test
    fun accessEnded() {
        render(SignInUiState(accessEnded = true))
        assertShown(
            "GITHUB_ACCESS_ENDED",
            "GitHub no longer accepts this app’s access. Your stars, tags and favorites are still here. Sign in again to continue.",
            "SIGN IN WITH GITHUB"
        )
        assertInsideSafeArea("SIGN IN WITH GITHUB", hasCode = false)
        capture("access-ended-message--default")
    }
}
