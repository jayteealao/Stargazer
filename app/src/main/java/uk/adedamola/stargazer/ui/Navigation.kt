package uk.adedamola.stargazer.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import uk.adedamola.stargazer.ui.screens.DetailScreen
import uk.adedamola.stargazer.ui.screens.HomeScreen
import uk.adedamola.stargazer.ui.screens.LoginScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.adedamola.stargazer.data.auth.TokenManager
import javax.inject.Inject

// Navigation 3 uses keys (Any) instead of routes (Strings)
sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class Detail(val repoName: String, val repoId: Int) : Screen
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Authenticated : AuthState
    data object Unauthenticated : AuthState
}

@HiltViewModel
class NavigationViewModel @Inject constructor(
    tokenManager: TokenManager
) : ViewModel() {
    val authState: StateFlow<AuthState> = tokenManager.token
        .map { token ->
            if (token != null && token.isNotBlank()) AuthState.Authenticated
            else AuthState.Unauthenticated
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)
}

@Composable
fun StargazerApp(
    navigationViewModel: NavigationViewModel = hiltViewModel()
) {
    val authState by navigationViewModel.authState.collectAsState()

    // Don't render anything while auth state is loading — the native splash screen stays visible
    if (authState == AuthState.Loading) return

    val initialScreen = if (authState == AuthState.Authenticated) Screen.Home else Screen.Login
    val backStack = remember(authState) {
        mutableStateListOf<Any>(initialScreen)
    }

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLastOrNull()
    }

    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Screen.Login> {
                    LoginScreen(
                        onLoginSuccess = {
                            backStack.clear()
                            backStack.add(Screen.Home)
                        }
                    )
                }
                entry<Screen.Home> {
                    HomeScreen(
                        onRepoClick = { repoName, repoId ->
                            backStack.add(Screen.Detail(repoName, repoId))
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    )
                }
                entry<Screen.Detail> { key ->
                    DetailScreen(
                        repoName = key.repoName,
                        repoId = key.repoId,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    )
                }
            }
        )
    }
}
