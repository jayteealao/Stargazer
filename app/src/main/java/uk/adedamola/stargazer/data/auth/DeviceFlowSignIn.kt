package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** One step of a device-code sign-in, as the sign-in screen shows it. */
sealed interface DeviceFlowStep {
    data object Requesting : DeviceFlowStep

    data class CodeReady(
        val userCode: String,
        val verificationUri: String,
        val expiresAt: TimeMark
    ) : DeviceFlowStep

    /** GitHub approved the code. [key] is the new access key; it is never logged. */
    class Approved(val key: String) : DeviceFlowStep {
        override fun toString(): String = "Approved(key=██)"
    }

    data object Expired : DeviceFlowStep
    data object Denied : DeviceFlowStep
    data object Offline : DeviceFlowStep

    /** GitHub refused the request; [label] is its error code or HTTP status, shown as is. */
    data class GitHubError(val label: String) : DeviceFlowStep
}

/**
 * GitHub's device-code sign-in: request a code, then poll until the person approves it on
 * github.com, denies it, or it expires. The flow is cold; cancelling it abandons the code.
 */
class DeviceFlowSignIn(
    private val api: DeviceFlowApi,
    private val clientId: String,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {

    fun run(): Flow<DeviceFlowStep> = flow {
        emit(DeviceFlowStep.Requesting)
        val code = try {
            api.requestCode(clientId)
        } catch (e: IOException) {
            emit(DeviceFlowStep.Offline)
            return@flow
        } catch (e: HttpException) {
            emit(DeviceFlowStep.GitHubError(e.code().toString()))
            return@flow
        } catch (e: SerializationException) {
            emit(DeviceFlowStep.GitHubError(UNREADABLE))
            return@flow
        }

        val expiresAt = timeSource.markNow() + code.expiresIn.seconds
        emit(DeviceFlowStep.CodeReady(code.userCode, code.verificationUri, expiresAt))

        var interval = code.interval.seconds
        while (true) {
            val remaining = -expiresAt.elapsedNow()
            if (remaining <= interval) {
                // The code dies before the next poll is allowed: stop at its expiry, poll no more.
                if (remaining > Duration.ZERO) delay(remaining)
                emit(DeviceFlowStep.Expired)
                return@flow
            }
            delay(interval)

            val answer = try {
                api.pollToken(clientId, code.deviceCode)
            } catch (e: IOException) {
                continue // A short drop keeps the code alive; try again at the interval.
            } catch (e: HttpException) {
                emit(DeviceFlowStep.GitHubError(e.code().toString()))
                return@flow
            } catch (e: SerializationException) {
                emit(DeviceFlowStep.GitHubError(UNREADABLE))
                return@flow
            }

            val key = answer.accessToken
            if (key != null) {
                emit(DeviceFlowStep.Approved(key))
                return@flow
            }
            when (answer.error) {
                "authorization_pending" -> Unit
                // slow_down adds 5 seconds for the rest of the flow; GitHub may name a longer wait.
                "slow_down" -> interval = maxOf(interval + 5.seconds, (answer.interval ?: 0).seconds)
                "expired_token", "incorrect_device_code" -> {
                    emit(DeviceFlowStep.Expired)
                    return@flow
                }
                "access_denied" -> {
                    emit(DeviceFlowStep.Denied)
                    return@flow
                }
                else -> {
                    emit(DeviceFlowStep.GitHubError(answer.error ?: UNREADABLE))
                    return@flow
                }
            }
        }
    }

    private companion object {
        const val UNREADABLE = "unreadable_response"
    }
}
