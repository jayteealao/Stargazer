package uk.adedamola.stargazer.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.GitHubRepository as GitHubRepo
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.SortOption
import javax.inject.Inject

data class RepositoryState(
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val tags: List<Tag> = emptyList()
)

private data class FilterState(
    val query: String,
    val sortBy: SortOption,
    val language: String?,
    val favoritesOnly: Boolean,
    val pinnedOnly: Boolean,
    val tagId: Int?
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val gitHubRepository: GitHubRepo,
    private val organizationRepository: OrganizationRepository
) : ViewModel() {

    companion object {
        private const val SEARCH_QUERY_KEY = "search_query"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    // Raw search query input (immediate updates for UI)
    private val _rawSearchQuery = MutableStateFlow(
        savedStateHandle.get<String>(SEARCH_QUERY_KEY) ?: ""
    )
    val searchQuery: StateFlow<String> = _rawSearchQuery.asStateFlow()

    // Debounced and trimmed search query (used for actual search)
    private val _debouncedSearchQuery: StateFlow<String> = _rawSearchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .map { it.trim() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = savedStateHandle.get<String>(SEARCH_QUERY_KEY)?.trim() ?: ""
        )

    private val _sortOption = MutableStateFlow(SortOption.STARS)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<String?>(null)
    val selectedLanguage: StateFlow<String?> = _selectedLanguage.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    private val _showPinnedOnly = MutableStateFlow(false)
    val showPinnedOnly: StateFlow<Boolean> = _showPinnedOnly.asStateFlow()

    private val _selectedTagId = MutableStateFlow<Int?>(null)
    val selectedTagId: StateFlow<Int?> = _selectedTagId.asStateFlow()

    // All available tags - automatically managed lifecycle with stateIn()
    val allTags: StateFlow<List<Tag>> = organizationRepository.getAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All saved presets - automatically managed lifecycle with stateIn()
    val savedPresets: StateFlow<List<SearchPreset>> = organizationRepository.getAllPresets()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Main paged repository flow that switches based on active filters.
     * Uses flatMapLatest to switch between different paging sources when filters change.
     * Uses debounced search query to avoid excessive database queries.
     */
    val repositories: Flow<PagingData<GitHubRepository>> = combine(
        _debouncedSearchQuery,
        _sortOption,
        _selectedLanguage,
        _showFavoritesOnly,
        _showPinnedOnly,
        _selectedTagId
    ) { flows: Array<Any?> ->
        FilterState(
            query = flows[0] as String,
            sortBy = flows[1] as SortOption,
            language = flows[2] as String?,
            favoritesOnly = flows[3] as Boolean,
            pinnedOnly = flows[4] as Boolean,
            tagId = flows[5] as Int?
        )
    }.flatMapLatest { filterState ->
        when {
            filterState.favoritesOnly -> {
                organizationRepository.getFavoriteRepositoriesPaging()
            }
            filterState.pinnedOnly -> {
                organizationRepository.getPinnedRepositoriesPaging()
            }
            filterState.tagId != null -> {
                organizationRepository.getRepositoriesWithTagPaging(filterState.tagId)
            }
            filterState.query.isNotBlank() -> {
                gitHubRepository.searchRepositoriesPaging(filterState.query)
            }
            filterState.language != null -> {
                gitHubRepository.getRepositoriesByLanguagePaging(filterState.language)
            }
            else -> {
                // Default: show all starred repos with selected sort option
                // Uses RemoteMediator to sync from API on first load
                gitHubRepository.getStarredRepositoriesPaging(filterState.sortBy)
            }
        }
    }.cachedIn(viewModelScope)

    fun refresh() {
        // Trigger refresh by clearing and reloading the paging data
        viewModelScope.launch {
            gitHubRepository.refreshStarredRepositories()
        }
    }

    fun onSearchQueryChange(query: String) {
        _rawSearchQuery.value = query
        savedStateHandle[SEARCH_QUERY_KEY] = query
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun filterByLanguage(language: String?) {
        _selectedLanguage.value = language
    }

    fun toggleFavoritesFilter() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
        if (_showFavoritesOnly.value) {
            _showPinnedOnly.value = false
        }
    }

    fun togglePinnedFilter() {
        _showPinnedOnly.value = !_showPinnedOnly.value
        if (_showPinnedOnly.value) {
            _showFavoritesOnly.value = false
        }
    }

    fun filterByTag(tagId: Int?) {
        _selectedTagId.value = tagId
    }

    fun toggleFavorite(repositoryId: Int, currentState: Boolean) {
        viewModelScope.launch {
            organizationRepository.toggleFavorite(repositoryId, !currentState)
        }
    }

    fun togglePinned(repositoryId: Int, currentState: Boolean) {
        viewModelScope.launch {
            organizationRepository.togglePinned(repositoryId, !currentState)
        }
    }

    fun addTagToRepository(repositoryId: Int, tagId: Int) {
        viewModelScope.launch {
            organizationRepository.addTagToRepository(repositoryId, tagId)
        }
    }

    fun removeTagFromRepository(repositoryId: Int, tagId: Int) {
        viewModelScope.launch {
            organizationRepository.removeTagFromRepository(repositoryId, tagId)
        }
    }

    fun createTag(name: String, color: String) {
        viewModelScope.launch {
            organizationRepository.createTag(name, color)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            organizationRepository.deleteTag(tag)
        }
    }

    fun saveCurrentAsPreset(presetName: String) {
        viewModelScope.launch {
            val preset = SearchPreset(
                name = presetName,
                sortBy = _sortOption.value.name.lowercase(),
                filterLanguage = _selectedLanguage.value,
                filterMinStars = null,
                filterMaxStars = null,
                filterFavoritesOnly = _showFavoritesOnly.value,
                filterPinnedOnly = _showPinnedOnly.value,
                searchQuery = _rawSearchQuery.value.trim().takeIf { it.isNotBlank() }
            )
            organizationRepository.savePreset(preset)
        }
    }

    fun loadPreset(preset: SearchPreset) {
        _sortOption.value = SortOption.valueOf(preset.sortBy.uppercase())
        _selectedLanguage.value = preset.filterLanguage
        _showFavoritesOnly.value = preset.filterFavoritesOnly
        _showPinnedOnly.value = preset.filterPinnedOnly
        val query = preset.searchQuery ?: ""
        _rawSearchQuery.value = query
        savedStateHandle[SEARCH_QUERY_KEY] = query
    }

    fun deletePreset(preset: SearchPreset) {
        viewModelScope.launch {
            organizationRepository.deletePreset(preset)
        }
    }

    fun clearAllFilters() {
        _rawSearchQuery.value = ""
        savedStateHandle[SEARCH_QUERY_KEY] = ""
        _selectedLanguage.value = null
        _showFavoritesOnly.value = false
        _showPinnedOnly.value = false
        _selectedTagId.value = null
        _sortOption.value = SortOption.STARS
    }

    /**
     * Gets the current repository state (favorite, pinned, tags) for a specific repository.
     * This is used by the UI to display the current state of each repository.
     */
    suspend fun getRepositoryState(repositoryId: Int): RepositoryState {
        val entity = organizationRepository.getRepositoryById(repositoryId)
        val tags = organizationRepository.getTagsForRepository(repositoryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
            .value

        return RepositoryState(
            isFavorite = entity?.isFavorite ?: false,
            isPinned = entity?.isPinned ?: false,
            tags = tags
        )
    }
}
