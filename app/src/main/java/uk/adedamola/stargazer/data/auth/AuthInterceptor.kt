package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            tokenManager.token.first()
        }

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
}
