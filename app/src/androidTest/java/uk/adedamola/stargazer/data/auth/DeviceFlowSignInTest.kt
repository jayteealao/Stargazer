package uk.adedamola.stargazer.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** GitHub's device-code answers, each on a virtual clock (AC-4, AC-6, offline and GitHub errors). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeviceFlowSignInTest {

    private fun TestScope.engine(api: FakeDeviceFlowApi): DeviceFlowSignIn {
        api.clock = { testScheduler.currentTime }
        return DeviceFlowSignIn(api, "Iv-test-client", testScheduler.timeSource)
    }

    private fun gaps(times: List<Long>): List<Long> = times.zipWithNext { a, b -> b - a }

    @Test
    fun pendingPollsNoFasterThanInterval() = runTest {
        val api = FakeDeviceFlowApi(interval = 5)
        val key = fakeKey()
        api.script({ pending() }, { pending() }, { pending() }, { token(key) })

        val steps = engine(api).run().toList()

        assertTrue(steps.last() is DeviceFlowStep.Approved)
        assertEquals(listOf(5_000L, 10_000L, 15_000L, 20_000L), api.pollTimes.toList())
        assertTrue(gaps(api.pollTimes).all { it >= 5_000 })
    }

    @Test
    fun slowDownAddsFiveSecondsForTheRestOfTheFlow() = runTest {
        val api = FakeDeviceFlowApi(interval = 5)
        val key = fakeKey()
        api.script({ pending() }, { answer("slow_down", interval = 10) }, { pending() }, { pending() }, { token(key) })

        engine(api).run().toList()

        assertEquals(listOf(5_000L, 10_000L, 10_000L, 10_000L), gaps(api.pollTimes))
    }

    @Test
    fun slowDownWithoutIntervalStillAddsFiveSeconds() = runTest {
        val api = FakeDeviceFlowApi(interval = 5)
        val key = fakeKey()
        api.script({ answer("slow_down") }, { answer("slow_down") }, { pending() }, { token(key) })

        engine(api).run().toList()

        assertEquals(listOf(5_000L, 15_000L, 30_000L, 45_000L), api.pollTimes.toList())
    }

    @Test
    fun expiredTokenEndsExpired() = runTest {
        val api = FakeDeviceFlowApi()
        api.script({ pending() }, { answer("expired_token") })

        val steps = engine(api).run().toList()

        assertEquals(DeviceFlowStep.Expired, steps.last())
        assertEquals(2, api.pollTimes.size)
    }

    @Test
    fun incorrectDeviceCodeEndsExpired() = runTest {
        val api = FakeDeviceFlowApi()
        api.script({ answer("incorrect_device_code") })

        assertEquals(DeviceFlowStep.Expired, engine(api).run().toList().last())
    }

    @Test
    fun localExpiryEndsExpiredWithoutAnAnswer() = runTest {
        val api = FakeDeviceFlowApi(expiresIn = 899, interval = 5)

        val steps = engine(api).run().toList()

        assertEquals(DeviceFlowStep.Expired, steps.last())
        assertEquals("ends at expires_in", 899_000L, testScheduler.currentTime)
        assertTrue("no poll after expiry", api.pollTimes.last() <= 899_000L)
        assertEquals(179, api.pollTimes.size)
    }

    @Test
    fun accessDeniedEndsDenied() = runTest {
        val api = FakeDeviceFlowApi()
        api.script({ pending() }, { answer("access_denied") })

        assertEquals(DeviceFlowStep.Denied, engine(api).run().toList().last())
    }

    @Test
    fun requestIoExceptionIsOffline() = runTest {
        val api = FakeDeviceFlowApi()
        api.onRequest = { throw IOException("no network") }

        val steps = engine(api).run().toList()

        assertEquals(listOf(DeviceFlowStep.Requesting, DeviceFlowStep.Offline), steps)
        assertTrue(api.pollTimes.isEmpty())
    }

    @Test
    fun request404IsGitHubError() = runTest {
        val api = FakeDeviceFlowApi()
        api.onRequest = {
            throw HttpException(Response.error<Any>(404, """{"error":"Not Found"}""".toResponseBody("application/json".toMediaType())))
        }

        assertEquals(DeviceFlowStep.GitHubError("404"), engine(api).run().toList().last())
    }

    @Test
    fun deviceFlowDisabledIsGitHubError() = runTest {
        val api = FakeDeviceFlowApi()
        api.script({ answer("device_flow_disabled") })

        assertEquals(DeviceFlowStep.GitHubError("device_flow_disabled"), engine(api).run().toList().last())
    }

    @Test
    fun pollIoExceptionKeepsPolling() = runTest {
        val api = FakeDeviceFlowApi(interval = 5)
        val key = fakeKey()
        api.script({ throw IOException("drop") }, { token(key) })

        val steps = engine(api).run().toList()

        assertEquals(key, (steps.last() as DeviceFlowStep.Approved).key)
        assertEquals(listOf(5_000L, 10_000L), api.pollTimes.toList())
    }

    @Test
    fun tokenEndsApprovedAndShowsTheCodeFirst() = runTest {
        val api = FakeDeviceFlowApi(expiresIn = 900)
        val key = fakeKey()
        api.script({ token(key) })

        val steps = engine(api).run().toList()

        assertEquals(DeviceFlowStep.Requesting, steps[0])
        val ready = steps[1] as DeviceFlowStep.CodeReady
        assertEquals(FIXTURE_USER_CODE, ready.userCode)
        assertEquals("https://github.com/login/device", ready.verificationUri)
        assertEquals(895.seconds, -ready.expiresAt.elapsedNow())
        val approved = steps[2] as DeviceFlowStep.Approved
        assertEquals(key, approved.key)
        assertFalse("key never printed", approved.toString().contains(key))
        assertEquals(listOf("Iv-test-client"), api.clientIds.toList())
    }
}
