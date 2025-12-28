package uk.adedamola.stargazer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.mappers.toDomainModel
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.GitHubRepository as GitHubRepo
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.Result
import uk.adedamola.stargazer.data.repository.SortOption
import javax.inject.Inject

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val repositories: List<GitHubRepository>,
        val repositoryStates: Map<Int, RepositoryState> = emptyMap()
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

data class RepositoryState(
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val tags: List<Tag> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepo,
    private val organizationRepository: OrganizationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    init {
        loadRepositories()
    }

    fun loadRepositories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Combine filters
            val favoritesOnly = _showFavoritesOnly.value
            val pinnedOnly = _showPinnedOnly.value
            val language = _selectedLanguage.value
            val tagId = _selectedTagId.value
            val sortBy = _sortOption.value
            val query = _searchQuery.value

            when {
                favoritesOnly -> {
                    organizationRepository.getFavoriteRepositories().collect { repos ->
                        loadRepositoryStates(repos.map { it.toDomainModel() })
                    }
                }
                pinnedOnly -> {
                    organizationRepository.getPinnedRepositories().collect { repos ->
                        loadRepositoryStates(repos.map { it.toDomainModel() })
                    }
                }
                tagId != null -> {
                    organizationRepository.getRepositoriesWithTag(tagId).collect { repos ->
                        loadRepositoryStates(repos.map { it.toDomainModel() })
                    }
                }
                query.isNotBlank() -> {
                    gitHubRepository.searchRepositories(query).collect { result ->
                        when (result) {
                            is Result.Success -> loadRepositoryStates(result.data)
                            is Result.Error -> _uiState.value = HomeUiState.Error(
                                result.exception.message ?: "Search failed"
                            )
                            else -> {}
                        }
                    }
                }
                language != null -> {
                    gitHubRepository.getRepositoriesByLanguage(language).collect { result ->
                        when (result) {
                            is Result.Success -> loadRepositoryStates(result.data)
                            is Result.Error -> _uiState.value = HomeUiState.Error(
                                result.exception.message ?: "Filter failed"
                            )
                            else -> {}
                        }
                    }
                }
                else -> {
                    gitHubRepository.getStarredRepositories(forceRefresh).collect { result ->
                        when (result) {
                            is Result.Loading -> _uiState.value = HomeUiState.Loading
                            is Result.Success -> loadRepositoryStates(result.data)
                            is Result.Error -> _uiState.value = HomeUiState.Error(
                                result.exception.message ?: "Unknown error occurred"
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadRepositoryStates(repositories: List<GitHubRepository>) {
        val states = mutableMapOf<Int, RepositoryState>()
        repositories.forEach { repo ->
            // Use first() to get current value instead of infinite collect()
            val tags = organizationRepository.getTagsForRepository(repo.id).first()
            val entity = organizationRepository.getRepositoryById(repo.id)
            states[repo.id] = RepositoryState(
                tags = tags,
                isFavorite = entity?.isFavorite ?: false,
                isPinned = entity?.isPinned ?: false
            )
        }
        _uiState.value = HomeUiState.Success(repositories, states)
    }

    fun refresh() {
        loadRepositories(forceRefresh = true)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        loadRepositories()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        loadRepositories()
    }

    fun filterByLanguage(language: String?) {
        _selectedLanguage.value = language
        loadRepositories()
    }

    fun toggleFavoritesFilter() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
        if (_showFavoritesOnly.value) {
            _showPinnedOnly.value = false
        }
        loadRepositories()
    }

    fun togglePinnedFilter() {
        _showPinnedOnly.value = !_showPinnedOnly.value
        if (_showPinnedOnly.value) {
            _showFavoritesOnly.value = false
        }
        loadRepositories()
    }

    fun filterByTag(tagId: Int?) {
        _selectedTagId.value = tagId
        loadRepositories()
    }

    fun toggleFavorite(repositoryId: Int, currentState: Boolean) {
        viewModelScope.launch {
            organizationRepository.toggleFavorite(repositoryId, !currentState)
            loadRepositories()
        }
    }

    fun togglePinned(repositoryId: Int, currentState: Boolean) {
        viewModelScope.launch {
            organizationRepository.togglePinned(repositoryId, !currentState)
            loadRepositories()
        }
    }

    fun addTagToRepository(repositoryId: Int, tagId: Int) {
        viewModelScope.launch {
            organizationRepository.addTagToRepository(repositoryId, tagId)
            loadRepositories()
        }
    }

    fun removeTagFromRepository(repositoryId: Int, tagId: Int) {
        viewModelScope.launch {
            organizationRepository.removeTagFromRepository(repositoryId, tagId)
            loadRepositories()
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
                searchQuery = _searchQuery.value.takeIf { it.isNotBlank() }
            )
            organizationRepository.savePreset(preset)
        }
    }

    fun loadPreset(preset: SearchPreset) {
        _sortOption.value = SortOption.valueOf(preset.sortBy.uppercase())
        _selectedLanguage.value = preset.filterLanguage
        _showFavoritesOnly.value = preset.filterFavoritesOnly
        _showPinnedOnly.value = preset.filterPinnedOnly
        _searchQuery.value = preset.searchQuery ?: ""
        loadRepositories()
    }

    fun deletePreset(preset: SearchPreset) {
        viewModelScope.launch {
            organizationRepository.deletePreset(preset)
        }
    }

    fun clearAllFilters() {
        _searchQuery.value = ""
        _selectedLanguage.value = null
        _showFavoritesOnly.value = false
        _showPinnedOnly.value = false
        _selectedTagId.value = null
        _sortOption.value = SortOption.STARS
        loadRepositories()
    }
}
