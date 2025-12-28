package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that adds GitHub authentication headers to all requests.
 * Caches the token to minimize DataStore access on the critical path.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    @Volatile
    private var cachedToken: String? = null
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        // Get token from cache or fetch it
        val token = getToken()

        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "token $token")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
        } else {
            chain.request().newBuilder()
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
        }

        return chain.proceed(request)
    }

    private fun getToken(): String? {
        // Return cached token if available
        cachedToken?.let { return it }

        // Otherwise fetch and cache it
        synchronized(lock) {
            // Double-check after acquiring lock
            cachedToken?.let { return it }

            val token = runBlocking {
                tokenManager.token.first()
            }
            cachedToken = token
            return token
        }
    }

    /**
     * Call this to invalidate the cached token (e.g., after logout)
     */
    fun clearCache() {
        synchronized(lock) {
            cachedToken = null
        }
    }
}
