package uk.adedamola.stargazer.data.remote

import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One readable status line for a failed GitHub call, in the app's terminal voice. A rate limit
 * names the time GitHub accepts requests again. None of these signs the person out: only a 401
 * does, and that is handled before the error reaches the screen.
 */
object GitHubErrorText {

    fun describe(
        e: Throwable,
        zone: TimeZone = TimeZone.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): String = when (e) {
        is HttpException -> describeHttp(e, zone, nowMillis)
        is IOException -> "NO_CONNECTION"
        else -> "ERROR: ${e.message}"
    }

    private fun describeHttp(e: HttpException, zone: TimeZone, nowMillis: Long): String {
        val code = e.code()
        if (code == 403 || code == 429) {
            retryAtMillis(e, nowMillis)?.let { at ->
                return "RATE_LIMITED — RETRY AT ${clockText(at, zone)}"
            }
        }
        return when {
            code == 403 -> "GITHUB_REFUSED (403)"
            code == 429 -> "RATE_LIMITED — try again in a minute"
            code >= 500 -> "GITHUB_ERROR ($code) — try again in a minute"
            else -> "ERROR: HTTP $code"
        }
    }

    /** [at] (epoch millis) as `HH:mm` in [zone]. */
    fun clockText(at: Long, zone: TimeZone = TimeZone.getDefault()): String =
        // java.text, not java.time: minSdk 23 has no java.time without desugaring.
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(Date(at))

    /**
     * Epoch millis from `retry-after` (seconds) or an exhausted `x-ratelimit-reset` (epoch
     * seconds); null when the answer names no reset time.
     */
    internal fun retryAtMillis(e: HttpException, nowMillis: Long): Long? {
        val headers = e.response()?.headers() ?: return null
        headers["retry-after"]?.trim()?.toLongOrNull()?.let { return nowMillis + it * 1000 }
        if (headers["x-ratelimit-remaining"]?.trim() == "0") {
            headers["x-ratelimit-reset"]?.trim()?.toLongOrNull()?.let { return it * 1000 }
        }
        return null
    }
}
