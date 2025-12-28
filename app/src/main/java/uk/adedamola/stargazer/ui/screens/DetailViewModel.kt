package uk.adedamola.stargazer.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.GitHubRepository as GitHubRepo
import uk.adedamola.stargazer.data.repository.Result
import javax.inject.Inject

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(val repository: GitHubRepository) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepo,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

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
                        _uiState.value = DetailUiState.Success(result.data)
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
