package uk.adedamola.stargazer.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import uk.adedamola.stargazer.ui.theme.StargazerTheme
import java.io.File

/**
 * Renders a screen edge to edge in the test activity, checks that its texts show and that no
 * touch target sits under the system bars or the cutout, and saves a screenshot named after its
 * board under `files/<folder>/<board>--<phone|foldable>.png`.
 */
class ScreenCheck(
    private val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    private val folder: String,
) {
    fun render(content: @Composable () -> Unit) {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        }
        rule.setContent { StargazerTheme { content() } }
        rule.waitForIdle()
    }

    fun assertShown(vararg texts: String) = texts.forEach { text ->
        assertTrue(
            "missing: $text",
            rule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    fun assertAbsent(vararg texts: String) = texts.forEach { text ->
        assertTrue(
            "unexpected: $text",
            rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )
    }

    /** The window minus the system bars and the cutout. */
    fun safeArea(): Rect {
        var safe = Rect.Zero
        rule.runOnUiThread {
            val decor = rule.activity.window.decorView
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
        return safe
    }

    /** Every clickable node that is on screen sits inside the safe area. */
    fun assertTargetsInsideSafeArea() {
        val safe = safeArea()
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val b = node.boundsInWindow
            if (b.width <= 0f || b.height <= 0f) return@forEach // scrolled out of view
            assertTrue(
                "clickable ${node.config} at $b outside safe area $safe",
                b.left >= safe.left && b.top >= safe.top && b.right <= safe.right && b.bottom <= safe.bottom
            )
        }
    }

    /** The laid-out text of the unmerged node showing exactly [text]. */
    fun layout(text: String): TextLayoutResult {
        val node = textNode(text)
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    fun textNode(text: String): SemanticsNode =
        rule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().first()

    fun nodeWithText(text: String): SemanticsMatcher = hasText(text)

    fun capture(board: String) {
        rule.waitForIdle()
        val activity = rule.activity
        val widthDp = activity.resources.configuration.screenWidthDp
        val suffix = if (widthDp >= 600) "foldable" else "phone"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(activity.filesDir, folder).apply { mkdirs() }
        File(dir, "$board--$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
