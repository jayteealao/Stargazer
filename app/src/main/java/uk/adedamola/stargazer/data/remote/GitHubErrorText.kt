package uk.adedamola.stargazer.data.remote

import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One readable status line for a failed GitHub call, in the app's terminal voice. A rate limit
 * names the time GitHub accepts requests again. None of these signs the person out: only a 401
 * does, and that is handled before the error reaches the screen.
 */
object GitHubErrorText {

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun describe(
        e: Throwable,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): String = when (e) {
        is HttpException -> describeHttp(e, zone, nowMillis)
        is IOException -> "NO_CONNECTION"
        else -> "ERROR: ${e.message}"
    }

    private fun describeHttp(e: HttpException, zone: ZoneId, nowMillis: Long): String {
        val code = e.code()
        if (code == 403 || code == 429) {
            retryAt(e, nowMillis)?.let { at ->
                return "RATE_LIMITED — RETRY AT ${clock.format(Instant.ofEpochMilli(at).atZone(zone))}"
            }
        }
        return when {
            code == 403 -> "GITHUB_REFUSED (403)"
            code == 429 -> "RATE_LIMITED — try again in a minute"
            code >= 500 -> "GITHUB_ERROR ($code) — try again in a minute"
            else -> "ERROR: HTTP $code"
        }
    }

    /** Epoch millis from `retry-after` (seconds) or an exhausted `x-ratelimit-reset` (epoch seconds). */
    private fun retryAt(e: HttpException, nowMillis: Long): Long? {
        val headers = e.response()?.headers() ?: return null
        headers["retry-after"]?.trim()?.toLongOrNull()?.let { return nowMillis + it * 1000 }
        if (headers["x-ratelimit-remaining"]?.trim() == "0") {
            headers["x-ratelimit-reset"]?.trim()?.toLongOrNull()?.let { return it * 1000 }
        }
        return null
    }
}
