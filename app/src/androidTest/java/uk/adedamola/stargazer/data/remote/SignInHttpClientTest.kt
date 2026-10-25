package uk.adedamola.stargazer.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.AccessKeyProvider
import uk.adedamola.stargazer.data.auth.AuthInterceptor
import uk.adedamola.stargazer.data.auth.DEVICE_GRANT
import uk.adedamola.stargazer.data.auth.DeviceFlowApi
import uk.adedamola.stargazer.data.auth.FIXTURE_DEVICE_CODE
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.di.NetworkModule
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList

/** The github.com sign-in client: no key, no client secret, no bodies in the log (AC-7). */
@RunWith(AndroidJUnit4::class)
class SignInHttpClientTest {

    private val key = fakeKey()
    private val clientId = "Iv-test-client"

    private fun network() = ScriptedNetwork { request ->
        when (request.url.encodedPath) {
            "/login/device/code" -> Scripted(
                200,
                """{"device_code":"$FIXTURE_DEVICE_CODE","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
            )
            "/login/oauth/access_token" -> Scripted(200, """{"access_token":"$key","token_type":"bearer","scope":""}""")
            else -> Scripted(404)
        }
    }

    private fun api(network: ScriptedNetwork, logLines: MutableList<String> = CopyOnWriteArrayList()): DeviceFlowApi {
        val client = NetworkModule.buildSignInClient(
            NetworkModule.createSignInLoggingInterceptor { logLines += it },
            terminal = network
        )
        return NetworkModule.createSignInRetrofit(client, NetworkModule.provideJson()).create(DeviceFlowApi::class.java)
    }

    private fun form(body: String): Map<String, String> = body.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    @Test
    fun noAuthorizationOnSignInRequests() = runBlocking<Unit> {
        val network = network()
        val client = NetworkModule.buildSignInClient(NetworkModule.createSignInLoggingInterceptor { }, terminal = network)

        client.newCall(
            Request.Builder()
                .url(NetworkModule.SIGN_IN_BASE_URL + "login/device/code")
                .header("Authorization", "Bearer $key")
                .post(FormBody.Builder().add("client_id", clientId).build())
                .build()
        ).execute().close()
        val api = api(network)
        api.requestCode(clientId)
        api.pollToken(clientId, FIXTURE_DEVICE_CODE)

        assertEquals(3, network.requests.size)
        network.requests.forEach { request ->
            assertEquals("github.com", request.url.host)
            assertNull("Authorization on ${request.url.encodedPath}", request.header("Authorization"))
        }
    }

    @Test
    fun tokenRequestCarriesNoClientSecret() = runBlocking<Unit> {
        val network = network()
        val api = api(network)

        api.requestCode(clientId)
        val answer = api.pollToken(clientId, FIXTURE_DEVICE_CODE)

        assertEquals(key, answer.accessToken)
        assertEquals(mapOf("client_id" to clientId), form(network.bodies[0]))
        assertEquals(
            mapOf("client_id" to clientId, "device_code" to FIXTURE_DEVICE_CODE, "grant_type" to DEVICE_GRANT),
            form(network.bodies[1])
        )
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", DEVICE_GRANT)
        network.bodies.forEach { assertFalse("client_secret sent", it.contains("client_secret")) }
    }

    @Test
    fun acceptsJson() = runBlocking<Unit> {
        val network = network()
        val api = api(network)

        api.requestCode(clientId)
        api.pollToken(clientId, FIXTURE_DEVICE_CODE)

        network.requests.forEach { assertEquals(listOf("application/json"), it.headers("Accept")) }
    }

    @Test
    fun logNeverHoldsTheKeyOrBodies() = runBlocking<Unit> {
        val lines = CopyOnWriteArrayList<String>()
        val api = api(network(), lines)

        api.requestCode(clientId)
        val answer = api.pollToken(clientId, FIXTURE_DEVICE_CODE)

        assertTrue("logger produced output", lines.isNotEmpty())
        lines.forEach { line ->
            assertFalse("key in log", line.contains(key))
            assertFalse("device code in log", line.contains(FIXTURE_DEVICE_CODE))
            assertFalse("token body in log", line.contains("access_token\""))
        }
        assertFalse("key in toString", answer.toString().contains(key))
    }

    @Test
    fun apiClientNeverSendsTheKeyToGithubDotCom() {
        val network = network()
        val client = NetworkModule.buildGitHubClient(
            authInterceptor = AuthInterceptor(object : AccessKeyProvider {
                override fun currentKey(): String = key
            }),
            loggingInterceptor = NetworkModule.createLoggingInterceptor(false) { },
            terminal = network,
        )

        client.newCall(
            Request.Builder()
                .url("https://github.com/login/device/code")
                .post(FormBody.Builder().add("client_id", clientId).build())
                .build()
        ).execute().close()

        assertNull(network.requests.single().header("Authorization"))
    }
}
