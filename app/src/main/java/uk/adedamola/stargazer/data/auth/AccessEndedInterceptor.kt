package uk.adedamola.stargazer.data.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a 401 from api.github.com into the access-ended sign-out. It runs after
 * [AuthInterceptor], so it sees the key the request was sent with. The response is returned
 * unchanged and never retried: the app has no way to renew a key. 403, 429 and 5xx pass through.
 */
@Singleton
class AccessEndedInterceptor @Inject constructor(
    private val session: AccessSession
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && request.url.host == AuthInterceptor.GITHUB_API_HOST) {
            val authorization = request.header("Authorization")
            if (authorization != null && authorization.startsWith(BEARER)) {
                session.onUnauthorized(authorization.removePrefix(BEARER))
            }
        }
        return response
    }

    private companion object {
        const val BEARER = "Bearer "
    }
}
