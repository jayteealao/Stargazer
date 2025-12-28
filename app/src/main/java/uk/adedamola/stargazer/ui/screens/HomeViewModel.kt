package uk.adedamola.stargazer.ui.screens

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

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val repositories: List<GitHubRepository>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<String?>(null)
    val selectedLanguage: StateFlow<String?> = _selectedLanguage.asStateFlow()

    init {
        loadRepositories()
    }

    fun loadRepositories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            gitHubRepository.getStarredRepositories(forceRefresh).collect { result ->
                _uiState.value = when (result) {
                    is Result.Loading -> HomeUiState.Loading
                    is Result.Success -> HomeUiState.Success(result.data)
                    is Result.Error -> HomeUiState.Error(
                        result.exception.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    fun refresh() {
        loadRepositories(forceRefresh = true)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            viewModelScope.launch {
                gitHubRepository.searchRepositories(query).collect { result ->
                    _uiState.value = when (result) {
                        is Result.Success -> HomeUiState.Success(result.data)
                        is Result.Error -> HomeUiState.Error(
                            result.exception.message ?: "Search failed"
                        )
                        else -> _uiState.value
                    }
                }
            }
        } else {
            loadRepositories()
        }
    }

    fun filterByLanguage(language: String?) {
        _selectedLanguage.value = language
        if (language != null) {
            viewModelScope.launch {
                gitHubRepository.getRepositoriesByLanguage(language).collect { result ->
                    _uiState.value = when (result) {
                        is Result.Success -> HomeUiState.Success(result.data)
                        is Result.Error -> HomeUiState.Error(
                            result.exception.message ?: "Filter failed"
                        )
                        else -> _uiState.value
                    }
                }
            }
        } else {
            loadRepositories()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        loadRepositories()
    }
}
