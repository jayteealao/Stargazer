package uk.adedamola.stargazer.data.auth

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.remote.GitHubErrorText
import uk.adedamola.stargazer.ui.AuthState
import uk.adedamola.stargazer.ui.NavigationViewModel
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Only a 401 ends access: the key goes once, local data stays (AC-21, AC-22, AC-23). */
@RunWith(AndroidJUnit4::class)
class AccessEndedTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
    private lateinit var network: ScriptedNetwork
    private val key = fakeKey()

    @Before
    fun setUp() = runBlocking {
        keys = TokenManagerFixture()
        db = inMemoryDatabase()
        seedLocalData(db)
        session = AccessSession(keys.tokenManager)
        network = ScriptedNetwork()
        keys.tokenManager.saveToken(key)
    }

    @After
    fun tearDown() {
        db.close()
        keys.close()
    }

    private fun service() = apiService(keys.tokenManager, network, session)

    @Test
    fun parallel401DeletesOnceAndRaisesOnce() = runBlocking {
        val before = localCounts(db)
        val seen = CopyOnWriteArrayList<Boolean>()
        val watcher = launch(Dispatchers.Unconfined) { session.accessEnded.collect { seen += it } }
        network.answer = { Scripted(401, """{"message":"Bad credentials"}""") }
        val api = service()

        val failures = (1..8).map {
            async(Dispatchers.IO) { runCatching { api.getStarredRepositories() }.exceptionOrNull() }
        }.awaitAll()

        assertEquals(8, network.requests.size)
        failures.forEach { assertEquals(401, (it as HttpException).code()) }
        network.requests.forEach { assertEquals("Bearer $key", it.header("Authorization")) }
        assertTrue(session.accessEnded.value)
        assertEquals("one false → true", listOf(false, true), seen.toList())
        assertNull(keys.tokenManager.currentKey())
        assertNull(keys.tokenManager.token.first())
        assertEquals("local data untouched", before, localCounts(db))
        watcher.cancel()
    }

    @Test
    fun concurrentConditionalDeletesSucceedOnce() = runBlocking {
        val results = (1..8).map { async(Dispatchers.IO) { keys.tokenManager.clearTokenIf(key) } }.awaitAll()

        assertEquals("deleted exactly once", 1, results.count { it })
        assertNull(keys.tokenManager.currentKey())
    }

    @Test
    fun late401FromOldKeyKeepsNewKey() = runBlocking {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        network.answer = {
            arrived.countDown()
            release.await(10, TimeUnit.SECONDS)
            Scripted(401)
        }
        val api = service()
        val inFlight = async(Dispatchers.IO) { runCatching { api.getStarredRepositories() } }
        assertTrue(arrived.await(10, TimeUnit.SECONDS))
        val newKey = fakeKey()
        keys.tokenManager.saveToken(newKey)

        release.countDown()
        inFlight.await()

        assertEquals("Bearer $key", network.requests.single().header("Authorization"))
        assertEquals(newKey, keys.tokenManager.currentKey())
        assertFalse(session.accessEnded.value)
    }

    @Test
    fun ok200StaysSignedIn() = runBlocking {
        network.answer = { Scripted(200, "[]") }

        service().getStarredRepositories()

        assertEquals(key, keys.tokenManager.currentKey())
        assertFalse(session.accessEnded.value)
    }

    @Test
    fun rateLimitAndServerErrorsStaySignedIn() = runBlocking {
        val utc = ZoneId.of("UTC")
        val cases = listOf(
            Scripted(403, headers = mapOf("x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "1800000000"))
                to "RATE_LIMITED — RETRY AT 08:00",
            Scripted(429, headers = mapOf("retry-after" to "60")) to "RATE_LIMITED — RETRY AT 00:01",
            Scripted(403) to "GITHUB_REFUSED (403)",
            Scripted(500) to "GITHUB_ERROR (500) — try again in a minute",
            Scripted(503) to "GITHUB_ERROR (503) — try again in a minute",
        )
        for ((scripted, text) in cases) {
            network.answer = { scripted }
            val error = runCatching { service().getStarredRepositories() }.exceptionOrNull()!!

            assertEquals(text, GitHubErrorText.describe(error, utc, nowMillis = 0L))
            assertEquals("still signed in after ${scripted.code}", key, keys.tokenManager.currentKey())
            assertFalse(session.accessEnded.value)
        }
        assertEquals("NO_CONNECTION", GitHubErrorText.describe(IOException("offline")))
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun mediatorShowsReadableError() = runBlocking {
        network.answer = { Scripted(429, headers = mapOf("retry-after" to "60")) }
        val mediator = StarredReposRemoteMediator(service(), db)

        val result = mediator.load(
            LoadType.REFRESH,
            PagingState(emptyList(), null, PagingConfig(pageSize = 30), leadingPlaceholderCount = 0)
        )

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertTrue(mediator.syncProgress.value.error!!, mediator.syncProgress.value.error!!.startsWith("RATE_LIMITED — RETRY AT "))
        assertEquals(key, keys.tokenManager.currentKey())
    }

    @Test
    fun navigationShowsSignInAfter401() = runBlocking {
        val navigation = NavigationViewModel(keys.tokenManager)
        withTimeout(10_000) { navigation.authState.first { it == AuthState.Authenticated } }
        network.answer = { Scripted(401) }

        runCatching { service().getStarredRepositories() }

        withTimeout(10_000) { navigation.authState.first { it == AuthState.Unauthenticated } }
        assertTrue(session.accessEnded.value)
    }
}
