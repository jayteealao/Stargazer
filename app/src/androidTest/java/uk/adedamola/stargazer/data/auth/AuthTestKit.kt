package uk.adedamola.stargazer.data.auth

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.paging.ExperimentalPagingApi
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.data.local.database.PendingUnstarEntity
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.local.database.StarListItemEntity
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.remote.Connectivity
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.api.GitHubGraphQlApi
import uk.adedamola.stargazer.data.repository.StarEngine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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

/** GitHub's GraphQL endpoint on the same production stack as [apiService]. */
fun graphQlApi(
    keys: AccessKeyProvider,
    network: ScriptedNetwork,
    session: AccessSession? = null,
): GitHubGraphQlApi {
    val client = NetworkModule.buildGitHubClient(
        authInterceptor = AuthInterceptor(keys),
        loggingInterceptor = NetworkModule.createLoggingInterceptor(true, HttpLoggingInterceptor.Logger { }),
        terminal = network,
        accessEnded = session?.let { AccessEndedInterceptor(it) },
    )
    return NetworkModule.provideGitHubGraphQlApi(NetworkModule.provideRetrofit(client, NetworkModule.provideJson()))
}

/** The body a request was sent with, as text. */
fun requestBody(request: Request): String =
    request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }.orEmpty()

/** The GraphQL request [body] as JSON (query and variables). */
fun graphQlBody(body: String): JsonObject =
    kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject

/** A full `GET /user` answer with the profile fields. */
fun profileJson(
    id: Long,
    login: String,
    name: String? = null,
    bio: String? = null,
    followers: Int = 0,
    following: Int = 0,
    publicRepos: Int = 0,
    location: String? = null,
    company: String? = null,
    blog: String? = null,
): String = buildJsonObject {
    put("id", id)
    put("login", login)
    put("avatar_url", "https://avatars.example/$id.png")
    put("html_url", "https://github.com/$login")
    put("name", name)
    put("bio", bio)
    put("followers", followers)
    put("following", following)
    put("public_repos", publicRepos)
    put("location", location)
    put("company", company)
    put("blog", blog)
}.toString()

/** One star list in a scripted answer: [ids] are the repository ids GitHub returns on the first page. */
data class FakeList(
    val id: String,
    val name: String,
    val ids: List<Long> = emptyList(),
    val total: Int = ids.size,
    val hasNextPage: Boolean = false,
    val endCursor: String? = null,
)

private fun pageInfo(hasNextPage: Boolean, endCursor: String?) = buildJsonObject {
    put("hasNextPage", hasNextPage)
    put("endCursor", endCursor)
}

private fun repoNodes(ids: List<Long>): JsonArray = buildJsonArray {
    ids.forEach { add(buildJsonObject { put("databaseId", it) }) }
}

/** A `viewer.lists` answer. */
fun listsJson(
    viewerId: Long,
    starred: Int,
    lists: List<FakeList>,
    hasNextPage: Boolean = false,
    endCursor: String? = null,
): String = buildJsonObject {
    putJsonObject("data") {
        putJsonObject("viewer") {
            put("databaseId", viewerId)
            putJsonObject("starredRepositories") { put("totalCount", starred) }
            putJsonObject("lists") {
                put("pageInfo", pageInfo(hasNextPage, endCursor))
                putJsonArray("nodes") {
                    lists.forEach { list ->
                        add(buildJsonObject {
                            put("id", list.id)
                            put("name", list.name)
                            put("description", JsonNull)
                            put("isPrivate", false)
                            putJsonObject("items") {
                                put("totalCount", list.total)
                                put("pageInfo", pageInfo(list.hasNextPage, list.endCursor))
                                put("nodes", repoNodes(list.ids))
                            }
                        })
                    }
                }
            }
        }
    }
}.toString()

/** A `node(id:)` answer with the next page of one list's repositories. */
fun listItemsPageJson(ids: List<Long>, hasNextPage: Boolean = false, endCursor: String? = null): String =
    buildJsonObject {
        putJsonObject("data") {
            putJsonObject("node") {
                putJsonObject("items") {
                    put("pageInfo", pageInfo(hasNextPage, endCursor))
                    put("nodes", repoNodes(ids))
                }
            }
        }
    }.toString()

/** A GraphQL answer that carries errors and no data. */
fun graphQlErrorsJson(): String = buildJsonObject {
    put("data", JsonNull)
    putJsonArray("errors") { add(buildJsonObject { put("message", JsonPrimitive("Something went wrong")) }) }
}.toString()

fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
        .build()

const val FIXTURE_USER_ID = 5901851L

/** A second, different GitHub account for the account-change tests. */
const val SECOND_USER_ID = 7713002L

/** A `GET /user` answer. */
fun userJson(id: Long, login: String, name: String? = null): String =
    """{"id":$id,"login":"$login","avatar_url":"https://avatars.example/$id.png","name":${name?.let { "\"$it\"" } ?: "null"}}"""

/**
 * Seeds an account, three repos (one favorite, one pinned), a tag with one assignment, a preset,
 * a sync row, the cached profile and a star list with one item.
 */
suspend fun seedLocalData(db: AppDatabase) {
    db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "fixture-login", "https://avatars.example/u.png", 1L))
    @Suppress("DEPRECATION") // sdlc-debt: test seed uses the deprecated insert-or-replace DAO call (ceiling: test-only; upgrade path: an upsert helper if the DAO drops it)
    db.repositoryDao().insertRepositories((1..3).map(::fixtureRepo))
    db.repositoryDao().updateFavorite(900_001, true)
    db.repositoryDao().updatePinned(900_002, true)
    val tagId = db.tagDao().insertTag(Tag(name = "kept", color = "#FF6600")).toInt()
    db.repositoryTagDao().addTagToRepository(RepositoryTag(repositoryId = 900_001, tagId = tagId))
    db.searchPresetDao().insertPreset(
        SearchPreset(name = "kept", sortBy = "stars", filterLanguage = null, filterMinStars = null, filterMaxStars = null, searchQuery = null)
    )
    db.syncMetadataDao().updateMetadata(SyncMetadata(dataType = "starred_repos", isInitialSyncComplete = true))
    db.gitHubProfileDao().upsert(
        GitHubProfileEntity(
            userId = FIXTURE_USER_ID, login = "fixture-login", name = "Fixture Person", avatarUrl = "",
            bio = null, followers = 1, following = 2, publicRepos = 3, starredCount = 3, location = null,
            company = null, blog = null, htmlUrl = "https://github.com/fixture-login", fetchedAt = 1L
        )
    )
    db.starListDao().upsertLists(listOf(StarListEntity("list-1", "kept", null, false, 1, 0, 1L)))
    db.starListDao().upsertItems(listOf(StarListItemEntity("list-1", 900_001L)))
    db.pendingUnstarDao().insert(PendingUnstarEntity(900_003, "fixture", "repo-3", PendingUnstarEntity.CONFIRMED, 2L, 1L))
}

/** Every table that holds account-derived data (the wipe set); `account` is the record itself. */
val WIPE_TABLES = listOf(
    "repositories", "tags", "repository_tags", "search_presets", "sync_metadata",
    "github_profile", "star_lists", "star_list_items", "pending_unstars"
)

/** Row counts of the account record and every table that local data lives in. */
fun localCounts(db: AppDatabase): Map<String, Int> =
    (listOf("account") + WIPE_TABLES).associateWith { table ->
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) }
    }

/** A [LegacyAccountStore] over a temporary file, standing in for the earlier version's preferences. */
class LegacyStoreFixture {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "legacy-" + UUID.randomUUID()).apply { mkdirs() }
    private val job = SupervisorJob()
    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(job + Dispatchers.IO),
        produceFile = { File(dir, "auth_preferences.preferences_pb") },
    )
    val store = LegacyAccountStore(dataStore)

    /** Writes a token the way the earlier version did (plain text, key `github_token`). */
    suspend fun seedOldToken(value: String) {
        dataStore.updateData { it.toMutablePreferences().apply { set(LegacyAccountStore.OLD_TOKEN, value) } }
    }

    fun close() = runBlocking {
        job.cancelAndJoin()
        dir.deleteRecursively()
        Unit
    }
}

/** The production sign-in completion over an in-memory database, a temp key store and [api]. */
@OptIn(ExperimentalPagingApi::class)
fun completer(
    db: AppDatabase,
    tokenManager: TokenManager,
    api: GitHubApiService,
    legacy: LegacyAccountStore,
    mediator: StarredReposRemoteMediator = StarredReposRemoteMediator(api, db),
    beforeCommit: suspend () -> Unit = {},
    clock: () -> Long = { 42L },
): SignInCompleter = SignInCompleter(
    api = api,
    accountDao = db.accountDao(),
    tokenManager = tokenManager,
    wipe = AccountDataWipe(db, mediator, beforeCommit),
    legacyIdentifier = LegacyAccountIdentifier(api, db.accountDao(), legacy, clock),
    legacyStore = legacy,
    repositoryDao = db.repositoryDao(),
    tagDao = db.tagDao(),
    presetDao = db.searchPresetDao(),
    profileDao = db.gitHubProfileDao(),
    clock = clock,
)

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

/** A connection the test switches on and off. */
class FakeConnectivity(online: Boolean = true) : Connectivity {
    override val online = MutableStateFlow(online)
    fun set(value: Boolean) { online.value = value }
}

/**
 * The Undo window's wait, held until the test calls [release]: `sleep` for a [StarEngine] that
 * lets the test end the window when it chooses.
 */
class WindowGate {
    private var gate = CompletableDeferred<Unit>()
    @Volatile var waits = 0
        private set

    val sleep: suspend (Long) -> Unit = { waits++; gate.await() }

    /** Ends every window that is waiting now; later windows wait again. */
    fun release() {
        val open = gate
        gate = CompletableDeferred()
        open.complete(Unit)
    }
}

/** A [StarEngine] over [db] and the production GitHub stack answered by [network]. */
fun starEngine(
    db: AppDatabase,
    network: ScriptedNetwork,
    keys: AccessKeyProvider,
    scope: CoroutineScope,
    connectivity: Connectivity = FakeConnectivity(),
    clock: () -> Long = System::currentTimeMillis,
    sleep: suspend (Long) -> Unit = WindowGate().sleep,
    session: AccessSession? = null,
): StarEngine = StarEngine(apiService(keys, network, session), db, connectivity, clock, scope, sleep)

/** The path of GitHub's star endpoint for one repository. */
fun starPath(owner: String, repo: String) = "/user/starred/$owner/$repo"

/** The requests [network] saw for [method] on [path]. */
fun ScriptedNetwork.calls(method: String, path: String): List<Request> =
    requests.filter { it.method == method && it.url.encodedPath == path }

/** A rate-limit answer with `retry-after` in seconds. */
fun rateLimited(code: Int = 403, retryAfterSeconds: Long) =
    Scripted(code, "{}", mapOf("retry-after" to retryAfterSeconds.toString()))

/** A rate-limit answer with no requests left until [epochSeconds]. */
fun rateLimitReset(code: Int = 429, epochSeconds: Long) =
    Scripted(code, "{}", mapOf("x-ratelimit-remaining" to "0", "x-ratelimit-reset" to epochSeconds.toString()))

/** One repository in GitHub's REST shape. */
fun repoJson(id: Int, owner: String, name: String, stars: Int = 1000): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("full_name", "$owner/$name")
    putJsonObject("owner") {
        put("login", owner)
        put("id", id + 1)
        put("avatar_url", "")
        put("url", "https://api.github.com/users/$owner")
        put("html_url", "https://github.com/$owner")
        put("type", "User")
    }
    put("html_url", "https://github.com/$owner/$name")
    put("description", "Search result $owner/$name.")
    put("fork", false)
    put("url", "https://api.github.com/repos/$owner/$name")
    put("created_at", "2020-01-01T00:00:00Z")
    put("updated_at", "2026-10-01T00:00:00Z")
    put("pushed_at", "2026-10-01T00:00:00Z")
    put("homepage", JsonNull)
    put("size", 100)
    put("stargazers_count", stars)
    put("watchers_count", stars)
    put("language", "Kotlin")
    put("forks_count", 10)
    put("open_issues_count", 1)
    put("default_branch", "main")
    putJsonArray("topics") {}
    put("visibility", "public")
    put("license", JsonNull)
}

/** A `search/repositories` answer. */
fun searchJson(total: Int, repos: List<JsonObject>): String = buildJsonObject {
    put("total_count", total)
    put("incomplete_results", false)
    put("items", JsonArray(repos))
}.toString()

/** Waits up to [timeoutMillis] for [condition]; fails with [what] when it never holds. */
suspend fun eventually(what: String, timeoutMillis: Long = 5_000, condition: suspend () -> Boolean) {
    try {
        withTimeout(timeoutMillis) { while (!condition()) delay(20) }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError("timed out waiting for: $what")
    }
}
