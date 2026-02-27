package uk.adedamola.stargazer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import uk.adedamola.stargazer.ui.theme.StargazerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val navigationViewModel: NavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            navigationViewModel.authState.value is AuthState.Loading
        }

        setContent {
            StargazerTheme {
                StargazerApp(navigationViewModel = navigationViewModel)
            }
        }
    }
}
