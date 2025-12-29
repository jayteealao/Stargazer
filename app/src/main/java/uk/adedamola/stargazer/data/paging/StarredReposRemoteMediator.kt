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
 *
 * Sync Strategy:
 * - On REFRESH: Check if we have recent data (within 1 hour)
 * - If recent: Fetch only first page and insert new repos (incremental)
 * - If stale or no data: Clear DB and fetch all (full refresh)
 * - On APPEND: Continue fetching next pages
 */
@OptIn(ExperimentalPagingApi::class)
class StarredReposRemoteMediator @Inject constructor(
    private val apiService: GitHubApiService,
    private val database: AppDatabase
) : RemoteMediator<Int, RepositoryEntity>() {

    companion object {
        private const val DATA_TYPE = "starred_repos"
        private const val GITHUB_PAGE_SIZE = 100
        private const val STALE_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour
    }

    private val repoDao = database.repositoryDao()
    private val syncMetadataDao = database.syncMetadataDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RepositoryEntity>
    ): MediatorResult {
        return try {
            // Check sync metadata to determine if we need full or incremental sync
            val syncMetadata = syncMetadataDao.getMetadata(DATA_TYPE)
            val currentTime = System.currentTimeMillis()
            val isStale = syncMetadata == null ||
                    (currentTime - syncMetadata.lastSyncTimestamp) > STALE_THRESHOLD_MS

            // Determine which page to load
            val page = when (loadType) {
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> {
                    // Don't prepend - GitHub API doesn't support that
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    // For append, we need to get the next page
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

            // Fetch data from GitHub API with descending order by created date
            val repos = apiService.getStarredRepositories(
                page = page,
                perPage = GITHUB_PAGE_SIZE,
                sort = "created"
            )

            // Check if we've reached the end
            val endOfPaginationReached = repos.isEmpty() || repos.size < GITHUB_PAGE_SIZE

            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    if (isStale) {
                        // Full refresh: clear database and insert all
                        repoDao.deleteAll()
                        repoDao.insertRepositories(repos.map { it.toEntity() })
                    } else {
                        // Incremental sync: only insert new repos created after last sync
                        val lastCreatedAt = syncMetadata?.lastItemCreatedAt
                        if (lastCreatedAt != null) {
                            // Filter repos created after our last synced item
                            val newRepos = repos.filter { repo ->
                                repo.createdAt > lastCreatedAt
                            }
                            if (newRepos.isNotEmpty()) {
                                // Insert only new repos
                                repoDao.insertRepositories(newRepos.map { it.toEntity() })
                            }
                        } else {
                            // No timestamp available, insert all
                            repoDao.insertRepositories(repos.map { it.toEntity() })
                        }
                    }
                } else {
                    // APPEND: Always insert all fetched repos
                    repoDao.insertRepositories(repos.map { it.toEntity() })
                }

                // Update sync metadata
                if (repos.isNotEmpty()) {
                    val mostRecentCreatedAt = repos.maxByOrNull { it.createdAt }?.createdAt
                    syncMetadataDao.updateMetadata(
                        SyncMetadata(
                            dataType = DATA_TYPE,
                            lastSyncTimestamp = currentTime,
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
