package uk.adedamola.stargazer.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.mappers.toEntity
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import javax.inject.Inject

/**
 * RemoteMediator for starred repositories.
 * Handles fetching data from GitHub API and storing in local database.
 * Implements incremental sync strategy based on created_at timestamps.
 */
@OptIn(ExperimentalPagingApi::class)
class StarredReposRemoteMediator @Inject constructor(
    private val apiService: GitHubApiService,
    private val database: AppDatabase
) : RemoteMediator<Int, RepositoryEntity>() {

    companion object {
        private const val DATA_TYPE = "starred_repos"
        private const val GITHUB_PAGE_SIZE = 100
    }

    private val repoDao = database.repositoryDao()
    private val syncMetadataDao = database.syncMetadataDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RepositoryEntity>
    ): MediatorResult {
        return try {
            // Determine which page to load
            val page = when (loadType) {
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> {
                    // Don't prepend - GitHub API doesn't support that
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    // For append, we need to get the next page
                    // The page number is embedded in the pagination state
                    val lastItem = state.lastItemOrNull()
                    if (lastItem == null) {
                        1
                    } else {
                        // Estimate page number based on item position
                        val position = state.pages.sumOf { it.data.size }
                        (position / GITHUB_PAGE_SIZE) + 1
                    }
                }
            }

            // Fetch data from GitHub API
            val repos = apiService.getStarredRepositories(
                page = page,
                perPage = GITHUB_PAGE_SIZE
            )

            // Check if we've reached the end
            val endOfPaginationReached = repos.isEmpty() || repos.size < GITHUB_PAGE_SIZE

            database.withTransaction {
                // On refresh, clear the database
                if (loadType == LoadType.REFRESH) {
                    repoDao.deleteAll()
                }

                // Insert new repos (REPLACE strategy handles updates)
                repoDao.insertRepositories(repos.map { it.toEntity() })

                // Update sync metadata
                if (repos.isNotEmpty()) {
                    val mostRecentCreatedAt = repos.maxByOrNull { it.createdAt }?.createdAt
                    syncMetadataDao.updateMetadata(
                        SyncMetadata(
                            dataType = DATA_TYPE,
                            lastSyncTimestamp = System.currentTimeMillis(),
                            lastItemCreatedAt = mostRecentCreatedAt
                        )
                    )
                }
            }

            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
