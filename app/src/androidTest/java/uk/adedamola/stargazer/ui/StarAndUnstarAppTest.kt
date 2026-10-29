package uk.adedamola.stargazer.ui

import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.ui.screens.HomeGitHubSearch
import uk.adedamola.stargazer.ui.screens.HomeAccount
import uk.adedamola.stargazer.ui.screens.HomeStarActions
import uk.adedamola.stargazer.ui.screens.HomeRemovals
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.paging.ExperimentalPagingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.FakeConnectivity
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.WindowGate
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.calls
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.fixtureRepo
import uk.adedamola.stargazer.data.auth.graphQlApi
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.listsJson
import uk.adedamola.stargazer.data.auth.profileJson
import uk.adedamola.stargazer.data.auth.rateLimited
import uk.adedamola.stargazer.data.auth.repoJson
import uk.adedamola.stargazer.data.auth.searchJson
import uk.adedamola.stargazer.data.auth.starEngine
import uk.adedamola.stargazer.data.auth.starPath
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.GitHubRepositoryImpl
import uk.adedamola.stargazer.data.repository.GitHubSearch
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.StarListRepository
import uk.adedamola.stargazer.data.repository.StarReconciler
import uk.adedamola.stargazer.ui.screens.DetailScreen
import uk.adedamola.stargazer.ui.screens.DetailViewModel
import uk.adedamola.stargazer.ui.screens.HomeScreen
import uk.adedamola.stargazer.ui.screens.HomeViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The real Home and repo screens with their view models, over the production GitHub client
 * answered by a scripted GitHub: unstar from the repo screen with Undo, the Home swipe and its
 * failure, starring a GitHub search result, and every state of the repo screen's star control.
 * Each state is checked for touch targets under the bars or the cutout and saved as a capture
 * named after its board.
 */
class StarAndUnstarAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val check = ScreenCheck(composeRule, "star-unstar")

    private sealed interface Shown {
        data object Home : Shown
        data class Detail(val name: String, val id: Int) : Shown
    }

    private val shown = mutableStateOf<Shown>(Shown.Home)

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var engine: StarEngine
    private val gate = WindowGate()
    private val connectivity = FakeConnectivity()
    private var tagId = 0

    /** GitHub's answer to a star endpoint, per method and path. */
    private val starAnswers = mutableMapOf<String, () -> Scripted>()
    private val network = ScriptedNetwork { request ->
        val path = request.url.encodedPath
        when {
            path.startsWith("/user/starred/") -> starAnswers["${request.method} $path"]?.invoke() ?: Scripted(204, "")
            path == "/user/starred" -> Scripted(200, "[]")
            path == "/user" -> Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds", "Ada Okafor"))
            path == "/graphql" -> Scripted(200, listsJson(FIXTURE_USER_ID, 5, emptyList()))
            path == "/search/repositories" -> Scripted(200, searchJson(2814, listOf(
                repoJson(4001, "JetBrains", "kotlin", 50318),
                repoJson(4002, "square", "retrofit", 43402),
                repoJson(4003, "Kotlin", "kotlinx.coroutines", 13214),
            )))
            else -> Scripted(404, """{"message":"Not Found"}""")
        }
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "", 1L))
        @Suppress("DEPRECATION") // sdlc-debt: test seed uses the deprecated insert-or-replace DAO call (ceiling: test-only; upgrade path: an upsert helper if the DAO drops it)
        db.repositoryDao().insertRepositories((1..5).map(::fixtureRepo))
        db.repositoryDao().updateFavorite(900_001, true)
        db.repositoryDao().updatePinned(900_001, true)
        tagId = db.tagDao().insertTag(Tag(name = "kept", color = "#FF6600")).toInt()
        db.repositoryTagDao().addTagToRepository(RepositoryTag(900_001, tagId))
        db.syncMetadataDao().updateMetadata(SyncMetadata(dataType = "starred_repos", isInitialSyncComplete = true))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engine = starEngine(db, network, keys.tokenManager, scope, connectivity, sleep = gate.sleep)
    }

    @After
    fun tearDown() {
        gate.release()
        scope.cancel()
        db.close()
        keys.close()
    }

    @OptIn(ExperimentalPagingApi::class)
    private fun show() {
        val api = apiService(keys.tokenManager, network)
        // Built once outside composition, so a recomposition never creates a second view model.
        val home = HomeViewModel(
            savedStateHandle = SavedStateHandle(),
            gitHubRepository = GitHubRepositoryImpl(db.repositoryDao(), StarredReposRemoteMediator(api, db), api),
            organizationRepository = OrganizationRepository(db.repositoryDao(), db.tagDao(), db.repositoryTagDao(), db.searchPresetDao()),
            accessSession = AccessSession(keys.tokenManager),
            starListRepository = StarListRepository(db.starListDao()),
            searchFactory = HomeGitHubSearch.Factory(GitHubSearch(api, db.repositoryDao()), connectivity, engine),
            removalsFactory = HomeRemovals.Factory(StarReconciler(api, db, engine)),
            starActionsFactory = HomeStarActions.Factory(engine),
            accountFactory = HomeAccount.Factory(AccountRepository(db.accountDao(), db.gitHubProfileDao(), db.repositoryDao()), StarListRepository(db.starListDao()), AccountDataRefresher(api, graphQlApi(keys.tokenManager, network), db) { 1L }),
        )
        val detail = DetailViewModel(GitHubRepositoryImpl(db.repositoryDao(), StarredReposRemoteMediator(api, db), api), SavedStateHandle(), engine)
        check.render {
            SharedTransitionLayout {
                AnimatedContent(targetState = shown.value, label = "screen") { screen ->
                    when (screen) {
                        Shown.Home -> HomeScreen(
                            onRepoClick = { name, id -> shown.value = Shown.Detail(name, id) },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            viewModel = home
                        )
                        is Shown.Detail -> DetailScreen(
                            repoName = screen.name,
                            repoId = screen.id,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            viewModel = detail
                        )
                    }
                }
            }
        }
    }

    private fun waitFor(text: String, substring: Boolean = false, timeout: Long = 10_000) =
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun waitGone(text: String, timeout: Long = 10_000) =
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
        }

    /** The star switch of [fullName] in the given state. */
    private fun starToggle(fullName: String, starred: Boolean) =
        hasContentDescription("Star $fullName") and (if (starred) isOn() else isOff())

    private fun waitToggle(fullName: String, starred: Boolean, timeout: Long = 10_000) =
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodes(starToggle(fullName, starred)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun waitUntil(what: String, timeout: Long = 10_000, condition: () -> Boolean) =
        try {
            composeRule.waitUntil(timeout) { condition() }
        } catch (e: Throwable) {
            throw AssertionError("timed out waiting for: $what", e)
        }

    private fun openDetail(n: Int) {
        waitFor("repo-$n")
        composeRule.onNode(hasText("repo-$n")).performClick()
        waitToggle("fixture/repo-$n", starred = true)
    }

    private fun backToHome() {
        composeRule.runOnUiThread { shown.value = Shown.Home }
        waitFor("STARGAZER")
    }

    private fun capture(board: String) {
        composeRule.waitForIdle()
        check.assertTargetsInsideSafeArea()
        check.capture(board)
    }

    private fun repoIntact(id: Int) = runBlocking {
        val row = db.repositoryDao().getRepositoryById(id)
        row != null && row.isFavorite && row.isPinned &&
            db.repositoryTagDao().getTagsForRepository(id).first().map { it.id } == listOf(tagId)
    }

    @Test
    fun detailUnstarThenUndo() {
        show()
        openDetail(1)
        waitFor("STARRED")

        composeRule.onNode(starToggle("fixture/repo-1", starred = true)).performClick()

        waitUntil("one DELETE") { network.calls("DELETE", starPath("fixture", "repo-1")).size == 1 }
        waitToggle("fixture/repo-1", starred = false)
        waitFor("UNSTARRED fixture/repo-1")
        capture("detail-star-toggle--not-starred")

        backToHome()
        waitFor("repo-2")
        assertTrue("repo-1 left Home", composeRule.onAllNodes(hasText("repo-1")).fetchSemanticsNodes().isEmpty())
        waitFor("UNSTARRED fixture/repo-1")
        capture("home-unstar-gesture--undo")

        composeRule.onNode(hasText("UNDO")).performClick()

        waitUntil("one PUT") { network.calls("PUT", starPath("fixture", "repo-1")).size == 1 }
        waitFor("repo-1")
        waitUntil("repo-1 back with favorite, pin and tag") { repoIntact(900_001) && runBlocking { db.pendingUnstarDao().get(900_001) } == null }
        assertEquals(1, network.calls("DELETE", starPath("fixture", "repo-1")).size)
    }

    @Test
    fun homeSwipeUnstars() {
        show()
        waitFor("repo-2")
        val card = composeRule.onNode(hasText("repo-2"))

        card.performTouchInput { down(center); moveBy(Offset(-60f, 0f)); moveBy(Offset(-240f, 0f)) }
        composeRule.waitForIdle()
        capture("home-unstar-gesture--swiping")
        card.performTouchInput { moveBy(Offset(-600f, 0f)); up() }

        waitUntil("one DELETE for the swiped row") { network.calls("DELETE", starPath("fixture", "repo-2")).size == 1 }
        waitGone("repo-2")
        waitFor("UNSTARRED fixture/repo-2")
        assertNotNull("data kept during the window", runBlocking { db.repositoryDao().getRepositoryById(900_002) })

        waitUntil("window open") { gate.waits > 0 }
        gate.release()

        waitUntil("repo-2 deleted after the window") { runBlocking { db.repositoryDao().getRepositoryById(900_002) } == null }
        waitGone("UNSTARRED fixture/repo-2")
    }

    @Test
    fun homeRowOffersUnstarWithoutTheGesture() {
        show()
        waitFor("repo-2")
        // The card is the node TalkBack and Switch Access focus; the action must sit on it.
        val actions = composeRule.onNode(hasText("repo-2")).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        val unstar = actions.single { it.label == "Unstar fixture/repo-2" }
        composeRule.runOnUiThread { unstar.action() }

        waitUntil("one DELETE for the row") { network.calls("DELETE", starPath("fixture", "repo-2")).size == 1 }
        waitGone("repo-2")
    }

    @Test
    fun homeSwipeFailureRestores() {
        starAnswers["DELETE ${starPath("fixture", "repo-3")}"] = { Scripted(500) }
        show()
        waitFor("repo-3")

        composeRule.onNode(hasText("repo-3")).performTouchInput {
            down(center); moveBy(Offset(-60f, 0f)); moveBy(Offset(-700f, 0f)); up()
        }

        waitUntil("one DELETE") { network.calls("DELETE", starPath("fixture", "repo-3")).size == 1 }
        waitFor("UNSTAR_FAILED — fixture/repo-3 is back with its tags.")
        waitFor("repo-3")
        waitFor("RETRY")
        assertNull(runBlocking { db.pendingUnstarDao().get(900_003) })
        assertNotNull(runBlocking { db.repositoryDao().getRepositoryById(900_003) })
        capture("home-unstar-gesture--failed")
    }

    @Test
    fun searchStars() {
        val answerPut = CountDownLatch(1)
        starAnswers["PUT ${starPath("JetBrains", "kotlin")}"] = { answerPut.await(15, TimeUnit.SECONDS); Scripted(204, "") }
        show()
        waitFor("repo-1")

        composeRule.onNode(hasSetTextAction()).performTextInput("zeta")

        waitFor("IN_YOUR_STARS · 0")
        waitFor("ON_GITHUB · 2,814")
        waitToggle("JetBrains/kotlin", starred = false)
        waitToggle("square/retrofit", starred = false)
        waitToggle("Kotlin/kotlinx.coroutines", starred = false)
        capture("search-star-control--default")

        composeRule.onNode(starToggle("JetBrains/kotlin", starred = false)).performClick()
        waitFor("SAVING…")
        capture("search-star-control--in-flight")
        answerPut.countDown()

        waitToggle("JetBrains/kotlin", starred = true)
        waitUntil("the repo entered the starred list") { runBlocking { db.repositoryDao().getRepositoryById(4001) } != null }
        assertEquals(1, network.calls("PUT", starPath("JetBrains", "kotlin")).size)
        waitFor("STARRED")
        capture("search-star-control--default-after-star")
    }

    @Test
    fun detailStates() {
        val answerDelete = CountDownLatch(1)
        val path = starPath("fixture", "repo-4")
        starAnswers["DELETE $path"] = { answerDelete.await(15, TimeUnit.SECONDS); Scripted(500) }
        show()
        openDetail(4)
        waitFor("STARRED")
        capture("detail-star-toggle--starred")

        composeRule.onNode(starToggle("fixture/repo-4", starred = true)).performClick()
        waitFor("SAVING…")
        capture("detail-star-toggle--in-flight")
        answerDelete.countDown()

        waitFor("UNSTAR_FAILED — GitHub did not confirm. The repo stays starred.")
        waitFor("[ RETRY ]")
        waitToggle("fixture/repo-4", starred = true)
        capture("detail-star-toggle--failed")

        starAnswers["DELETE $path"] = { rateLimited(403, retryAfterSeconds = 600) }
        composeRule.onNode(hasText("[ RETRY ]")).performClick()
        waitFor("RATE_LIMITED — GitHub accepts more changes at ", substring = true)
        composeRule.onNode(starToggle("fixture/repo-4", starred = true)).assertIsNotEnabled()
        capture("detail-star-toggle--rate-limited")

        connectivity.set(false)
        waitFor("CONNECTION_NEEDED — starring needs the internet.")
        composeRule.onNode(starToggle("fixture/repo-4", starred = true)).assertIsNotEnabled()
        capture("detail-star-toggle--offline")

        assertEquals("one DELETE per tap, none while limited or offline", 2, network.calls("DELETE", path).size)
        assertTrue("repo-4 kept", runBlocking { db.repositoryDao().getRepositoryById(900_004) } != null)
    }
}
