package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ends the signed-in session. Sign-out and a key that GitHub rejects both delete only the key:
 * the account record and every local table stay.
 */
@Singleton
class AccessSession @Inject constructor(
    private val tokenManager: TokenManager
) {
    private val _accessEnded = MutableStateFlow(false)

    /** True after GitHub rejected the stored key, until the person starts a new sign-in. */
    val accessEnded: StateFlow<Boolean> = _accessEnded.asStateFlow()

    suspend fun signOut() {
        _accessEnded.value = false
        tokenManager.clearToken()
    }

    /**
     * GitHub answered 401 to a request sent with [keyUsed]. Deletes that key once, however many
     * requests fail together; a 401 for an older key leaves a newer key alone. Called on OkHttp
     * threads only.
     */
    fun onUnauthorized(keyUsed: String) {
        runBlocking {
            if (tokenManager.clearTokenIf(keyUsed)) _accessEnded.value = true
        }
    }

    fun acknowledgeAccessEnded() {
        _accessEnded.value = false
    }
}
