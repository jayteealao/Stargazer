package uk.adedamola.stargazer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.auth.TokenManager
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onTokenChange(token: String) {
        _uiState.value = _uiState.value.copy(token = token.trim(), error = null)
    }

    private fun isValidGitHubToken(token: String): Boolean {
        // GitHub tokens follow specific formats:
        // - Classic tokens: ghp_... (40 chars total)
        // - Fine-grained PATs: github_pat_...
        // - OAuth tokens: gho_...
        // - Minimum length should be reasonable
        return token.length >= 40 && (
            token.startsWith("ghp_") ||
            token.startsWith("github_pat_") ||
            token.startsWith("gho_") ||
            token.startsWith("ghs_") ||
            token.startsWith("ghu_")
        )
    }

    fun login(onSuccess: () -> Unit) {
        val token = _uiState.value.token.trim()

        when {
            token.isBlank() -> {
                _uiState.value = _uiState.value.copy(error = "Token cannot be empty")
                return
            }
            !isValidGitHubToken(token) -> {
                _uiState.value = _uiState.value.copy(
                    error = "Invalid GitHub token format. Token should start with ghp_, github_pat_, gho_, ghs_, or ghu_"
                )
                return
            }
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                tokenManager.saveToken(token)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to save token: ${e.message}"
                )
            }
        }
    }
}

data class LoginUiState(
    val token: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "STARGAZER_SYSTEM",
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                color = FactoryOrange,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "v1.0.0 // AUTHENTICATION_REQUIRED",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Instructions
            Text(
                text = "ENTER GITHUB PERSONAL ACCESS TOKEN",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Generate token at: github.com/settings/tokens\nRequired scopes: repo, user",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Token input
            OutlinedTextField(
                value = uiState.token,
                onValueChange = { viewModel.onTokenChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TOKEN", fontFamily = FontFamily.Monospace) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        viewModel.login(onLoginSuccess)
                    }
                ),
                singleLine = true,
                enabled = !uiState.isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FactoryOrange,
                    focusedLabelColor = FactoryOrange
                )
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ERROR: ${uiState.error}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Login button
            Button(
                onClick = { viewModel.login(onLoginSuccess) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FactoryOrange
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "AUTHENTICATE >>",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
