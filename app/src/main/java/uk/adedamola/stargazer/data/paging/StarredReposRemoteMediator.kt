package uk.adedamola.stargazer.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.mappers.toEntity
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the current sync progress state.
 */
data class SyncProgress(
    val phase: SyncPhase = SyncPhase.IDLE,
    val loadedCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class SyncPhase {
    IDLE,
    INITIAL_SYNC,      // First-time full sync of all starred repos
    INCREMENTAL_SYNC,  // Fetching only newly starred repos
    COMPLETE
}

/**
 * RemoteMediator for starred repositories with full sync strategy.
 *
 * Sync Strategy:
 * - Initial sync: Fetch ALL pages from GitHub API sequentially
 * - Incremental sync: Fetch only repos starred after our newest local starred_at
 * - Progress is emitted via syncProgress StateFlow
 * - Database is never cleared - preserves user data (favorites, pins, tags)
 */
@OptIn(ExperimentalPagingApi::class)
@Singleton
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

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    /**
     * Always trigger initial refresh to check for new starred repos.
     */
    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RepositoryEntity>
    ): MediatorResult {
        return try {
            when (loadType) {
                LoadType.REFRESH -> handleRefresh()
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> MediatorResult.Success(endOfPaginationReached = true)
            }
        } catch (e: Exception) {
            _syncProgress.value = _syncProgress.value.copy(
                isLoading = false,
                error = e.message
            )
            MediatorResult.Error(e)
        }
    }

    private suspend fun handleRefresh(): MediatorResult {
        val currentTime = System.currentTimeMillis()
        val metadata = syncMetadataDao.getMetadata(DATA_TYPE)

        return if (metadata?.isInitialSyncComplete == true) {
            performIncrementalSync(currentTime, metadata)
        } else {
            performInitialSync(currentTime)
        }
    }

    /**
     * Initial sync: Fetch ALL starred repos from GitHub.
     * Fetches pages sequentially, inserting each batch immediately.
     * UI updates progressively as data arrives.
     */
    private suspend fun performInitialSync(currentTime: Long): MediatorResult {
        _syncProgress.value = SyncProgress(
            phase = SyncPhase.INITIAL_SYNC,
            loadedCount = 0,
            isLoading = true
        )

        var page = 1
        var totalLoaded = 0
        var newestStarredAt: Long? = null

        while (true) {
            val repos = apiService.getStarredRepositoriesWithTimestamp(
                page = page,
                perPage = GITHUB_PAGE_SIZE,
                sort = "created",
                direction = "desc"
            )

            if (repos.isEmpty()) break

            // Convert and insert immediately for progressive UI updates
            val entities = repos.map { it.toEntity() }
            repoDao.upsertRepositories(entities)

            // Track the newest starred_at from first page
            if (page == 1 && entities.isNotEmpty()) {
                newestStarredAt = entities.maxOfOrNull { it.starredAt ?: 0L }
            }

            totalLoaded += repos.size
            _syncProgress.value = _syncProgress.value.copy(loadedCount = totalLoaded)

            // Check if we've reached the end
            if (repos.size < GITHUB_PAGE_SIZE) break

            page++
        }

        // Mark initial sync as complete
        syncMetadataDao.updateMetadata(
            SyncMetadata(
                dataType = DATA_TYPE,
                lastSyncTimestamp = currentTime,
                lastItemCreatedAt = null,
                isInitialSyncComplete = true,
                newestStarredAt = newestStarredAt
            )
        )

        _syncProgress.value = SyncProgress(
            phase = SyncPhase.COMPLETE,
            loadedCount = totalLoaded,
            isLoading = false
        )

        return MediatorResult.Success(endOfPaginationReached = true)
    }

    /**
     * Incremental sync: Fetch only newly starred repos.
     * Stops when we encounter a repo with starred_at <= our newest local starred_at.
     */
    private suspend fun performIncrementalSync(
        currentTime: Long,
        metadata: SyncMetadata
    ): MediatorResult {
        _syncProgress.value = SyncProgress(
            phase = SyncPhase.INCREMENTAL_SYNC,
            loadedCount = 0,
            isLoading = true
        )

        // Get the newest starred_at from local database
        val localNewestStarredAt = repoDao.getNewestStarredAt() ?: metadata.newestStarredAt ?: 0L

        var page = 1
        var totalLoaded = 0
        var newestStarredAt = localNewestStarredAt
        var shouldContinue = true

        while (shouldContinue) {
            val repos = apiService.getStarredRepositoriesWithTimestamp(
                page = page,
                perPage = GITHUB_PAGE_SIZE,
                sort = "created",
                direction = "desc"
            )

            if (repos.isEmpty()) break

            val entities = repos.map { it.toEntity() }

            // Find repos that are newer than what we have locally
            val newRepos = entities.filter { entity ->
                val starredAt = entity.starredAt ?: 0L
                starredAt > localNewestStarredAt
            }

            if (newRepos.isNotEmpty()) {
                repoDao.upsertRepositories(newRepos)
                totalLoaded += newRepos.size
                _syncProgress.value = _syncProgress.value.copy(loadedCount = totalLoaded)

                // Update newest starred_at if we found newer repos
                val pageNewest = newRepos.maxOfOrNull { it.starredAt ?: 0L } ?: 0L
                if (pageNewest > newestStarredAt) {
                    newestStarredAt = pageNewest
                }
            }

            // Stop if we found repos older than our cutoff (we've caught up)
            val hasOlderRepos = entities.any { entity ->
                val starredAt = entity.starredAt ?: 0L
                starredAt <= localNewestStarredAt
            }

            if (hasOlderRepos || repos.size < GITHUB_PAGE_SIZE) {
                shouldContinue = false
            } else {
                page++
            }
        }

        // Update metadata with new newest starred_at
        syncMetadataDao.updateMetadata(
            metadata.copy(
                lastSyncTimestamp = currentTime,
                newestStarredAt = newestStarredAt
            )
        )

        _syncProgress.value = SyncProgress(
            phase = SyncPhase.COMPLETE,
            loadedCount = totalLoaded,
            isLoading = false
        )

        return MediatorResult.Success(endOfPaginationReached = true)
    }
}
