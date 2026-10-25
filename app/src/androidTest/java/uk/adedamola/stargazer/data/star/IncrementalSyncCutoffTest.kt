package uk.adedamola.stargazer.data.star

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.apiService
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.fixtureRepo
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.starredPages
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator

/**
 * A repo starred in the app carries the phone's time, which can be later than stars made on
 * github.com that the phone has not read yet. The next sync still reads those.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class IncrementalSyncCutoffTest {

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private var listed = listOf(900_003, 900_004)
    private val network = ScriptedNetwork { request -> starredPages(listed)(request) ?: Scripted(404, "{}") }

    @Before
    fun setUp() = runBlocking<Unit> {
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        keys.close()
    }

    private fun refresh(mediator: StarredReposRemoteMediator) = runBlocking {
        mediator.load(LoadType.REFRESH, PagingState<Int, RepositoryEntity>(emptyList(), null, PagingConfig(pageSize = 30), 0))
    }

    @Test
    fun aStarInTheAppDoesNotHideAnEarlierStarMadeOnGitHub() = runBlocking<Unit> {
        val mediator = StarredReposRemoteMediator(apiService(keys.tokenManager, network), db)
        refresh(mediator)

        // Starred in the app "now": later than every star GitHub has listed so far.
        db.repositoryDao().upsertRepositories(listOf(fixtureRepo(10).copy(starredAt = System.currentTimeMillis())))
        // Meanwhile starred on github.com: newer than the last sync, older than the app's star.
        listed = listOf(900_001, 900_003, 900_004)
        refresh(mediator)

        assertNotNull("the star made on github.com is read", db.repositoryDao().getRepositoryById(900_001))
    }
}
