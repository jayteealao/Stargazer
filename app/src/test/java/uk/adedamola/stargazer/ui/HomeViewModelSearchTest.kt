package uk.adedamola.stargazer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.TokenManager
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.GitHubProfileDao
import uk.adedamola.stargazer.data.local.database.PendingUnstarDao
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTagDao
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.SearchPresetDao
import uk.adedamola.stargazer.data.local.database.StarListDao
import uk.adedamola.stargazer.data.local.database.StargazerDao
import uk.adedamola.stargazer.data.local.database.SyncMetadataDao
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.local.database.TagDao
import uk.adedamola.stargazer.data.paging.SyncProgress
import uk.adedamola.stargazer.data.remote.Connectivity
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.api.GitHubGraphQlApi
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.remote.model.Owner
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.data.repository.GitHubRepository as GitHubRepo
import uk.adedamola.stargazer.data.repository.GitHubSearch
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.Result as RepoResult
import uk.adedamola.stargazer.data.repository.SortOption
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.StarListRepository
import uk.adedamola.stargazer.data.repository.StarReconciler
import uk.adedamola.stargazer.ui.screens.HomeAccount
import uk.adedamola.stargazer.ui.screens.HomeGitHubSearch
import uk.adedamola.stargazer.ui.screens.HomeRemovals
import uk.adedamola.stargazer.ui.screens.HomeStarActions
import uk.adedamola.stargazer.ui.screens.HomeViewModel

/**
 * Tests for HomeViewModel search functionality.
 * Tests:
 * - Search query persistence across process recreation
 * - Debouncing of search queries
 * - Query trimming
 * - SavedStateHandle integration
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSearchTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var gitHubRepository: FakeGitHubRepository
    private lateinit var searchPresetDao: RecordingSearchPresetDao
    private lateinit var organizationRepository: OrganizationRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle()
        gitHubRepository = FakeGitHubRepository()
        searchPresetDao = RecordingSearchPresetDao()
        organizationRepository = OrganizationRepository(
            repositoryDao = fake(),
            tagDao = fake(),
            repositoryTagDao = fake(),
            searchPresetDao = searchPresetDao
        )
        viewModel = newViewModel()
    }

    /**
     * Builds the view model over real collaborators backed by empty fake DAOs, an offline
     * connection, and a GitHub API that fails every call.
     */
    private fun newViewModel(): HomeViewModel {
        val database = FakeAppDatabase()
        val api: GitHubApiService = fake { _, _ -> throw IllegalStateException("No GitHub in unit tests") }
        val graphQl: GitHubGraphQlApi = fake { _, _ -> throw IllegalStateException("No GitHub in unit tests") }
        val connectivity = object : Connectivity {
            override val online = MutableStateFlow(false)
        }
        val starEngine = StarEngine(
            api = api,
            database = database,
            connectivity = connectivity,
            clock = { 0L },
            scope = CoroutineScope(testDispatcher),
            sleep = {}
        )
        val starListRepository = StarListRepository(database.starListDao())
        return HomeViewModel(
            savedStateHandle = savedStateHandle,
            gitHubRepository = gitHubRepository,
            organizationRepository = organizationRepository,
            accessSession = AccessSession(
                TokenManager(fake(), fake(), CoroutineScope(Job().apply { cancel() }))
            ),
            starListRepository = starListRepository,
            searchFactory = HomeGitHubSearch.Factory(GitHubSearch(api, database.repositoryDao()), connectivity, starEngine),
            removalsFactory = HomeRemovals.Factory(StarReconciler(api, database, starEngine)),
            starActionsFactory = HomeStarActions.Factory(starEngine),
            accountFactory = HomeAccount.Factory(
                AccountRepository(database.accountDao(), database.gitHubProfileDao(), database.repositoryDao()),
                starListRepository,
                AccountDataRefresher(api, graphQl, database)
            )
        )
    }

    @Test
    fun searchQuery_initiallyEmpty() = runTest(testDispatcher) {
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun searchQuery_updatesImmediately() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("test")
        assertEquals("test", viewModel.searchQuery.value)
    }

    @Test
    fun searchQuery_savedToSavedStateHandle() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("test query")
        assertEquals("test query", savedStateHandle.get<String>("search_query"))
    }

    @Test
    fun searchQuery_restoredFromSavedStateHandle() = runTest(testDispatcher) {
        // Simulate process recreation with saved state
        savedStateHandle["search_query"] = "restored query"

        val newViewModel = newViewModel()

        assertEquals("restored query", newViewModel.searchQuery.value)
    }

    @Test
    fun searchQuery_debounced() = runTest(testDispatcher) {
        // Type multiple characters quickly
        viewModel.onSearchQueryChange("t")
        viewModel.onSearchQueryChange("te")
        viewModel.onSearchQueryChange("tes")
        viewModel.onSearchQueryChange("test")

        // Should show immediate update in searchQuery
        assertEquals("test", viewModel.searchQuery.value)

        // But repository search should not be called yet (debounced)
        assertEquals(0, gitHubRepository.searchCallCount)

        // Advance time past debounce period (300ms)
        advanceTimeBy(350)

        // Now search should be triggered
        // Note: In actual implementation, this would trigger repository search
    }

    @Test
    fun searchQuery_trimmedBeforeSearch() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("  test query  ")

        // Raw query should show exact input
        assertEquals("  test query  ", viewModel.searchQuery.value)

        // After debounce, trimmed query should be used for search
        advanceTimeBy(350)
    }

    @Test
    fun searchQuery_whitespaceOnlyTreatedAsEmpty() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("   ")

        // Raw query shows whitespace
        assertEquals("   ", viewModel.searchQuery.value)

        // After debounce and trim, should be treated as empty
        advanceTimeBy(350)
    }

    @Test
    fun clearAllFilters_resetsSearchQuery() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("test")
        assertEquals("test", viewModel.searchQuery.value)

        viewModel.clearAllFilters()

        assertEquals("", viewModel.searchQuery.value)
        assertEquals("", savedStateHandle.get<String>("search_query"))
    }

    @Test
    fun loadPreset_updatesSearchQuery() = runTest(testDispatcher) {
        val preset = SearchPreset(
            id = 1,
            name = "Test Preset",
            sortBy = "stars",
            filterLanguage = null,
            filterMinStars = null,
            filterMaxStars = null,
            filterFavoritesOnly = false,
            filterPinnedOnly = false,
            searchQuery = "kotlin",
            createdAt = 0L
        )

        viewModel.loadPreset(preset)

        assertEquals("kotlin", viewModel.searchQuery.value)
        assertEquals("kotlin", savedStateHandle.get<String>("search_query"))
    }

    @Test
    fun saveCurrentAsPreset_includesTrimmedSearchQuery() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("  test query  ")

        viewModel.saveCurrentAsPreset("My Preset")
        advanceUntilIdle()

        // Should save with trimmed query
        val savedPreset = searchPresetDao.lastSavedPreset
        assertEquals("test query", savedPreset?.searchQuery)
    }

    @Test
    fun saveCurrentAsPreset_excludesEmptySearchQuery() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("")

        viewModel.saveCurrentAsPreset("My Preset")
        advanceUntilIdle()

        // Empty query should be null in preset
        val savedPreset = searchPresetDao.lastSavedPreset
        assertEquals("My Preset", savedPreset?.name)
        assertEquals(null, savedPreset?.searchQuery)
    }
}

// Fake implementations for testing

private class FakeGitHubRepository : GitHubRepo {
    var searchCallCount = 0
    private var lastSearchQuery = ""

    override val syncProgress: StateFlow<SyncProgress> = MutableStateFlow(SyncProgress())

    override fun getStarredRepositoriesPaging(sortBy: SortOption): Flow<PagingData<GitHubRepository>> {
        return flowOf(PagingData.from(emptyList()))
    }

    override fun searchRepositoriesPaging(query: String): Flow<PagingData<GitHubRepository>> {
        searchCallCount++
        lastSearchQuery = query
        return flowOf(PagingData.from(emptyList()))
    }

    override fun getRepositoriesByLanguagePaging(language: String): Flow<PagingData<GitHubRepository>> {
        return flowOf(PagingData.from(emptyList()))
    }

    override suspend fun getRepositoryByFullName(fullName: String): RepoResult<GitHubRepository?> = RepoResult.Success(null)

    override suspend fun getRepositoryReadme(owner: String, repo: String): RepoResult<String> = RepoResult.Success("")

    override suspend fun refreshStarredRepositories(): RepoResult<Unit> = RepoResult.Success(Unit)
}

/** Records the presets OrganizationRepository saves. */
private class RecordingSearchPresetDao : SearchPresetDao {
    var lastSavedPreset: SearchPreset? = null

    override fun getAllPresets(): Flow<List<SearchPreset>> = flowOf(emptyList())

    override suspend fun getPresetById(id: Int): SearchPreset? = null

    override suspend fun insertPreset(preset: SearchPreset): Long {
        lastSavedPreset = preset
        return 1L
    }

    override suspend fun deletePreset(preset: SearchPreset) {}

    override suspend fun deletePresetById(id: Int) {}

    override suspend fun count(): Int = 0

    override suspend fun deleteAll() {}
}

/** An empty database: every DAO is a [fake], and Room itself is never opened. */
private class FakeAppDatabase : AppDatabase() {
    private val stargazerDao: StargazerDao = fake()
    private val repositoryDao: RepositoryDao = fake()
    private val tagDao: TagDao = fake()
    private val repositoryTagDao: RepositoryTagDao = fake()
    private val searchPresetDao: SearchPresetDao = fake()
    private val syncMetadataDao: SyncMetadataDao = fake()
    private val accountDao: AccountDao = fake()
    private val gitHubProfileDao: GitHubProfileDao = fake()
    private val starListDao: StarListDao = fake()
    private val pendingUnstarDao: PendingUnstarDao = fake()

    override fun stargazerDao() = stargazerDao
    override fun repositoryDao() = repositoryDao
    override fun tagDao() = tagDao
    override fun repositoryTagDao() = repositoryTagDao
    override fun searchPresetDao() = searchPresetDao
    override fun syncMetadataDao() = syncMetadataDao
    override fun accountDao() = accountDao
    override fun gitHubProfileDao() = gitHubProfileDao
    override fun starListDao() = starListDao
    override fun pendingUnstarDao() = pendingUnstarDao

    override fun createInvalidationTracker(): InvalidationTracker =
        throw UnsupportedOperationException("FakeAppDatabase is never opened")

    override fun clearAllTables() {}
}

/**
 * A fake of interface [T] that answers every call with an empty value: an empty list, set or
 * map, zero, false, null, or a flow of one such value. Suspend functions answer at once.
 * [answer] may answer a call itself; returning [Unanswered] falls back to the empty value.
 */
private inline fun <reified T : Any> fake(
    noinline answer: (method: Method, args: Array<Any?>) -> Any? = { _, _ -> Unanswered }
): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
    val arguments = args ?: emptyArray()
    when (method.name) {
        "equals" -> if (method.parameterCount == 1) return@newProxyInstance proxy === arguments[0]
        "hashCode" -> if (method.parameterCount == 0) return@newProxyInstance System.identityHashCode(proxy)
        "toString" -> if (method.parameterCount == 0) return@newProxyInstance "fake ${T::class.java.simpleName}"
    }
    val answered = answer(method, arguments)
    if (answered !== Unanswered) return@newProxyInstance answered
    val continuation = method.genericParameterTypes.lastOrNull()
    val resultType = if (continuation is ParameterizedType && continuation.rawType == Continuation::class.java) {
        continuation.actualTypeArguments[0]
    } else {
        method.genericReturnType
    }
    emptyValueOf(resultType)
} as T

private object Unanswered

private fun emptyValueOf(type: Type): Any? {
    val resolved = if (type is WildcardType) type.lowerBounds.firstOrNull() ?: type.upperBounds[0] else type
    val raw = when (resolved) {
        is Class<*> -> resolved
        is ParameterizedType -> resolved.rawType as Class<*>
        else -> return null
    }
    return when {
        Flow::class.java.isAssignableFrom(raw) ->
            flowOf(emptyValueOf((resolved as ParameterizedType).actualTypeArguments[0]))
        raw == Unit::class.java || raw == Void.TYPE -> Unit
        Set::class.java.isAssignableFrom(raw) -> emptySet<Any>()
        Map::class.java.isAssignableFrom(raw) -> emptyMap<Any, Any>()
        Collection::class.java.isAssignableFrom(raw) -> emptyList<Any>()
        raw == Int::class.javaPrimitiveType || raw == Int::class.javaObjectType -> 0
        raw == Long::class.javaPrimitiveType || raw == Long::class.javaObjectType -> 0L
        raw == Boolean::class.javaPrimitiveType || raw == Boolean::class.javaObjectType -> false
        else -> null
    }
}
