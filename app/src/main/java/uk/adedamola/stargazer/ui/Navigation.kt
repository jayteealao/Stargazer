package uk.adedamola.stargazer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.ui.NavDisplay
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
    data class Detail(val repoName: String) : Screen
}

@HiltViewModel
class NavigationViewModel @Inject constructor(
    tokenManager: TokenManager
) : ViewModel() {
    val isAuthenticated: StateFlow<Boolean> = tokenManager.token
        .map { it != null && it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}

@Composable
fun StargazerApp(
    navigationViewModel: NavigationViewModel = hiltViewModel()
) {
    val isAuthenticated by navigationViewModel.isAuthenticated.collectAsState()

    // 1. Create a back stack, specifying the key the app should start with
    val initialScreen = if (isAuthenticated) Screen.Home else Screen.Login
    val backStack = remember(isAuthenticated) {
        mutableStateListOf<Any>(initialScreen)
    }

    // Handle System Back Press
    // If backStack has more than 1 item, pop the last one.
    // Otherwise, allow system back (exit app).
    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLastOrNull()
    }

    // 2. Use NavDisplay to display content based on the back stack
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
                    onRepoClick = { repoName ->
                        backStack.add(Screen.Detail(repoName))
                    }
                )
            }
            entry<Screen.Detail> { key ->
                DetailScreen(repoName = key.repoName)
            }
        }
    )
}
