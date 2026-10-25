package uk.adedamola.stargazer.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.AccessKeyProvider
import uk.adedamola.stargazer.data.auth.AuthInterceptor
import uk.adedamola.stargazer.data.di.NetworkModule
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.api.MarkdownRenderRequest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** The production client's headers and log redaction, with a recording fake in place of the network. */
@RunWith(AndroidJUnit4::class)
class GitHubHttpClientTest {

    private val fakeKey = "test-key-not-real-" + UUID.randomUUID()

    /** Answers every request in place of the network and remembers what it saw. */
    private class Recorder : Interceptor {
        val requests = CopyOnWriteArrayList<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val path = request.url.encodedPath
            val (body, type) = when {
                path.endsWith("/starred") -> "[]" to "application/json"
                path.endsWith("/search/repositories") ->
                    """{"total_count":0,"incomplete_results":false,"items":[]}""" to "application/json"
                path.endsWith("/readme") -> "Hello" to "text/plain"
                path.endsWith("/markdown") -> "<p>Hi</p>" to "text/html"
                else -> "{}" to "application/json"
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }
    }

    private fun client(key: String?, debug: Boolean, logLines: MutableList<String>, recorder: Recorder): OkHttpClient =
        NetworkModule.buildGitHubClient(
            authInterceptor = AuthInterceptor(object : AccessKeyProvider {
                override fun currentKey(): String? = key
            }),
            loggingInterceptor = NetworkModule.createLoggingInterceptor(debug) { logLines += it },
            terminal = recorder,
        )

    private fun service(client: OkHttpClient): GitHubApiService =
        NetworkModule.provideGitHubApiService(NetworkModule.provideRetrofit(client, NetworkModule.provideJson()))

    private fun callEveryEndpoint(service: GitHubApiService) = runBlocking {
        service.getStarredRepositories()
        service.getStarredRepositoriesWithTimestamp()
        service.searchRepositories("kotlin")
        service.getReadme("octocat", "Hello-World")
        service.renderMarkdown(MarkdownRenderRequest(text = "Hi"))
    }

    @Test
    fun redactsAuthorizationInDebugAndRelease() {
        for (debug in listOf(true, false)) {
            val lines = CopyOnWriteArrayList<String>()
            callEveryEndpoint(service(client(fakeKey, debug, lines, Recorder())))

            assertTrue("logger produced output (debug=$debug)", lines.isNotEmpty())
            lines.forEach { assertFalse("key in log line (debug=$debug): ${it.take(20)}", it.contains(fakeKey)) }
            if (debug) {
                val authLines = lines.filter { it.startsWith("Authorization:") }
                assertTrue("Authorization header logged in redacted form", authLines.isNotEmpty())
            }
        }
    }

    @Test
    fun everyRequestCarriesContractHeaders() {
        val recorder = Recorder()
        callEveryEndpoint(service(client(fakeKey, debug = true, logLines = mutableListOf(), recorder = recorder)))

        assertEquals(5, recorder.requests.size)
        val accepts = recorder.requests.map { request ->
            assertEquals("Bearer $fakeKey", request.header("Authorization"))
            assertEquals("2022-11-28", request.header("X-GitHub-Api-Version"))
            assertEquals(1, request.headers("Accept").size)
            request.header("Accept")
        }
        assertEquals(
            listOf(
                "application/vnd.github+json",
                "application/vnd.github.star+json",
                "application/vnd.github+json",
                "application/vnd.github.raw+json",
                "application/vnd.github+json",
            ),
            accepts
        )
    }

    @Test
    fun noAuthorizationWhenSignedOut() {
        val recorder = Recorder()
        callEveryEndpoint(service(client(key = null, debug = false, logLines = mutableListOf(), recorder = recorder)))

        recorder.requests.forEach { request ->
            assertNull(request.header("Authorization"))
            assertEquals("2022-11-28", request.header("X-GitHub-Api-Version"))
            assertTrue(request.header("Accept")!!.startsWith("application/vnd.github"))
        }
    }

    @Test
    fun noAuthorizationForOtherHosts() {
        val recorder = Recorder()
        val client = client(fakeKey, debug = false, logLines = mutableListOf(), recorder = recorder)

        client.newCall(Request.Builder().url("https://example.com/").build()).execute().close()

        val request = recorder.requests.single()
        assertNull(request.header("Authorization"))
        assertNull(request.header("X-GitHub-Api-Version"))
    }
}
