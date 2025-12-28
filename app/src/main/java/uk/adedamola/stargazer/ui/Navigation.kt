package uk.adedamola.stargazer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uk.adedamola.stargazer.ui.screens.DetailScreen
import uk.adedamola.stargazer.ui.screens.HomeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Detail : Screen("detail/{repoName}") {
        fun createRoute(repoName: String) = "detail/$repoName"
    }
}

@Composable
fun StargazerApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onRepoClick = { repoName ->
                    navController.navigate(Screen.Detail.createRoute(repoName))
                }
            )
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("repoName") { type = NavType.StringType })
        ) { backStackEntry ->
            val repoName = backStackEntry.arguments?.getString("repoName")
            DetailScreen(repoName = repoName)
        }
    }
}
