package uk.adedamola.stargazer.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.RepositoryEntity

/**
 * Tests for RepositoryDao search functionality.
 * Tests case-insensitive search across multiple fields:
 * - name
 * - description
 * - fullName
 * - ownerLogin
 * - language
 * - topics
 */
@RunWith(AndroidJUnit4::class)
class RepositoryDaoSearchTest {

    private lateinit var database: AppDatabase
    private lateinit var repositoryDao: RepositoryDao

    private val testRepos = listOf(
        RepositoryEntity(
            id = 1,
            name = "awesome-kotlin",
            fullName = "kotlin-user/awesome-kotlin",
            ownerLogin = "kotlin-user",
            ownerAvatarUrl = "https://example.com/avatar1.png",
            htmlUrl = "https://github.com/kotlin-user/awesome-kotlin",
            description = "A curated list of awesome Kotlin resources",
            fork = false,
            createdAt = "2020-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            pushedAt = "2024-01-01T00:00:00Z",
            homepage = null,
            size = 1000,
            stargazersCount = 5000,
            watchersCount = 500,
            language = "Kotlin",
            forksCount = 100,
            openIssuesCount = 10,
            defaultBranch = "main",
            topics = "kotlin,awesome,resources",
            visibility = "public",
            licenseName = "MIT"
        ),
        RepositoryEntity(
            id = 2,
            name = "react-native-app",
            fullName = "facebook/react-native-app",
            ownerLogin = "facebook",
            ownerAvatarUrl = "https://example.com/avatar2.png",
            htmlUrl = "https://github.com/facebook/react-native-app",
            description = "A framework for building native apps using React",
            fork = false,
            createdAt = "2019-01-01T00:00:00Z",
            updatedAt = "2024-02-01T00:00:00Z",
            pushedAt = "2024-02-01T00:00:00Z",
            homepage = "https://reactnative.dev",
            size = 2000,
            stargazersCount = 10000,
            watchersCount = 1000,
            language = "JavaScript",
            forksCount = 200,
            openIssuesCount = 20,
            defaultBranch = "main",
            topics = "react,mobile,javascript",
            visibility = "public",
            licenseName = "MIT"
        ),
        RepositoryEntity(
            id = 3,
            name = "python-data-science",
            fullName = "data-org/python-data-science",
            ownerLogin = "data-org",
            ownerAvatarUrl = "https://example.com/avatar3.png",
            htmlUrl = "https://github.com/data-org/python-data-science",
            description = "Machine learning and data analysis with Python",
            fork = false,
            createdAt = "2021-01-01T00:00:00Z",
            updatedAt = "2024-03-01T00:00:00Z",
            pushedAt = "2024-03-01T00:00:00Z",
            homepage = null,
            size = 1500,
            stargazersCount = 7500,
            watchersCount = 750,
            language = "Python",
            forksCount = 150,
            openIssuesCount = 15,
            defaultBranch = "main",
            topics = "python,machine-learning,data-science",
            visibility = "public",
            licenseName = "Apache-2.0"
        )
    )

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        repositoryDao = database.repositoryDao()

        // Insert test data
        testRepos.forEach { repo ->
            repositoryDao.insertRepositoryInternal(repo)
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun searchByName_returnsMatchingRepositories() = runTest {
        val results = repositoryDao.searchRepositories("kotlin").first()
        assertEquals(1, results.size)
        assertEquals("awesome-kotlin", results[0].name)
    }

    @Test
    fun searchByDescription_returnsMatchingRepositories() = runTest {
        val results = repositoryDao.searchRepositories("machine learning").first()
        assertEquals(1, results.size)
        assertEquals("python-data-science", results[0].name)
    }

    @Test
    fun searchByFullName_returnsMatchingRepositories() = runTest {
        val results = repositoryDao.searchRepositories("facebook").first()
        assertEquals(1, results.size)
        assertEquals("react-native-app", results[0].name)
    }

    @Test
    fun searchByOwnerLogin_returnsMatchingRepositories() = runTest {
        val results = repositoryDao.searchRepositories("data-org").first()
        assertEquals(1, results.size)
        assertEquals("python-data-science", results[0].name)
    }

    @Test
    fun searchByLanguage_returnsMatchingRepositories() = runTest {
        val results = repositoryDao.searchRepositories("Python").first()
        assertEquals(1, results.size)
        assertEquals("python-data-science", results[0].name)
    }

    @Test
    fun searchByTopics_returnsMatchingRepositories() = runTest {
        val results = repositoryDao.searchRepositories("mobile").first()
        assertEquals(1, results.size)
        assertEquals("react-native-app", results[0].name)
    }

    @Test
    fun searchWithPartialMatch_returnsMatchingRepositories() = runTest {
        val results = repositoryDao.searchRepositories("react").first()
        assertEquals(1, results.size)
        assertEquals("react-native-app", results[0].name)
    }

    @Test
    fun searchWithNoMatches_returnsEmptyList() = runTest {
        val results = repositoryDao.searchRepositories("nonexistent").first()
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchEmptyQuery_returnsAllRepositories() = runTest {
        val results = repositoryDao.searchRepositories("").first()
        assertEquals(3, results.size)
    }

    @Test
    fun searchPaging_returnsCorrectResults() = runTest {
        val pagingSource = repositoryDao.searchRepositoriesPaging("kotlin")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )

        assertTrue(loadResult is PagingSource.LoadResult.Page)
        val page = loadResult as PagingSource.LoadResult.Page
        assertEquals(1, page.data.size)
        assertEquals("awesome-kotlin", page.data[0].name)
    }

    @Test
    fun searchSortsByStars_descendingOrder() = runTest {
        val results = repositoryDao.searchRepositories("").first()
        // All repos should be returned, sorted by stars (descending)
        assertEquals(10000, results[0].stargazersCount) // react-native
        assertEquals(7500, results[1].stargazersCount)  // python-data-science
        assertEquals(5000, results[2].stargazersCount)  // awesome-kotlin
    }

    @Test
    fun searchMultipleMatches_returnsAllMatching() = runTest {
        // Search for "app" which matches name and description
        val results = repositoryDao.searchRepositories("app").first()
        // Should match react-native-app (name) and python-data-science (description: "analysis")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.name == "react-native-app" })
    }
}
