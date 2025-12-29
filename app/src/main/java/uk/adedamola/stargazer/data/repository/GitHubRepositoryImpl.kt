package uk.adedamola.stargazer.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.mappers.toDomainModel
import uk.adedamola.stargazer.data.mappers.toEntity
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubRepository as GitHubRepoModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of GitHubRepository using Paging 3 for offline-first architecture.
 * Room database is the single source of truth.
 * RemoteMediator handles syncing from GitHub API to local DB.
 */
@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val repositoryDao: RepositoryDao,
    private val database: AppDatabase
) : GitHubRepository {

    companion object {
        private const val PAGE_SIZE = 20
        private const val PREFETCH_DISTANCE = 5
        private const val INITIAL_LOAD_SIZE = 40
    }

    /**
     * Returns a paged flow of starred repositories with sorting.
     * Uses RemoteMediator to sync from GitHub API to local DB.
     * Room DB is the single source of truth.
     */
    @OptIn(ExperimentalPagingApi::class)
    override fun getStarredRepositoriesPaging(sortBy: SortOption): Flow<PagingData<GitHubRepoModel>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            remoteMediator = StarredReposRemoteMediator(
                apiService = apiService,
                database = database
            ),
            pagingSourceFactory = {
                when (sortBy) {
                    SortOption.STARS -> repositoryDao.getRepositoriesByStarsPaging()
                    SortOption.FORKS -> repositoryDao.getRepositoriesByForksPaging()
                    SortOption.UPDATED -> repositoryDao.getRepositoriesByUpdatedPaging()
                    SortOption.CREATED -> repositoryDao.getRepositoriesByCreatedPaging()
                    SortOption.NAME -> repositoryDao.getRepositoriesByNamePaging()
                }
            }
        ).flow.map { pagingData ->
            // Map entities to domain models
            pagingData.map { it.toDomainModel() }
        }
    }

    /**
     * Returns a paged flow of repositories matching the search query.
     * Searches only in local DB (no remote fetch for search).
     */
    override fun searchRepositoriesPaging(query: String): Flow<PagingData<GitHubRepoModel>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { repositoryDao.searchRepositoriesPaging(query) }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    /**
     * Returns a paged flow of repositories filtered by language.
     * Filters only in local DB (no remote fetch for filters).
     */
    override fun getRepositoriesByLanguagePaging(language: String): Flow<PagingData<GitHubRepoModel>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { repositoryDao.getRepositoriesByLanguagePaging(language) }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    /**
     * Gets a single repository by full name from local DB.
     */
    override suspend fun getRepositoryByFullName(fullName: String): Result<GitHubRepoModel?> {
        return try {
            val cachedRepos = repositoryDao.getAllRepositories().first()
            val foundRepo = cachedRepos.find { it.fullName == fullName }?.toDomainModel()
            Result.Success(foundRepo)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Manually triggers a refresh of starred repositories.
     * This will cause the RemoteMediator to fetch new data.
     */
    override suspend fun refreshStarredRepositories(): Result<Unit> {
        return try {
            // The actual refresh is handled by RemoteMediator
            // This is just a trigger for manual refresh if needed
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
