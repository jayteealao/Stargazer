package uk.adedamola.stargazer.data.auth

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import uk.adedamola.stargazer.data.di.NetworkModule
import uk.adedamola.stargazer.data.di.SecureStorageModule
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** A plain test value in the shape of nothing real; never a GitHub key. */
fun fakeKey(): String = "test-key-not-real-" + UUID.randomUUID()

const val FIXTURE_USER_CODE = "WDJB-MJHT"
const val FIXTURE_DEVICE_CODE = "device-code-fixture"

fun pending() = TokenPollResponse(error = "authorization_pending")
fun answer(error: String, interval: Int? = null) = TokenPollResponse(error = error, interval = interval)
fun token(key: String) = TokenPollResponse(accessToken = key, tokenType = "bearer")

/**
 * GitHub's device-code endpoints, scripted per test. [onRequest] and [onPoll] may suspend or
 * throw; [clock] stamps each poll (a virtual clock in engine tests).
 */
class FakeDeviceFlowApi(
    var expiresIn: Int = 900,
    var interval: Int = 5,
    var clock: () -> Long = System::currentTimeMillis,
) : DeviceFlowApi {
    val pollTimes = CopyOnWriteArrayList<Long>()
    val clientIds = CopyOnWriteArrayList<String>()
    @Volatile var requestCount = 0
    var onRequest: suspend () -> DeviceCodeResponse = {
        DeviceCodeResponse(FIXTURE_DEVICE_CODE, FIXTURE_USER_CODE, "https://github.com/login/device", expiresIn, interval)
    }
    var onPoll: suspend (index: Int) -> TokenPollResponse = { pending() }

    /** Answers the scripted polls in order, then repeats the last one. */
    fun script(vararg answers: () -> TokenPollResponse) {
        onPoll = { i -> answers[minOf(i, answers.size - 1)]() }
    }

    override suspend fun requestCode(clientId: String): DeviceCodeResponse {
        requestCount++
        clientIds += clientId
        return onRequest()
    }

    override suspend fun pollToken(clientId: String, deviceCode: String, grantType: String): TokenPollResponse {
        val index = pollTimes.size
        pollTimes += clock()
        return onPoll(index)
    }
}

/** A real [TokenManager] over a temporary file and its own Keystore alias. */
class TokenManagerFixture {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "test_alias_" + UUID.randomUUID()
    private val dir = File(context.cacheDir, "auth-" + UUID.randomUUID()).apply { mkdirs() }
    val file = File(dir, "key.preferences_pb")
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    val tokenManager = TokenManager(
        SecureStorageModule.createAccessKeyDataStore(scope) { file },
        AndroidKeystoreCipher(alias),
        scope,
    )

    fun close() = runBlocking {
        job.cancelAndJoin()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.run {
            if (containsAlias(alias)) deleteEntry(alias)
        }
        dir.deleteRecursively()
        Unit
    }
}

/** One scripted HTTP answer. */
data class Scripted(val code: Int, val body: String = "{}", val headers: Map<String, String> = emptyMap())

/** Answers every request in place of the network, as scripted, and remembers what it saw. */
class ScriptedNetwork(var answer: (Request) -> Scripted = { Scripted(200) }) : Interceptor {
    val requests = CopyOnWriteArrayList<Request>()
    val bodies = CopyOnWriteArrayList<String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        bodies += request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }.orEmpty()
        val scripted = answer(request)
        if (scripted.code == -1) throw IOException("scripted offline")
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(scripted.code)
            .message("scripted")
            .apply { scripted.headers.forEach { (k, v) -> header(k, v) } }
            .body(scripted.body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

/** The production GitHub API stack over [network], with the access-ended handler. */
fun apiService(
    keys: AccessKeyProvider,
    network: ScriptedNetwork,
    session: AccessSession? = null,
    logLines: MutableList<String> = CopyOnWriteArrayList(),
): GitHubApiService {
    val client = NetworkModule.buildGitHubClient(
        authInterceptor = AuthInterceptor(keys),
        loggingInterceptor = NetworkModule.createLoggingInterceptor(true, HttpLoggingInterceptor.Logger { logLines += it }),
        terminal = network,
        accessEnded = session?.let { AccessEndedInterceptor(it) },
    )
    return NetworkModule.provideGitHubApiService(NetworkModule.provideRetrofit(client, NetworkModule.provideJson()))
}

fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
        .build()

const val FIXTURE_USER_ID = 5901851L

/** Seeds an account, three repos, a tag with one assignment, a preset and a sync row. */
suspend fun seedLocalData(db: AppDatabase) {
    db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "fixture-login", "https://avatars.example/u.png", 1L))
    @Suppress("DEPRECATION") // sdlc-debt: test seed uses the deprecated insert-or-replace DAO call (ceiling: test-only; upgrade path: an upsert helper if the DAO drops it)
    db.repositoryDao().insertRepositories((1..3).map(::fixtureRepo))
    val tagId = db.tagDao().insertTag(Tag(name = "kept", color = "#FF6600")).toInt()
    db.repositoryTagDao().addTagToRepository(RepositoryTag(repositoryId = 900_001, tagId = tagId))
    db.searchPresetDao().insertPreset(
        SearchPreset(name = "kept", sortBy = "stars", filterLanguage = null, filterMinStars = null, filterMaxStars = null, searchQuery = null)
    )
    db.syncMetadataDao().updateMetadata(SyncMetadata(dataType = "starred_repos", isInitialSyncComplete = true))
}

/** Row counts of every table that local data lives in. */
fun localCounts(db: AppDatabase): Map<String, Int> =
    listOf("account", "repositories", "tags", "repository_tags", "search_presets", "sync_metadata").associateWith { table ->
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) }
    }

fun fixtureRepo(n: Int) = RepositoryEntity(
    id = 900_000 + n,
    name = "repo-$n",
    fullName = "fixture/repo-$n",
    ownerLogin = "fixture",
    ownerAvatarUrl = "",
    htmlUrl = "https://github.com/fixture/repo-$n",
    description = "Fixture repository $n.",
    fork = false,
    createdAt = "2024-01-01T00:00:00Z",
    updatedAt = "2026-10-01T00:00:00Z",
    pushedAt = "2026-10-01T00:00:00Z",
    homepage = null,
    size = 100,
    stargazersCount = n,
    watchersCount = n,
    language = "Kotlin",
    forksCount = n,
    openIssuesCount = 0,
    defaultBranch = "main",
    topics = "",
    visibility = "public",
    licenseName = "MIT",
    starredAt = 1_760_000_000_000L - n,
)
