package uk.adedamola.stargazer.data.repository

import kotlinx.coroutines.flow.Flow
import uk.adedamola.stargazer.data.remote.model.GitHubRepository as GitHubRepoModel

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

interface GitHubRepository {
    fun getStarredRepositories(forceRefresh: Boolean = false): Flow<Result<List<GitHubRepoModel>>>
    fun searchRepositories(query: String): Flow<Result<List<GitHubRepoModel>>>
    fun getRepositoriesByLanguage(language: String): Flow<Result<List<GitHubRepoModel>>>
    suspend fun getRepositoryByFullName(fullName: String): Result<GitHubRepoModel?>
    suspend fun refreshStarredRepositories(): Result<Unit>
}
