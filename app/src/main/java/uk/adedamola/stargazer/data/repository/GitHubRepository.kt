package uk.adedamola.stargazer.data.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import uk.adedamola.stargazer.data.paging.SyncProgress
import uk.adedamola.stargazer.data.remote.model.GitHubRepository as GitHubRepoModel

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

interface GitHubRepository {
    // Sync progress for UI to observe
    val syncProgress: StateFlow<SyncProgress>

    // Paging methods - return PagingData for UI consumption
    // Uses RemoteMediator to sync from API on first load
    fun getStarredRepositoriesPaging(sortBy: SortOption = SortOption.STARRED): Flow<PagingData<GitHubRepoModel>>
    fun searchRepositoriesPaging(query: String): Flow<PagingData<GitHubRepoModel>>
    fun getRepositoriesByLanguagePaging(language: String): Flow<PagingData<GitHubRepoModel>>

    // Non-paging methods for single items
    suspend fun getRepositoryByFullName(fullName: String): Result<GitHubRepoModel?>

    // Trigger manual refresh
    suspend fun refreshStarredRepositories(): Result<Unit>
}
