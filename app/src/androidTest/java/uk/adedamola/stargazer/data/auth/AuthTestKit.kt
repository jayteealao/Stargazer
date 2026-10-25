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
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.local.database.StarListItemEntity
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.api.GitHubGraphQlApi
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
}

/** Every table that holds account-derived data (the wipe set); `account` is the record itself. */
val WIPE_TABLES = listOf(
    "repositories", "tags", "repository_tags", "search_presets", "sync_metadata",
    "github_profile", "star_lists", "star_list_items"
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
