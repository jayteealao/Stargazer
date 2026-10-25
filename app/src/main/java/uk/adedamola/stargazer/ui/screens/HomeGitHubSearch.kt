package uk.adedamola.stargazer.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import uk.adedamola.stargazer.data.remote.Connectivity
import uk.adedamola.stargazer.data.remote.GitHubErrorText
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.GitHubSearch
import uk.adedamola.stargazer.data.repository.StarControlState
import uk.adedamola.stargazer.data.repository.StarEngine
import javax.inject.Inject

/** GitHub-wide results for the search query, shown under `ON_GITHUB`. */
sealed interface GitHubSearchState {
    data object Idle : GitHubSearchState
    data object Loading : GitHubSearchState
    data class Loaded(val total: Int, val items: List<GitHubRepository>) : GitHubSearchState
    data object Offline : GitHubSearchState
    data class Failed(val text: String) : GitHubSearchState
}

/**
 * Home's GitHub-wide search: debounced, two or more characters, online only, the latest query
 * wins. Each result carries the state of its star control.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeGitHubSearch(
    queries: Flow<String>,
    scope: CoroutineScope,
    gitHubSearch: GitHubSearch,
    connectivity: Connectivity,
    starEngine: StarEngine,
) {
    val state: StateFlow<GitHubSearchState> = queries
        .debounce(DEBOUNCE_MS)
        .map { it.trim() }
        .distinctUntilChanged()
        .transformLatest { query ->
            when {
                query.length < MIN_LENGTH -> emit(GitHubSearchState.Idle)
                !connectivity.isOnline() -> emit(GitHubSearchState.Offline)
                else -> {
                    emit(GitHubSearchState.Loading)
                    val result = runCatching { gitHubSearch.search(query) }
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    emit(
                        result.fold(
                            onSuccess = { GitHubSearchState.Loaded(it.total, it.items) },
                            onFailure = { GitHubSearchState.Failed(GitHubErrorText.describe(it)) }
                        )
                    )
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, GitHubSearchState.Idle)

    /** The star control of each result on screen. */
    val controlStates: StateFlow<Map<Int, StarControlState>> = state
        .flatMapLatest { state ->
            val items = (state as? GitHubSearchState.Loaded)?.items.orEmpty()
            if (items.isEmpty()) flowOf(emptyMap())
            else combine(items.map { repo -> starEngine.controlState(repo.id).map { repo.id to it } }) { it.toMap() }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The result with [id] on screen now, or null. */
    fun resultFor(id: Int): GitHubRepository? =
        (state.value as? GitHubSearchState.Loaded)?.items?.find { it.id == id }

    fun isStarred(id: Int): Boolean = controlStates.value[id]?.starred == true

    class Factory @Inject constructor(
        private val gitHubSearch: GitHubSearch,
        private val connectivity: Connectivity,
        private val starEngine: StarEngine,
    ) {
        fun create(queries: Flow<String>, scope: CoroutineScope) =
            HomeGitHubSearch(queries, scope, gitHubSearch, connectivity, starEngine)
    }

    companion object {
        /** GitHub search spends rate-limit points: wait longer, and only for 2+ characters. */
        const val DEBOUNCE_MS = 600L
        const val MIN_LENGTH = 2
    }
}
