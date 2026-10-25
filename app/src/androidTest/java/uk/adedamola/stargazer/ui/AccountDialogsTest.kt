package uk.adedamola.stargazer.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.auth.LocalDataCounts
import uk.adedamola.stargazer.ui.components.ACCOUNT_CHANGE_DIALOG_TAG
import uk.adedamola.stargazer.ui.components.AccountChangeDialog
import uk.adedamola.stargazer.ui.components.KEEP_DATA_DIALOG_TAG
import uk.adedamola.stargazer.ui.components.KeepDataDialog
import uk.adedamola.stargazer.ui.screens.SignInPhase
import uk.adedamola.stargazer.ui.screens.SignInScreen
import uk.adedamola.stargazer.ui.screens.SignInUiState
import uk.adedamola.stargazer.ui.theme.StargazerTheme
import java.io.File

/**
 * Both account dialogs as drawn on their boards: the copy, the one-line account rows of the
 * worst case, nothing under the system bars or the cutout, and a screenshot named
 * after each board.
 */
class AccountDialogsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ada = AccountSummary(3, "ada-builds", "Ada Okafor", null)
    private val kofi = AccountSummary(4, "kofi-dev", "Kofi Mensah", null)
    private val longLogin = "aleksandra-wisniewska-kowalczyk-dev-01"
    private val aleksandra = AccountSummary(5, longLogin, "Aleksandra Wiśniewska-Kowalczyk", null)
    private val j = AccountSummary(6, "j", null, null)

    private fun render(dialog: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        }
        composeRule.setContent {
            StargazerTheme {
                SignInScreen(
                    state = SignInUiState(phase = SignInPhase.Completing, userCode = "WDJB-MJHT", secondsLeft = 872),
                    onSignIn = {}, onCopyAndOpen = {}, onCopyCode = {}, cursorBlinks = false
                )
                dialog()
            }
        }
        composeRule.waitForIdle()
    }

    /** The dialog's own window: the topmost window view of this process. */
    private fun dialogRoot(): View = WindowInspector.getGlobalWindowViews().last()

    /**
     * The dialog window covers the screen, and its card and every control in it sit inside the
     * window minus the system bars and the cutout, as reported to the dialog's own root view.
     */
    private fun assertInsideSafeArea(tag: String) {
        var safe = Rect.Zero
        composeRule.runOnUiThread {
            val root = dialogRoot()
            val activityDecor = composeRule.activity.window.decorView
            assertTrue("dialog window is a separate window", root !== activityDecor)
            assertEquals("dialog window spans the width", activityDecor.width, root.width)
            assertEquals("dialog window spans the height", activityDecor.height, root.height)
            val insets = ViewCompat.getRootWindowInsets(root)!!
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            assertTrue("dialog window reports a status bar inset", insets.top > 0)
            assertTrue("dialog window reports a navigation bar inset", insets.bottom > 0)
            safe = Rect(
                insets.left.toFloat(),
                insets.top.toFloat(),
                (root.width - insets.right).toFloat(),
                (root.height - insets.bottom).toFloat()
            )
        }
        val nodes = mutableListOf<Pair<String, SemanticsNode>>()
        nodes += "card" to composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag)))
            .fetchSemanticsNodes().also { assertEquals("two buttons", 2, it.size) }
            .forEach { nodes += "button" to it }
        nodes.forEach { (name, node) ->
            // A button's touch target can be larger than what it draws; both must stay clear.
            listOf(node.boundsInWindow, node.touchBoundsInRoot).forEach { b ->
                assertTrue(
                    "$name $b outside safe area $safe",
                    b.left >= safe.left && b.top >= safe.top && b.right <= safe.right && b.bottom <= safe.bottom
                )
            }
            if (name == "button") {
                val density = composeRule.activity.resources.displayMetrics.density
                val target = node.touchBoundsInRoot
                assertTrue("$name touch target ${target.height / density}dp high", target.height / density >= 47.5f)
            }
        }
    }

    private fun capture(board: String) {
        composeRule.waitForIdle()
        val activity = composeRule.activity
        val suffix = if (activity.resources.configuration.screenWidthDp >= 600) "foldable" else "phone"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(activity.filesDir, "account-dialogs").apply { mkdirs() }
        File(dir, "$board--$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertShown(vararg texts: String) = texts.forEach { text ->
        assertTrue(
            "missing: $text",
            composeRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().single()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    private val isPhone get() = composeRule.activity.resources.configuration.screenWidthDp < 600

    @Test
    fun accountChangeDefault() {
        render { AccountChangeDialog(old = ada, new = kofi, onContinue = {}, onCancel = {}) }
        assertShown(
            "CHANGE ACCOUNT",
            "Signed in as @kofi-dev. Data from @ada-builds will be removed from this phone:",
            "– starred repos cache",
            "– favorites, pins and tags",
            "– saved search presets",
            "– profile and star lists",
            "FROM", "TO", "Ada Okafor", "@ada-builds", "Kofi Mensah", "@kofi-dev",
            "CANCEL", "REMOVE & CONTINUE"
        )
        val pane = composeRule.onNodeWithTag(ACCOUNT_CHANGE_DIALOG_TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.PaneTitle)
        assertEquals("CHANGE ACCOUNT", pane)
        assertInsideSafeArea(ACCOUNT_CHANGE_DIALOG_TAG)
        capture("account-change-dialog--default")
    }

    @Test
    fun accountChangeWorst() {
        render { AccountChangeDialog(old = aleksandra, new = j, onContinue = {}, onCancel = {}) }
        assertShown("Signed in as @j. Data from @$longLogin will be removed from this phone:", "@j")

        assertTrue("the sentence wraps", layoutOf("Signed in as @j. Data from @$longLogin will be removed from this phone:").lineCount > 1)
        listOf(aleksandra.name!!, "@$longLogin", "@j").forEach { text ->
            assertEquals("$text on one line", 1, layoutOf(text).lineCount)
        }
        if (isPhone) assertTrue("the long login ends in an ellipsis", layoutOf("@$longLogin").isLineEllipsized(0))
        assertFalse("no name row for @j", composeRule.onAllNodes(hasText("null"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertInsideSafeArea(ACCOUNT_CHANGE_DIALOG_TAG)
        capture("account-change-dialog--worst")
    }

    @Test
    fun keepDataDefault() {
        render {
            KeepDataDialog(account = ada, counts = LocalDataCounts(repos = 1284, tags = 37, presets = 5), onKeep = {}, onRemove = {}, onDismiss = {})
        }
        assertShown(
            "KEEP LOCAL DATA?",
            "Data from an earlier sign-in is on this phone: 1,284 starred repos, 37 tags, 5 presets. Stargazer cannot tell which GitHub account it came from.",
            "Keep it for @ada-builds?",
            "Remove deletes it from this phone.",
            "REMOVE", "KEEP"
        )
        assertInsideSafeArea(KEEP_DATA_DIALOG_TAG)
        capture("keep-data-dialog--default")
    }
}
