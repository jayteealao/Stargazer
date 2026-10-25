package uk.adedamola.stargazer.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.paging.SyncProgress
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.data.repository.Attempt
import uk.adedamola.stargazer.data.repository.RepoRef
import uk.adedamola.stargazer.data.repository.StarControlState
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.StarMessage
import uk.adedamola.stargazer.data.repository.UndoOffer
import uk.adedamola.stargazer.data.repository.RefreshStatus
import uk.adedamola.stargazer.data.repository.StarListRepository
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
    val minStars: Int?,
    val maxStars: Int?,
    val favoritesOnly: Boolean,
    val pinnedOnly: Boolean,
    val tagId: Int?,
    val listId: String?
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val gitHubRepository: GitHubRepo,
    private val organizationRepository: OrganizationRepository,
    private val accessSession: AccessSession,
    accounts: AccountRepository,
    private val starListRepository: StarListRepository,
    private val refresher: AccountDataRefresher,
    private val starEngine: StarEngine,
    searchFactory: HomeGitHubSearch.Factory,
    removalsFactory: HomeRemovals.Factory,
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

    private val _sortOption = MutableStateFlow(SortOption.STARRED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // Sync progress from the repository
    val syncProgress: StateFlow<SyncProgress> = gitHubRepository.syncProgress

    private val _selectedLanguage = MutableStateFlow<String?>(null)
    val selectedLanguage: StateFlow<String?> = _selectedLanguage.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    private val _showPinnedOnly = MutableStateFlow(false)
    val showPinnedOnly: StateFlow<Boolean> = _showPinnedOnly.asStateFlow()

    private val _selectedTagId = MutableStateFlow<Int?>(null)
    val selectedTagId: StateFlow<Int?> = _selectedTagId.asStateFlow()

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    /** The account's GitHub star lists, as last cached (read-only). */
    val starLists: StateFlow<List<StarListEntity>> = starListRepository.observeLists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val listsStatus: StateFlow<RefreshStatus> = refresher.listsStatus

    /**
     * The signed-in account for the drawer header: the cached profile, else the account record
     * (login and avatar), so the header shows right after sign-in.
     */
    val account: StateFlow<AccountSummary?> = accounts.observeAccount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _minStars = MutableStateFlow<Int?>(null)
    val minStars: StateFlow<Int?> = _minStars.asStateFlow()

    private val _maxStars = MutableStateFlow<Int?>(null)
    val maxStars: StateFlow<Int?> = _maxStars.asStateFlow()

    // All available tags - automatically managed lifecycle with stateIn()
    val allTags: StateFlow<List<Tag>> = organizationRepository.getAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All available languages - automatically managed lifecycle with stateIn()
    val availableLanguages: StateFlow<List<String>> = organizationRepository.getDistinctLanguages()
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
        _minStars,
        _maxStars,
        _showFavoritesOnly,
        _showPinnedOnly,
        _selectedTagId,
        _selectedListId
    ) { flows: Array<Any?> ->
        FilterState(
            query = flows[0] as String,
            sortBy = flows[1] as SortOption,
            language = flows[2] as String?,
            minStars = flows[3] as Int?,
            maxStars = flows[4] as Int?,
            favoritesOnly = flows[5] as Boolean,
            pinnedOnly = flows[6] as Boolean,
            tagId = flows[7] as Int?,
            listId = flows[8] as String?
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
            filterState.listId != null -> {
                starListRepository.repositoriesInListPaging(filterState.listId)
            }
            filterState.query.isNotBlank() -> {
                gitHubRepository.searchRepositoriesPaging(filterState.query)
            }
            filterState.language != null -> {
                gitHubRepository.getRepositoriesByLanguagePaging(filterState.language)
            }
            filterState.minStars != null && filterState.maxStars != null -> {
                organizationRepository.getRepositoriesByStarRangePaging(filterState.minStars, filterState.maxStars)
            }
            else -> {
                // Default: show all starred repos with selected sort option
                // Uses RemoteMediator to sync from API on first load
                gitHubRepository.getStarredRepositoriesPaging(filterState.sortBy)
            }
        }
    }.cachedIn(viewModelScope)
        // A repo whose unstar is pending leaves every list at once, but keeps its data until
        // GitHub confirms and the Undo window ends.
        .combine(starEngine.hiddenIds) { data, hidden -> data.filter { it.id !in hidden } }

    private val search = searchFactory.create(_rawSearchQuery, viewModelScope)

    /** GitHub-wide search for the query, under the local matches. */
    val gitHubSearchState: StateFlow<GitHubSearchState> = search.state

    /** The star control of each GitHub result on screen. */
    val searchControlStates: StateFlow<Map<Int, StarControlState>> = search.controlStates

    /** The latest unstar's Undo offer. */
    val undoOffer: StateFlow<UndoOffer?> = starEngine.undoOffer

    private val refusals = MutableSharedFlow<StarMessage>(extraBufferCapacity = 8)

    /** Results the person is told about: GitHub's answers and taps refused here. */
    val starMessages: Flow<StarMessage> = merge(starEngine.messages, refusals)

    /** Unstars a Home row (the swipe or its accessibility action). Refused: the row stays. */
    suspend fun unstarFromSwipe(repo: GitHubRepository): Attempt =
        starEngine.unstar(RepoRef(repo.id, repo.owner.login, repo.name)).also(::reportRefusal)

    /** Stars or unstars a GitHub search result. */
    fun toggleSearchStar(repo: GitHubRepository) {
        viewModelScope.launch {
            val ref = RepoRef(repo.id, repo.owner.login, repo.name)
            reportRefusal(if (search.isStarred(repo.id)) starEngine.unstar(ref) else starEngine.star(ref, repo))
        }
    }

    fun undo(repoId: Int) {
        viewModelScope.launch { reportRefusal(starEngine.undo(repoId)) }
    }

    /** Tries a failed star or unstar again. */
    fun retry(message: StarMessage) {
        viewModelScope.launch {
            when (message) {
                is StarMessage.UnstarFailed -> reportRefusal(starEngine.unstar(message.ref))
                // When the result has left the screen, the engine reads the repository from GitHub.
                is StarMessage.StarFailed -> reportRefusal(starEngine.star(message.ref, search.resultFor(message.ref.id)))
                else -> Unit
            }
        }
    }

    private fun reportRefusal(attempt: Attempt) {
        when (attempt) {
            Attempt.Offline -> refusals.tryEmit(StarMessage.ConnectionNeeded(null))
            is Attempt.RateLimited -> refusals.tryEmit(StarMessage.RateLimited(null, attempt.until))
            else -> Unit
        }
    }

    private val removals = removalsFactory.create(viewModelScope)

    /** True while a pull-to-refresh reads GitHub's whole starred list. */
    val reconciling: StateFlow<Boolean> = removals.reconciling

    /** How many repos the first reconcile after a sign-in would remove; null when nothing waits. */
    val removalNotice: StateFlow<Int?> = removals.notice

    fun refresh() {
        // Trigger refresh by clearing and reloading the paging data
        viewModelScope.launch {
            gitHubRepository.refreshStarredRepositories()
        }
        refreshAccountData()
        resumePendingUnstars()
        // Pull-to-refresh only: app start and background sync never remove.
        removals.reconcile()
    }

    /** Continue on the removal notice: the repos it counted leave, with their local data. */
    fun confirmRemoval() = removals.confirm()

    /** Settles unstars left open by a process that was killed during the Undo window. */
    private fun resumePendingUnstars() {
        viewModelScope.launch { starEngine.resumePending() }
    }

    /** Refreshes the profile and the star lists on their own, so the starred list never waits. */
    fun refreshAccountData() {
        viewModelScope.launch { refresher.refresh() }
    }

    /** Filters Home to one GitHub star list; null shows every starred repo. */
    fun filterByList(listId: String?) {
        _selectedListId.value = listId
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

    fun setStarRange(minStars: Int?, maxStars: Int?) {
        _minStars.value = minStars
        _maxStars.value = maxStars
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
                filterMinStars = _minStars.value,
                filterMaxStars = _maxStars.value,
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
        _minStars.value = preset.filterMinStars
        _maxStars.value = preset.filterMaxStars
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

    init {
        // This view model outlives a sign-out. When another account's data replaces the old one,
        // a selected tag, preset or language of the old account must not filter the new list.
        viewModelScope.launch {
            accounts.observeUserId().drop(1).collect {
                removals.drop()
                clearAllFilters()
                refreshAccountData()
            }
        }
        // A list that a refresh removed can no longer filter Home.
        viewModelScope.launch {
            starListRepository.observeLists().collect { lists ->
                val selected = _selectedListId.value
                if (selected != null && lists.none { it.id == selected }) _selectedListId.value = null
            }
        }
        // Home loads: bring the profile and the lists up to date.
        refreshAccountData()
        resumePendingUnstars()
    }

    fun clearAllFilters() {
        _rawSearchQuery.value = ""
        savedStateHandle[SEARCH_QUERY_KEY] = ""
        _selectedLanguage.value = null
        _minStars.value = null
        _maxStars.value = null
        _showFavoritesOnly.value = false
        _showPinnedOnly.value = false
        _selectedTagId.value = null
        _selectedListId.value = null
        _sortOption.value = SortOption.STARRED
    }

    fun logout() {
        viewModelScope.launch {
            accessSession.signOut()
        }
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
