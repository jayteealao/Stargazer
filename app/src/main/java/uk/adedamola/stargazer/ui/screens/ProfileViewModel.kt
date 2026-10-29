package uk.adedamola.stargazer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.data.repository.RefreshStatus
import javax.inject.Inject

/** What the profile screen shows. */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    /** Nothing cached yet, and GitHub did not answer. */
    data object Error : ProfileUiState

    /**
     * A cached profile. [starCount] is GitHub's count, or the starred repos on this phone until
     * GitHub has answered. [offline] is true when the last refresh never reached GitHub.
     */
    data class Loaded(
        val profile: GitHubProfileEntity,
        val starCount: Int,
        val offline: Boolean,
        val refreshing: Boolean
    ) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val refresher: AccountDataRefresher
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        accounts.observeProfile(),
        refresher.profileStatus,
        accounts.observeLocalStarCount()
    ) { cached, status, localCount ->
        when {
            cached != null -> ProfileUiState.Loaded(
                profile = cached,
                starCount = cached.starredCount ?: localCount,
                offline = status is RefreshStatus.Failed && status.offline,
                refreshing = status is RefreshStatus.Refreshing
            )
            status is RefreshStatus.Failed || status is RefreshStatus.Succeeded -> ProfileUiState.Error
            else -> ProfileUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)

    init {
        // Home refreshes on load; only a screen with nothing to show starts its own refresh.
        viewModelScope.launch {
            if (!accounts.hasCachedProfile() && refresher.profileStatus.value == RefreshStatus.Idle) refresher.refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { refresher.refresh() }
    }
}
