package uk.adedamola.stargazer.data.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds GitHub's REST API contract headers and the access key to requests for api.github.com.
 * Requests to any other host pass through unchanged, so the key never leaves for another host.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val accessKeyProvider: AccessKeyProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != GITHUB_API_HOST) return chain.proceed(original)

        val request = original.newBuilder().apply {
            accessKeyProvider.currentKey()?.let { header("Authorization", "Bearer $it") }
            if (original.header("Accept") == null) header("Accept", DEFAULT_ACCEPT)
            header("X-GitHub-Api-Version", API_VERSION)
        }.build()

        return chain.proceed(request)
    }

    companion object {
        const val GITHUB_API_HOST = "api.github.com"
        const val DEFAULT_ACCEPT = "application/vnd.github+json"
        const val API_VERSION = "2022-11-28"
    }
}
