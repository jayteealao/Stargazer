package uk.adedamola.stargazer.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.toRef
import uk.adedamola.stargazer.data.repository.StarControlState
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.UndoOffer
import uk.adedamola.stargazer.data.repository.GitHubRepository as GitHubRepo
import uk.adedamola.stargazer.data.repository.Result
import javax.inject.Inject

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(
        val repository: GitHubRepository,
        val readme: String? = null
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepo,
    savedStateHandle: SavedStateHandle,
    private val starEngine: StarEngine
) : ViewModel() {

    private val starStates = mutableMapOf<Int, StateFlow<StarControlState>>()

    /** The latest unstar's Undo offer, from any screen. */
    val undoOffer: StateFlow<UndoOffer?> = starEngine.undoOffer

    /** The GitHub star control of [repoId]: starred, in flight, disabled, and its status line. */
    fun starState(repoId: Int): StateFlow<StarControlState> = starStates.getOrPut(repoId) {
        starEngine.controlState(repoId).stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            StarControlState(starred = true, inFlight = false, enabled = false, status = null, rateLimitedUntil = null)
        )
    }

    /** Unstars a starred repo (with Undo) or stars it; refused taps change nothing. */
    fun toggleStar(repo: GitHubRepository) {
        val ref = repo.toRef()
        // A refused tap (offline, rate limited) shows in the control's status line, which reads it
        // from the engine; Home has no such line, so it reports refusals as snackbar messages.
        viewModelScope.launch {
            if (starState(repo.id).value.starred) starEngine.unstar(ref) else starEngine.star(ref, repo)
        }
    }

    fun undo(repoId: Int) {
        viewModelScope.launch { starEngine.undo(repoId) }
    }

    /** Tries the failed action again: the control shows the repo's current state. */
    fun retry(repo: GitHubRepository) = toggleStar(repo)

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        // In Navigation 3, we need to get the parameter differently
        // For now, we'll handle it in the screen itself
    }

    fun loadRepository(fullName: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            when (val result = gitHubRepository.getRepositoryByFullName(fullName)) {
                is Result.Success -> {
                    if (result.data != null) {
                        val repo = result.data
                        // Fetch README
                        val readme = try {
                            val parts = fullName.split("/")
                            if (parts.size == 2) {
                                when (val readmeResult = gitHubRepository.getRepositoryReadme(parts[0], parts[1])) {
                                    is Result.Success -> readmeResult.data
                                    else -> null
                                }
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                        _uiState.value = DetailUiState.Success(repo, readme)
                    } else {
                        _uiState.value = DetailUiState.Error("Repository not found")
                    }
                }
                is Result.Error -> {
                    _uiState.value = DetailUiState.Error(
                        result.exception.message ?: "Failed to load repository"
                    )
                }
                else -> {}
            }
        }
    }
}
