package uk.adedamola.stargazer.ui

import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.TokenManager
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.GitHubProfileDao
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.StarListDao
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.local.database.StarListItemEntity
import javax.inject.Inject

/**
 * Seeds the app's real storage with a signed-in fixture account, 30 starred repos, a cached
 * profile and star lists, so the drawer and the profile screen can be driven on an emulator with
 * no GitHub account. It runs only with `-e profileFixture default|worst|none`; a normal test run
 * skips it. `none` seeds the key, the account and the repos only. Keep the emulator offline while
 * the fixture value is stored, and sign out afterwards to remove it.
 */
@HiltAndroidTest
class ProfileFixtureSeed {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var accountDao: AccountDao
    @Inject lateinit var repositoryDao: RepositoryDao
    @Inject lateinit var profileDao: GitHubProfileDao
    @Inject lateinit var starListDao: StarListDao

    private val fixture: String? = InstrumentationRegistry.getArguments().getString("profileFixture")

    @Before
    fun setUp() {
        // Skip before injecting: a second injection opens a second key store and crashes the run.
        assumeTrue(fixture in setOf("default", "worst", "none"))
        hiltRule.inject()
    }

    @Test
    fun seed() = runBlocking<Unit> {
        tokenManager.saveToken("profile-lists-fixture")
        val now = System.currentTimeMillis()
        val worst = fixture == "worst"
        val login = if (worst) WORST_LOGIN else "ada-builds"
        accountDao.replace(AccountRecord(FIXTURE_USER_ID, login, "", now))
        // sdlc-debt: relies on the deprecated insert-or-replace DAO call (ceiling: test-only fixture
        // seed, breaks if the DAO drops the method); upgrade path: a DAO upsert helper.
        @Suppress("DEPRECATION")
        repositoryDao.insertRepositories((1..30).map(::repo))
        if (fixture == "none") {
            profileDao.deleteAll()
            starListDao.deleteAll()
            return@runBlocking
        }
        profileDao.replace(if (worst) worstProfile(now) else defaultProfile(now))
        val lists = if (worst) worstLists(now) else defaultLists(now)
        starListDao.replaceAll(lists.map { it.first }, lists.flatMap { (list, ids) -> ids.map { StarListItemEntity(list.id, it) } })
    }

    private fun defaultProfile(now: Long) = GitHubProfileEntity(
        userId = FIXTURE_USER_ID, login = "ada-builds", name = "Ada Okafor", avatarUrl = "",
        bio = "Android engineer. I collect libraries, then forget them. Stargazer helps me remember.",
        followers = 1284, following = 211, publicRepos = 64, starredCount = 1307,
        location = "Lagos, Nigeria", company = "Fieldwork Labs", blog = "ada.example.com",
        htmlUrl = "https://github.com/ada-builds", fetchedAt = now - TWO_HOURS
    )

    private fun worstProfile(now: Long) = GitHubProfileEntity(
        userId = FIXTURE_USER_ID, login = WORST_LOGIN, name = "Aleksandra Wiśniewska-Kowalczyk", avatarUrl = "",
        bio = WORST_BIO, followers = 1_300_000, following = 0, publicRepos = 1000, starredCount = 48_912,
        location = "Szczebrzeszyn, Lubelskie Voivodeship, Poland",
        company = "@an-organization-with-a-long-handle and @second-org",
        blog = "https://very-long-personal-domain.example.org/about/me",
        htmlUrl = "https://github.com/$WORST_LOGIN", fetchedAt = now - TWO_HOURS
    )

    /** The five board lists; Android libraries holds repos 1 to 10. */
    private fun defaultLists(now: Long): List<Pair<StarListEntity, List<Long>>> = listOf(
        "Android libraries" to (1..10), "Rust tools" to (11..14), "Read later" to (15..25),
        "Design systems" to (26..27), "Compose samples" to (28..30)
    ).mapIndexed { i, (name, range) ->
        val ids = range.map { 900_000L + it }
        StarListEntity("fixture-list-$i", name, null, false, ids.size, i, now) to ids
    }

    /** 30 lists: a long name, counts of 0 and 999. */
    private fun worstLists(now: Long): List<Pair<StarListEntity, List<Long>>> = (0 until 30).map { i ->
        val name = when (i) {
            0 -> "Benachrichtigungseinstellungen and other very long list names"
            1 -> "Read later"
            else -> "List %02d".format(i + 1)
        }
        val count = when (i) { 0 -> 999; 1 -> 0; else -> 100 + i }
        StarListEntity("fixture-list-$i", name, null, false, count, i, now) to (if (i == 1) emptyList() else listOf(900_001L + i % 30))
    }

    private fun repo(n: Int): RepositoryEntity {
        val nn = n.toString().padStart(2, '0')
        return RepositoryEntity(
            id = 900_000 + n, name = "repo-$nn", fullName = "fixture/repo-$nn", ownerLogin = "fixture",
            ownerAvatarUrl = "", htmlUrl = "https://github.com/fixture/repo-$nn",
            description = "Fixture repository number $nn for screen checks.", fork = false,
            createdAt = "2024-01-01T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z", pushedAt = "2026-10-01T00:00:00Z",
            homepage = null, size = 100 * n, stargazersCount = n * 1_537, watchersCount = n * 41, language = "Kotlin",
            forksCount = n * 97, openIssuesCount = n, defaultBranch = "main", topics = "", visibility = "public",
            licenseName = "MIT", starredAt = 1_760_000_000_000L - n * 86_400_000L,
        )
    }

    private companion object {
        const val TWO_HOURS = 2 * 60 * 60 * 1000L
        const val WORST_LOGIN = "aleksandra-wisniewska-kowalczyk-dev-01"
        val WORST_BIO = ("Staff engineer · Kotlin, Rust, Compose · Benachrichtigungseinstellungen-Refactoring · " +
            "speaker · maintainer of 40+ libraries · opinions are mine · https://very-long.example").take(160)
    }
}
