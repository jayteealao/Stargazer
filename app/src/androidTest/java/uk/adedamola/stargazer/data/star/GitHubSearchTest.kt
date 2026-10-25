package uk.adedamola.stargazer.data.star

import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.ui.screens.HomeGitHubSearch
import uk.adedamola.stargazer.ui.screens.HomeRemovals
import androidx.lifecycle.SavedStateHandle
import androidx.paging.ExperimentalPagingApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.FakeConnectivity
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.eventually
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
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.remote.GitHubErrorText
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.GitHubRepositoryImpl
import uk.adedamola.stargazer.data.repository.GitHubSearch
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.StarListRepository
import uk.adedamola.stargazer.data.repository.StarReconciler
import uk.adedamola.stargazer.ui.screens.GitHubSearchState
import uk.adedamola.stargazer.ui.screens.HomeViewModel

/**
 * GitHub-wide search: repos already starred here are left out, the request asks for one page of
 * 30, a rate limit names its reset time, and Home searches GitHub once per pause in typing, for
 * two or more characters, and never offline.
 */
@RunWith(AndroidJUnit4::class)
class GitHubSearchTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private var onSearch: () -> Scripted = {
        Scripted(200, searchJson(2814, listOf(
            repoJson(900_001, "fixture", "repo-1"),
            repoJson(4001, "JetBrains", "kotlin"),
            repoJson(4002, "square", "retrofit"),
            repoJson(4003, "Kotlin", "kotlinx.coroutines"),
        )))
    }
    private val network = ScriptedNetwork { request ->
        when (request.url.encodedPath) {
            "/search/repositories" -> onSearch()
            "/user" -> Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds"))
            "/graphql" -> Scripted(200, listsJson(FIXTURE_USER_ID, 1, emptyList()))
            else -> Scripted(200, "[]")
        }
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "", 1L))
        @Suppress("DEPRECATION") // sdlc-debt: test seed uses the deprecated insert-or-replace DAO call (ceiling: test-only; upgrade path: an upsert helper if the DAO drops it)
        db.repositoryDao().insertRepositories(listOf(fixtureRepo(1)))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        keys.close()
    }

    private fun searches() = network.requests.filter { it.url.encodedPath == "/search/repositories" }

    @Test
    fun leavesOutLocalReposAndAsksForOnePage() = runBlocking<Unit> {
        val page = GitHubSearch(apiService(keys.tokenManager, network), db.repositoryDao()).search("kotlin")

        assertEquals(2814, page.total)
        assertEquals(listOf(4001, 4002, 4003), page.items.map { it.id })
        val url = searches().single().url
        assertEquals("kotlin", url.queryParameter("q"))
        assertEquals("30", url.queryParameter("per_page"))
        assertEquals("1", url.queryParameter("page"))
    }

    @Test
    fun rateLimitNamesTheResetTime() = runBlocking<Unit> {
        onSearch = { rateLimited(403, retryAfterSeconds = 60) }
        try {
            GitHubSearch(apiService(keys.tokenManager, network), db.repositoryDao()).search("kotlin")
            fail("expected a rate limit")
        } catch (e: HttpException) {
            assertTrue(GitHubErrorText.describe(e), GitHubErrorText.describe(e).startsWith("RATE_LIMITED — RETRY AT "))
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    private suspend fun homeViewModel(connectivity: FakeConnectivity): HomeViewModel {
        val api = apiService(keys.tokenManager, network)
        val engine = starEngine(db, network, keys.tokenManager, scope, connectivity)
        return withContext(Dispatchers.Main) {
            HomeViewModel(
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
        }
    }

    @Test
    fun homeSearchesOncePerPauseForTwoOrMoreCharacters() = runBlocking<Unit> {
        val viewModel = homeViewModel(FakeConnectivity())

        for (q in listOf("k", "ko", "kot", "kotl")) {
            withContext(Dispatchers.Main) { viewModel.onSearchQueryChange(q) }
            delay(100)
        }
        eventually("results shown") { viewModel.gitHubSearchState.value is GitHubSearchState.Loaded }
        delay(800)
        assertEquals("one request per pause", listOf("kotl"), searches().map { it.url.queryParameter("q") })
        val loaded = viewModel.gitHubSearchState.value as GitHubSearchState.Loaded
        assertEquals(2814, loaded.total)
        assertEquals(3, loaded.items.size)

        withContext(Dispatchers.Main) { viewModel.onSearchQueryChange("k") }
        eventually("idle for one character") { viewModel.gitHubSearchState.value == GitHubSearchState.Idle }
        delay(800)
        assertEquals("no request for one character", 1, searches().size)
    }

    @Test
    fun homeDoesNotSearchGitHubOffline() = runBlocking<Unit> {
        val viewModel = homeViewModel(FakeConnectivity(online = false))

        withContext(Dispatchers.Main) { viewModel.onSearchQueryChange("kotlin") }

        eventually("offline state") { viewModel.gitHubSearchState.value == GitHubSearchState.Offline }
        delay(300)
        assertEquals(0, searches().size)
    }
}
