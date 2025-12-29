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
 * Sync Strategy (Offline-First):
 * - DB is NEVER cleared - it's the single source of truth
 * - On REFRESH: Fetch latest repos and upsert (preserves isFavorite, isPinned)
 * - On APPEND: Fetch next pages and upsert
 * - Local user data (favorites, pins, tags) is always preserved
 * - If network fails, local data remains available
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

    /**
     * Called on first Pager collection to determine if initial refresh is needed.
     * Returns LAUNCH_INITIAL_REFRESH to always fetch fresh data when the Pager starts.
     */
    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RepositoryEntity>
    ): MediatorResult {
        return try {
            val currentTime = System.currentTimeMillis()

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
                // Always upsert repos - updates API fields, preserves local user data
                // NEVER clear the DB - it's the single source of truth
                if (repos.isNotEmpty()) {
                    repoDao.upsertRepositories(repos.map { it.toEntity() })

                    // Update sync metadata
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
