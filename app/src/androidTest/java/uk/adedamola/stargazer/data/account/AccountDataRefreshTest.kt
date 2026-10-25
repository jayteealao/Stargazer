package uk.adedamola.stargazer.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.FakeList
import uk.adedamola.stargazer.data.auth.SECOND_USER_ID
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.graphQlApi
import uk.adedamola.stargazer.data.auth.graphQlBody
import uk.adedamola.stargazer.data.auth.graphQlErrorsJson
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.listItemsPageJson
import uk.adedamola.stargazer.data.auth.listsJson
import uk.adedamola.stargazer.data.auth.profileJson
import uk.adedamola.stargazer.data.auth.requestBody
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.RefreshStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The profile and the star lists, refreshed from scripted GitHub answers into the cache: every
 * field, list paging, item paging, failures that keep the cache and the profile, the 401 path, the
 * account guard and single flight.
 */
@RunWith(AndroidJUnit4::class)
class AccountDataRefreshTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var session: AccessSession
    private lateinit var network: ScriptedNetwork
    private val key = fakeKey()
    private val now = 1_761_000_000_000L

    private val fiveLists = listOf(
        FakeList("L1", "Android libraries", listOf(1, 2, 3)),
        FakeList("L2", "Rust tools", listOf(4)),
        FakeList("L3", "Read later", emptyList()),
        FakeList("L4", "Design systems", listOf(5, 6)),
        FakeList("L5", "Compose samples", listOf(7)),
    )

    /** `/user` answers [user]; GraphQL answers [graph] for the first page and [items] for `node(id:)`. */
    private var user: () -> Scripted = { Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds", "Ada Okafor")) }
    private var graph: (Map<String, String?>) -> Scripted = { Scripted(200, listsJson(FIXTURE_USER_ID, 1307, fiveLists)) }
    private var items: (Map<String, String?>) -> Scripted = { Scripted(500) }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        db = inMemoryDatabase()
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "https://avatars.example/a.png", 1L))
        session = AccessSession(keys.tokenManager)
        keys.tokenManager.saveToken(key)
        network = ScriptedNetwork { request -> route(request) }
    }

    @After
    fun tearDown() {
        db.close()
        keys.close()
    }

    private fun route(request: Request): Scripted = when (request.url.encodedPath) {
        "/user" -> user()
        "/graphql" -> {
            val body = graphQlBody(requestBody(request))
            val vars = body["variables"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
            if (body["query"]!!.jsonPrimitive.content.contains("node(id:")) items(vars) else graph(vars)
        }
        else -> Scripted(404)
    }

    private fun refresher() = AccountDataRefresher(
        apiService(keys.tokenManager, network, session),
        graphQlApi(keys.tokenManager, network, session),
        db,
    ) { now }

    private fun graphQlRequests() = network.requests.filter { it.url.encodedPath == "/graphql" }

    @Test
    fun profileStoresEveryFieldAndDropsABlankBlog() = runBlocking<Unit> {
        user = {
            Scripted(
                200,
                profileJson(
                    FIXTURE_USER_ID, "ada-builds", "Ada Okafor", bio = "Android engineer.",
                    followers = 1284, following = 211, publicRepos = 64,
                    location = "Lagos, Nigeria", company = "Fieldwork Labs", blog = ""
                )
            )
        }
        val refresher = refresher()
        refresher.refreshProfile()

        val p = db.gitHubProfileDao().get()!!
        assertEquals(FIXTURE_USER_ID, p.userId)
        assertEquals("ada-builds", p.login)
        assertEquals("Ada Okafor", p.name)
        assertEquals("Android engineer.", p.bio)
        assertEquals(1284, p.followers)
        assertEquals(211, p.following)
        assertEquals(64, p.publicRepos)
        assertEquals("Lagos, Nigeria", p.location)
        assertEquals("Fieldwork Labs", p.company)
        assertNull("blank blog is no blog", p.blog)
        assertEquals("https://github.com/ada-builds", p.htmlUrl)
        assertEquals(now, p.fetchedAt)
        assertEquals(RefreshStatus.Succeeded, refresher.profileStatus.value)
    }

    @Test
    fun listsStoreInOrderWithCountsItemsAndStarCount() = runBlocking<Unit> {
        val refresher = refresher()
        refresher.refresh()

        val lists = db.starListDao().getLists()
        assertEquals(listOf("Android libraries", "Rust tools", "Read later", "Design systems", "Compose samples"), lists.map { it.name })
        assertEquals(listOf(3, 1, 0, 2, 1), lists.map { it.itemCount })
        assertEquals(listOf(0, 1, 2, 3, 4), lists.map { it.position })
        assertEquals(7, itemCount())
        assertEquals(1307, db.gitHubProfileDao().get()!!.starredCount)
        assertEquals(RefreshStatus.Succeeded, refresher.listsStatus.value)
    }

    @Test
    fun aListWithMoreThan100ItemsPagesThroughNodeId() = runBlocking<Unit> {
        val first = (1L..100L).toList()
        graph = { Scripted(200, listsJson(FIXTURE_USER_ID, 120, listOf(FakeList("BIG", "Read later", first, total = 101, hasNextPage = true, endCursor = "c100")))) }
        items = { vars ->
            assertEquals("BIG", vars["id"])
            assertEquals("c100", vars["after"])
            Scripted(200, listItemsPageJson(listOf(101L)))
        }
        refresher().refresh()

        assertEquals(2, graphQlRequests().size)
        assertEquals(101, itemCount())
        assertEquals(101, db.starListDao().getLists().single().itemCount)
    }

    @Test
    fun aSecondPageOfListsIsAskedWithTheCursor() = runBlocking<Unit> {
        graph = { vars ->
            if (vars["after"] == null) {
                Scripted(200, listsJson(FIXTURE_USER_ID, 10, fiveLists.take(2), hasNextPage = true, endCursor = "page-2"))
            } else {
                assertEquals("page-2", vars["after"])
                Scripted(200, listsJson(FIXTURE_USER_ID, 10, fiveLists.drop(2)))
            }
        }
        refresher().refresh()

        assertEquals(2, graphQlRequests().size)
        assertEquals(5, db.starListDao().getLists().size)
    }

    @Test
    fun zeroListsLeaveAnEmptyTableAndSucceed() = runBlocking<Unit> {
        graph = { Scripted(200, listsJson(FIXTURE_USER_ID, 0, emptyList())) }
        val refresher = refresher()
        refresher.refresh()

        assertTrue(db.starListDao().getLists().isEmpty())
        assertEquals(RefreshStatus.Succeeded, refresher.listsStatus.value)
    }

    @Test
    fun listFailuresKeepTheCachedListsAndTheProfileStillRefreshes() = runBlocking<Unit> {
        refresher().refresh()
        val cached = db.starListDao().getLists()
        val failures = listOf(
            "server error" to { _: Map<String, String?> -> Scripted(500, """{"message":"oops"}""") },
            "errors in the answer" to { _: Map<String, String?> -> Scripted(200, graphQlErrorsJson()) },
            "offline" to { _: Map<String, String?> -> Scripted(-1) },
        )
        failures.forEach { (name, answer) ->
            graph = answer
            user = { Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds", "Ada $name")) }
            val refresher = refresher()
            refresher.refresh()

            val status = refresher.listsStatus.value
            assertTrue("$name: $status", status is RefreshStatus.Failed)
            assertEquals(name, name == "offline", (status as RefreshStatus.Failed).offline)
            assertEquals(name, cached, db.starListDao().getLists())
            assertEquals(name, 7, itemCount())
            assertEquals(name, "Ada $name", db.gitHubProfileDao().get()!!.name)
            assertEquals(name, RefreshStatus.Succeeded, refresher.profileStatus.value)
        }
        assertTrue("the key stays for non-401 failures", keys.tokenManager.currentKey() != null)
    }

    @Test
    fun aFailedFollowUpPageKeepsTheWholeCache() = runBlocking<Unit> {
        refresher().refresh()
        val cached = db.starListDao().getLists()
        graph = { Scripted(200, listsJson(FIXTURE_USER_ID, 120, listOf(FakeList("BIG", "Read later", listOf(1L), total = 101, hasNextPage = true, endCursor = "c1")))) }
        items = { Scripted(-1) }
        val refresher = refresher()
        refresher.refresh()

        assertTrue(refresher.listsStatus.value is RefreshStatus.Failed)
        assertEquals(cached, db.starListDao().getLists())
        assertEquals(7, itemCount())
    }

    @Test
    fun a401FromGraphQlEndsAccessOnce() = runBlocking<Unit> {
        graph = { Scripted(401, """{"message":"Bad credentials"}""") }
        val refresher = refresher()
        refresher.refresh()

        assertNull(keys.tokenManager.currentKey())
        assertTrue(session.accessEnded.value)
        assertTrue(refresher.listsStatus.value is RefreshStatus.Failed)
        assertEquals("the profile from before the 401 stays", "Ada Okafor", db.gitHubProfileDao().get()!!.name)
    }

    @Test
    fun anAnswerForAnotherAccountWritesNothing() = runBlocking<Unit> {
        user = { Scripted(200, profileJson(SECOND_USER_ID, "someone-else", "Someone Else")) }
        graph = { Scripted(200, listsJson(SECOND_USER_ID, 5, fiveLists)) }
        refresher().refresh()

        assertNull(db.gitHubProfileDao().get())
        assertTrue(db.starListDao().getLists().isEmpty())
        assertEquals(0, itemCount())
    }

    @Test
    fun aListRemovedOnGitHubLeavesWithItsItems() = runBlocking<Unit> {
        refresher().refresh()
        graph = { Scripted(200, listsJson(FIXTURE_USER_ID, 1307, fiveLists.drop(1))) }
        refresher().refresh()

        assertFalse(db.starListDao().getLists().any { it.id == "L1" })
        assertEquals(4, itemCount())
    }

    @Test
    fun concurrentRefreshesSendOneOfEachRequest() = runBlocking<Unit> {
        val hold = CountDownLatch(1)
        user = {
            hold.await(5, TimeUnit.SECONDS)
            Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds", "Ada Okafor"))
        }
        val refresher = refresher()
        val first = async(Dispatchers.IO) { refresher.refresh() }
        delay(200)
        val second = async(Dispatchers.IO) { refresher.refresh() }
        delay(300)
        hold.countDown()
        listOf(first, second).awaitAll()

        assertEquals(1, network.requests.count { it.url.encodedPath == "/user" })
        assertEquals(1, graphQlRequests().size)
    }

    @Test
    fun everyGraphQlRequestAsksForPagesOf100AndCarriesTheKeyOnlyAsBearer() = runBlocking<Unit> {
        graph = { Scripted(200, listsJson(FIXTURE_USER_ID, 120, listOf(FakeList("BIG", "Read later", listOf(1L), total = 101, hasNextPage = true, endCursor = "c1")))) }
        items = { Scripted(200, listItemsPageJson(listOf(2L))) }
        refresher().refresh()

        val requests = graphQlRequests()
        assertEquals(2, requests.size)
        requests.forEach { request ->
            val body = requestBody(request)
            val query = graphQlBody(body)["query"]!!.jsonPrimitive.content
            assertTrue(query, query.contains("first: 100"))
            assertEquals("Bearer $key", request.header("Authorization"))
            assertFalse("no key in the body", body.contains(key))
            assertFalse(Regex("gh[uro]_").containsMatchIn(body))
        }
    }

    private fun itemCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM star_list_items").use { c -> c.moveToFirst(); c.getInt(0) }
}
