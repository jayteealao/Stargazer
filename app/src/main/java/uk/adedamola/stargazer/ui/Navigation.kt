package uk.adedamola.stargazer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.compose.BackHandler
import androidx.navigation3.NavDisplay
import androidx.navigation3.NavEntry
import androidx.navigation3.entryProvider
import uk.adedamola.stargazer.ui.screens.DetailScreen
import uk.adedamola.stargazer.ui.screens.HomeScreen

// Navigation 3 uses keys (Any) instead of routes (Strings)
sealed interface Screen {
    data object Home : Screen
    data class Detail(val repoName: String) : Screen
}

@Composable
fun StargazerApp() {
    // 1. Create a back stack, specifying the key the app should start with
    val backStack = remember { mutableStateListOf<Any>(Screen.Home) }

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
