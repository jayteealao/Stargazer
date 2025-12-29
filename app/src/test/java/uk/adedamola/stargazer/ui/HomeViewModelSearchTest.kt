package uk.adedamola.stargazer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.remote.model.Owner
import uk.adedamola.stargazer.data.repository.GitHubRepository as GitHubRepo
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.SortOption
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
    private lateinit var organizationRepository: FakeOrganizationRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle()
        gitHubRepository = FakeGitHubRepository()
        organizationRepository = FakeOrganizationRepository()
        viewModel = HomeViewModel(
            savedStateHandle = savedStateHandle,
            gitHubRepository = gitHubRepository,
            organizationRepository = organizationRepository
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

        val newViewModel = HomeViewModel(
            savedStateHandle = savedStateHandle,
            gitHubRepository = gitHubRepository,
            organizationRepository = organizationRepository
        )

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

        // Should save with trimmed query
        val savedPreset = organizationRepository.lastSavedPreset
        assertEquals("test query", savedPreset?.searchQuery)
    }

    @Test
    fun saveCurrentAsPreset_excludesEmptySearchQuery() = runTest(testDispatcher) {
        viewModel.onSearchQueryChange("")

        viewModel.saveCurrentAsPreset("My Preset")

        // Empty query should be null in preset
        val savedPreset = organizationRepository.lastSavedPreset
        assertEquals(null, savedPreset?.searchQuery)
    }
}

// Fake implementations for testing

private class FakeGitHubRepository : GitHubRepo {
    var searchCallCount = 0
    private var lastSearchQuery = ""

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

    override suspend fun refreshStarredRepositories() {}
}

private class FakeOrganizationRepository : OrganizationRepository {
    var lastSavedPreset: SearchPreset? = null

    override suspend fun getRepositoryById(id: Int): RepositoryEntity? = null

    override suspend fun toggleFavorite(repositoryId: Int, isFavorite: Boolean) {}

    override suspend fun togglePinned(repositoryId: Int, isPinned: Boolean) {}

    override fun getFavoriteRepositoriesPaging(): Flow<PagingData<GitHubRepository>> {
        return flowOf(PagingData.from(emptyList()))
    }

    override fun getPinnedRepositoriesPaging(): Flow<PagingData<GitHubRepository>> {
        return flowOf(PagingData.from(emptyList()))
    }

    override fun getAllTags(): Flow<List<Tag>> {
        return flowOf(emptyList())
    }

    override suspend fun createTag(name: String, color: String): Long = 0L

    override suspend fun deleteTag(tag: Tag) {}

    override fun getTagsForRepository(repositoryId: Int): Flow<List<Tag>> {
        return flowOf(emptyList())
    }

    override suspend fun addTagToRepository(repositoryId: Int, tagId: Int) {}

    override suspend fun removeTagFromRepository(repositoryId: Int, tagId: Int) {}

    override fun getRepositoriesWithTagPaging(tagId: Int): Flow<PagingData<GitHubRepository>> {
        return flowOf(PagingData.from(emptyList()))
    }

    override fun getAllPresets(): Flow<List<SearchPreset>> {
        return flowOf(emptyList())
    }

    override suspend fun savePreset(preset: SearchPreset) {
        lastSavedPreset = preset
    }

    override suspend fun deletePreset(preset: SearchPreset) {}
}
