package uk.adedamola.stargazer.ui

import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.ui.screens.HomeGitHubSearch
import uk.adedamola.stargazer.ui.screens.HomeRemovals
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.SavedStateHandle
import androidx.paging.ExperimentalPagingApi
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.graphQlApi
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.listsJson
import uk.adedamola.stargazer.data.auth.profileJson
import uk.adedamola.stargazer.data.auth.seedRepos
import uk.adedamola.stargazer.data.auth.starEngine
import uk.adedamola.stargazer.data.auth.starredPages
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
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
import uk.adedamola.stargazer.ui.components.REMOVAL_NOTICE_TAG
import uk.adedamola.stargazer.ui.screens.HomeScreen
import uk.adedamola.stargazer.ui.screens.HomeViewModel

/**
 * The real Home with its view model over the production GitHub client, answered by a scripted
 * starred list: the first pull-to-refresh after a sign-in that would remove repos shows the
 * count and removes nothing until CONTINUE, back leaves it shown, and a refresh that removes
 * nothing shows no notice. The notice is checked for touch targets under the bars or the cutout
 * and saved as a capture named after its board.
 */
class RemovalNoticeAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val check = ScreenCheck(composeRule, "removal-notice")

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var engine: StarEngine
    private var tagId = 0

    private val local = (1..30).map { 900_000 + it }
    private var listed: (okhttp3.Request) -> Scripted? = starredPages(local)
    private val network = ScriptedNetwork { request ->
        listed(request) ?: when (request.url.encodedPath) {
            "/user" -> Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds", "Ada Okafor"))
            "/graphql" -> Scripted(200, listsJson(FIXTURE_USER_ID, 30, emptyList()))
            else -> Scripted(404, """{"message":"Not Found"}""")
        }
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
        // Signed in now; no reconcile has finished for this sign-in.
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "", SIGNED_IN_AT))
        tagId = db.tagDao().insertTag(Tag(name = "kept", color = "#FF6600")).toInt()
        seedRepos(db, local, favorite = true, pinned = true, tagId = tagId)
        db.syncMetadataDao().updateMetadata(SyncMetadata(dataType = "starred_repos", isInitialSyncComplete = true))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engine = starEngine(db, network, keys.tokenManager, scope, FakeConnectivity())
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        keys.close()
    }

    @OptIn(ExperimentalPagingApi::class)
    private fun show() {
        val api = apiService(keys.tokenManager, network)
        val connectivity = FakeConnectivity()
        // Built once outside composition, so a recomposition never creates a second view model.
        val home = HomeViewModel(
            savedStateHandle = SavedStateHandle(),
            gitHubRepository = GitHubRepositoryImpl(db.repositoryDao(), StarredReposRemoteMediator(api, db), api),
            organizationRepository = OrganizationRepository(db.repositoryDao(), db.tagDao(), db.repositoryTagDao(), db.searchPresetDao()),
            accessSession = AccessSession(keys.tokenManager),
            accounts = AccountRepository(db.accountDao(), db.gitHubProfileDao(), db.repositoryDao()),
            starListRepository = StarListRepository(db.starListDao()),
            refresher = AccountDataRefresher(api, graphQlApi(keys.tokenManager, network), db) { 1L },
            starEngine = engine,
            searchFactory = HomeGitHubSearch.Factory(GitHubSearch(api, db.repositoryDao()), connectivity, engine),
            removalsFactory = HomeRemovals.Factory(StarReconciler(api, db, engine)),
        )
        check.render {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    HomeScreen(
                        onRepoClick = { _, _ -> },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        viewModel = home
                    )
                }
            }
        }
        waitUntil("Home shows the list") {
            composeRule.onAllNodes(hasText("repo-1")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntil(what: String, timeout: Long = 10_000, condition: () -> Boolean) =
        try {
            composeRule.waitUntil(timeout) { condition() }
        } catch (e: Throwable) {
            throw AssertionError("timed out waiting for: $what", e)
        }

    private fun pullToRefresh() {
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("repo-1"))).performTouchInput { swipeDown() }
    }

    private fun noticeShown() = composeRule.onAllNodes(hasTestTag(REMOVAL_NOTICE_TAG)).fetchSemanticsNodes().isNotEmpty()

    private fun count() = runBlocking { db.repositoryDao().getRepositoryCount() }

    private fun marker() = runBlocking { db.syncMetadataDao().getMetadata(StarReconciler.MARKER)?.lastSyncTimestamp }

    @Test
    fun theFirstReconcileAfterSignInShowsTheCountAndWaitsForContinue() {
        val kept = local.take(7)
        listed = starredPages(kept)
        show()

        pullToRefresh()

        waitUntil("the removal notice") { noticeShown() }
        check.assertShown("REPOS TO REMOVE", "23", "CONTINUE")
        assertTrue(
            "TalkBack reads the count",
            composeRule.onAllNodes(hasContentDescription("23 repos to remove")).fetchSemanticsNodes().isNotEmpty()
        )
        assertEquals("nothing removed before Continue", 30, count())

        // Back does not dismiss it.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
        assertTrue("back leaves the notice", noticeShown())
        assertEquals(30, count())

        composeRule.waitForIdle()
        check.assertTargetsInsideSafeArea()
        check.capture("removal-notice--default")

        composeRule.onNode(hasText("CONTINUE")).performClick()

        waitUntil("the notice closes") { !noticeShown() }
        waitUntil("the 23 repos leave") { count() == 7 }
        runBlocking {
            local.drop(7).forEach { id -> assertNull("repo $id is removed", db.repositoryDao().getRepositoryById(id)) }
            kept.forEach { id ->
                val row = db.repositoryDao().getRepositoryById(id)!!
                assertTrue("repo $id keeps its favorite and pin", row.isFavorite && row.isPinned)
                assertEquals("repo $id keeps its tag", listOf(tagId), db.repositoryTagDao().getTagsForRepository(id).first().map { it.id })
            }
        }
        assertEquals(SIGNED_IN_AT, marker())
        waitUntil("Home drops the removed repos") {
            composeRule.onAllNodes(hasText("repo-8")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aReconcileThatRemovesNothingShowsNoNotice() {
        show()

        pullToRefresh()

        waitUntil("the reconcile finishes") { marker() == SIGNED_IN_AT }
        composeRule.waitForIdle()
        assertTrue("no notice", !noticeShown())
        assertEquals(30, count())
    }

    private companion object {
        const val SIGNED_IN_AT = 1_800_000_000_000L
    }
}
