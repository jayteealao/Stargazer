package uk.adedamola.stargazer.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.paging.SyncProgress
import uk.adedamola.stargazer.data.remote.model.GitHubRepository as GitHubRepoModel
import uk.adedamola.stargazer.data.remote.model.License
import uk.adedamola.stargazer.data.remote.model.Owner
import uk.adedamola.stargazer.data.repository.GitHubRepository
import uk.adedamola.stargazer.data.repository.Result
import uk.adedamola.stargazer.data.repository.SortOption
import uk.adedamola.stargazer.ui.screens.DetailScreen
import uk.adedamola.stargazer.ui.screens.DetailViewModel
import uk.adedamola.stargazer.ui.theme.StargazerTheme
import java.io.File

/** The app window draws behind the system bars with light icons on the dark theme. */
@HiltAndroidTest
class EdgeToEdgeSystemBarsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun systemBarsUseLightIconsOnTheDarkTheme() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val window = activity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                assertFalse("status bar icons must be light", controller.isAppearanceLightStatusBars)
                assertFalse("navigation bar icons must be light", controller.isAppearanceLightNavigationBars)

                val insets = ViewCompat.getRootWindowInsets(window.decorView)!!
                val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                assertTrue("status bar has height", statusTop > 0)

                // The content view starts at the top of the window, under the status bar.
                val location = IntArray(2)
                activity.findViewById<View>(android.R.id.content).getLocationInWindow(location)
                assertEquals("content drawn behind the status bar", 0, location[1])
            }
        }
    }
}

/** The README sits inside the safe area of an edge-to-edge window. */
@HiltAndroidTest
class EdgeToEdgeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun readmeStaysInsideTheSafeArea() {
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        }
        composeRule.setContent {
            StargazerTheme {
                SharedTransitionLayout {
                    AnimatedVisibility(visible = true) {
                        DetailScreen(
                            repoName = "fixture/readme",
                            repoId = 900001,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            viewModel = DetailViewModel(FakeReadmeRepository(), SavedStateHandle())
                        )
                    }
                }
            }
        }

        val decor = composeRule.activity.window.decorView
        composeRule.waitUntil(timeoutMillis = 15_000) {
            var laidOut = false
            composeRule.runOnUiThread { laidOut = findWebView(decor)?.let { it.height > 0 } == true }
            laidOut
        }
        composeRule.waitForIdle()

        var webTop = 0
        var webBottom = 0
        var windowHeight = 0
        var safeTop = 0
        var safeBottom = 0
        composeRule.runOnUiThread {
            val insets = ViewCompat.getRootWindowInsets(decor)!!
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            safeTop = insets.top
            safeBottom = insets.bottom
            windowHeight = decor.height
            val webView = findWebView(decor)!!
            val location = IntArray(2)
            webView.getLocationInWindow(location)
            webTop = location[1]
            webBottom = location[1] + webView.height
        }

        assertTrue("README top $webTop is below the top inset $safeTop", webTop >= safeTop)
        assertTrue(
            "README bottom $webBottom is above the bottom inset (${windowHeight - safeBottom})",
            webBottom <= windowHeight - safeBottom
        )
        val titleTop = composeRule.onAllNodesWithText("README", useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInWindow.top
        assertTrue("title top $titleTop is below the top inset $safeTop", titleTop >= safeTop)

        saveScreenshot()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findWebView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun saveScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.filesDir, "edge-to-edge").apply { mkdirs() }
        File(dir, "detail-readme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private class FakeReadmeRepository : GitHubRepository {
    override val syncProgress: StateFlow<SyncProgress> = MutableStateFlow(SyncProgress())

    override fun getStarredRepositoriesPaging(sortBy: SortOption): Flow<PagingData<GitHubRepoModel>> = emptyFlow()
    override fun searchRepositoriesPaging(query: String): Flow<PagingData<GitHubRepoModel>> = emptyFlow()
    override fun getRepositoriesByLanguagePaging(language: String): Flow<PagingData<GitHubRepoModel>> = emptyFlow()

    override suspend fun getRepositoryByFullName(fullName: String): Result<GitHubRepoModel?> =
        Result.Success(
            GitHubRepoModel(
                id = 900001,
                name = "readme",
                fullName = "fixture/readme",
                owner = Owner(
                    login = "fixture",
                    id = 1,
                    avatarUrl = "",
                    url = "",
                    htmlUrl = "https://github.com/fixture",
                    type = "User"
                ),
                htmlUrl = "https://github.com/fixture/readme",
                description = "Fixture repository for the README check.",
                fork = false,
                url = "",
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2026-10-01T00:00:00Z",
                pushedAt = "2026-10-01T00:00:00Z",
                homepage = null,
                size = 100,
                stargazersCount = 46123,
                watchersCount = 1204,
                language = "Kotlin",
                forksCount = 9312,
                openIssuesCount = 3,
                defaultBranch = "main",
                visibility = "public",
                license = License(key = "mit", name = "MIT License", spdxId = "MIT", url = null)
            )
        )

    override suspend fun getRepositoryReadme(owner: String, repo: String): Result<String> =
        Result.Success(
            (1..12).joinToString("\n") { n ->
                "<h2>Section $n</h2><p>Fixture README text for section $n. It is long enough to wrap " +
                    "across more than one line on a phone and to fill the screen.</p>"
            }
        )

    override suspend fun refreshStarredRepositories(): Result<Unit> = Result.Success(Unit)
}
