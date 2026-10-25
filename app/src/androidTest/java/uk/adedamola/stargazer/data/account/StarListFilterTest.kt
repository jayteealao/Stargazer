package uk.adedamola.stargazer.data.account

import androidx.lifecycle.SavedStateHandle
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingSource
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.FakeConnectivity
import uk.adedamola.stargazer.data.auth.FakeList
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.fixtureRepo
import uk.adedamola.stargazer.data.auth.graphQlApi
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.listsJson
import uk.adedamola.stargazer.data.auth.profileJson
import uk.adedamola.stargazer.data.auth.starEngine
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.local.database.StarListItemEntity
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.GitHubRepositoryImpl
import uk.adedamola.stargazer.data.repository.GitHubSearch
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.StarListRepository
import uk.adedamola.stargazer.ui.screens.HomeViewModel

/**
 * A selected star list filters Home to its repos on this phone, and clearing the filter shows
 * every starred repo again (AC-27). A list entry that is not starred here is never added.
 */
@RunWith(AndroidJUnit4::class)
class StarListFilterTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var network: ScriptedNetwork
    private val notLocal = 999_999L
    private val inList = listOf(900_001L, 900_002L, 900_003L)

    /** GitHub's lists, as the next refresh will answer them. */
    private var remoteLists = listOf(FakeList("L1", "Android libraries", inList + notLocal), FakeList("L2", "Rust tools", listOf(900_004L)))

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "", 1L))
        @Suppress("DEPRECATION") // sdlc-debt: test seed uses the deprecated insert-or-replace DAO call (ceiling: test-only; upgrade path: an upsert helper if the DAO drops it)
        db.repositoryDao().insertRepositories((1..5).map(::fixtureRepo))
        network = ScriptedNetwork { request ->
            when (request.url.encodedPath) {
                "/user" -> Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds", "Ada Okafor"))
                "/graphql" -> Scripted(200, listsJson(FIXTURE_USER_ID, 5, remoteLists))
                else -> Scripted(200, "[]")
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
        keys.close()
    }

    @Test
    fun theListQueryReturnsOnlyLocalReposOfThatList() = runBlocking<Unit> {
        db.starListDao().replaceAll(
            listOf(StarListEntity("L1", "Android libraries", null, false, 4, 0, 1L)),
            (inList + notLocal).map { StarListItemEntity("L1", it) }
        )
        val page = db.starListDao().repositoriesInListPaging("L1")
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false))
            as PagingSource.LoadResult.Page

        assertEquals(inList.map { it.toInt() }.toSet(), page.data.map { it.id }.toSet())
        assertEquals("nothing is added", 5, db.repositoryDao().getRepositoryCount())
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeFiltersByListClearsBackToAllAndDropsARemovedList() = runBlocking<Unit> {
        val api = apiService(keys.tokenManager, network)
        val refresher = AccountDataRefresher(api, graphQlApi(keys.tokenManager, network), db) { 1L }
        val connectivity = FakeConnectivity()
        val starScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        val viewModel = withContext(Dispatchers.Main) {
            HomeViewModel(
                savedStateHandle = SavedStateHandle(),
                gitHubRepository = GitHubRepositoryImpl(db.repositoryDao(), StarredReposRemoteMediator(api, db), api),
                organizationRepository = OrganizationRepository(db.repositoryDao(), db.tagDao(), db.repositoryTagDao(), db.searchPresetDao()),
                accessSession = AccessSession(keys.tokenManager),
                accountDao = db.accountDao(),
                starListRepository = StarListRepository(db.starListDao()),
                refresher = refresher,
                profileDao = db.gitHubProfileDao(),
                starEngine = starEngine(db, network, keys.tokenManager, starScope, connectivity),
                gitHubSearch = GitHubSearch(api, db.repositoryDao()),
                connectivity = connectivity,
            )
        }
        val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<GitHubRepository>() {
                override fun areItemsTheSame(oldItem: GitHubRepository, newItem: GitHubRepository) = oldItem.id == newItem.id
                override fun areContentsTheSame(oldItem: GitHubRepository, newItem: GitHubRepository) = oldItem == newItem
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {}
                override fun onRemoved(position: Int, count: Int) {}
                override fun onMoved(fromPosition: Int, toPosition: Int) {}
                override fun onChanged(position: Int, count: Int, payload: Any?) {}
            },
            mainDispatcher = Dispatchers.Main,
            workerDispatcher = Dispatchers.Default,
        )
        val collector = launch(Dispatchers.Main) { viewModel.repositories.collectLatest { differ.submitData(it) } }

        suspend fun shownIds(expected: Set<Int>): Set<Int> {
            var ids = emptySet<Int>()
            runCatching {
                withTimeout(5_000) {
                    while (true) {
                        ids = withContext(Dispatchers.Main) { differ.snapshot().items.map { it.id }.toSet() }
                        if (ids == expected) break
                        delay(50)
                    }
                }
            }
            return ids
        }

        // Home load refreshed the lists from GitHub.
        withTimeout(5_000) { while (db.starListDao().getLists().size != 2) delay(50) }

        withContext(Dispatchers.Main) { viewModel.filterByList("L1") }
        assertEquals(inList.map { it.toInt() }.toSet(), shownIds(inList.map { it.toInt() }.toSet()))

        withContext(Dispatchers.Main) { viewModel.clearAllFilters() }
        assertNull(viewModel.selectedListId.value)
        val all = (1..5).map { 900_000 + it }.toSet()
        assertEquals(all, shownIds(all))

        // GitHub no longer has the selected list: the selection goes with it.
        withContext(Dispatchers.Main) { viewModel.filterByList("L1") }
        remoteLists = remoteLists.drop(1)
        refresher.refresh()
        withTimeout(5_000) { while (viewModel.selectedListId.value != null) delay(50) }
        assertNull(viewModel.selectedListId.value)

        collector.cancel()
        starScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
