package uk.adedamola.stargazer.ui

import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.TokenManager
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import javax.inject.Inject

/**
 * Seeds the app's real storage so Home opens with starred repos and no GitHub account,
 * for screen checks on an emulator. It runs only with `-e seedFixture true`; a normal
 * test run skips it. Keep the emulator offline while the fixture value is stored, and
 * sign out afterwards to remove it.
 */
@HiltAndroidTest
class EdgeToEdgeFixtureSeed {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var repositoryDao: RepositoryDao

    @Before
    fun setUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seedFixture") == "true")
        hiltRule.inject()
    }

    @Test
    fun seed() = runBlocking {
        tokenManager.saveToken("edge-to-edge-fixture")
        // sdlc-debt: relies on the deprecated insert-or-replace DAO call (ceiling: test-only fixture
        // seed, breaks if the DAO drops the method); upgrade path: a DAO helper that resets local
        // flags and upserts, so a reseed never keeps stale favorites or pins.
        @Suppress("DEPRECATION")
        repositoryDao.insertRepositories(fixtureRepos())
    }

    private fun fixtureRepos(): List<RepositoryEntity> {
        val languages = listOf("Kotlin", "Rust", "TypeScript", "Go", null, "Python")
        return (1..30).map { n ->
            val nn = n.toString().padStart(2, '0')
            RepositoryEntity(
                id = 900_000 + n,
                name = "repo-$nn",
                fullName = "fixture/repo-$nn",
                ownerLogin = "fixture",
                ownerAvatarUrl = "",
                htmlUrl = "https://github.com/fixture/repo-$nn",
                description = if (n == 1) "A".repeat(200) else "Fixture repository number $nn for screen checks.",
                fork = false,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2026-10-01T00:00:00Z",
                pushedAt = "2026-10-01T00:00:00Z",
                homepage = null,
                size = 100 * n,
                stargazersCount = n * 1_537,
                watchersCount = n * 41,
                language = languages[n % languages.size],
                forksCount = n * 97,
                openIssuesCount = n,
                defaultBranch = "main",
                topics = "",
                visibility = "public",
                licenseName = "MIT",
                starredAt = 1_760_000_000_000L - n * 86_400_000L,
            )
        }
    }
}
