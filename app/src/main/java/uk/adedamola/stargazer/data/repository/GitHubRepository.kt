package uk.adedamola.stargazer.data.repository

import kotlinx.coroutines.flow.Flow
import uk.adedamola.stargazer.data.remote.model.GitHubRepository as GitHubRepoModel

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

/**
 * Cache policy for data fetching operations.
 */
enum class CachePolicy {
    /** Use cached data if fresh, background refresh if stale, network only if empty */
    CACHE_FIRST,
    /** Always fetch from network, use cache as fallback on error */
    NETWORK_FIRST,
    /** Force network fetch, show loading state */
    FORCE_REFRESH
}

interface GitHubRepository {
    fun getStarredRepositories(cachePolicy: CachePolicy = CachePolicy.CACHE_FIRST): Flow<Result<List<GitHubRepoModel>>>
    fun searchRepositories(query: String): Flow<Result<List<GitHubRepoModel>>>
    fun getRepositoriesByLanguage(language: String): Flow<Result<List<GitHubRepoModel>>>
    suspend fun getRepositoryByFullName(fullName: String): Result<GitHubRepoModel?>
    suspend fun refreshStarredRepositories(): Result<Unit>
}
