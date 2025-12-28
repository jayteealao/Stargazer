package uk.adedamola.stargazer.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.mappers.toDomainModel
import uk.adedamola.stargazer.data.mappers.toEntity
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubRepository as GitHubRepoModel
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val repositoryDao: RepositoryDao
) : GitHubRepository {

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val RATE_LIMIT_STATUS_CODE = 403
        private const val RATE_LIMIT_RETRY_AFTER_MS = 60000L // 1 minute
    }

    /**
     * Converts technical exceptions into user-friendly error messages
     */
    private fun getUserFriendlyErrorMessage(exception: Exception): String {
        return when (exception) {
            is UnknownHostException -> "No internet connection. Please check your network."
            is IOException -> "Network error. Please try again."
            is HttpException -> when (exception.code()) {
                401 -> "Authentication failed. Please check your GitHub token."
                403 -> "Access forbidden. You may have exceeded the GitHub API rate limit."
                404 -> "Resource not found."
                500, 502, 503, 504 -> "GitHub server error. Please try again later."
                else -> "HTTP error: ${exception.code()}"
            }
            else -> exception.message ?: "An unknown error occurred"
        }
    }

    /**
     * Retries an API call with exponential backoff on failure.
     * Handles GitHub rate limiting with special retry logic.
     */
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = MAX_RETRIES,
        block: suspend () -> T
    ): T {
        var currentRetry = 0
        var lastException: Exception? = null

        while (currentRetry <= maxRetries) {
            try {
                return block()
            } catch (e: HttpException) {
                lastException = e
                // Check if it's a rate limit error (403)
                if (e.code() == RATE_LIMIT_STATUS_CODE) {
                    if (currentRetry < maxRetries) {
                        // For rate limiting, wait longer
                        delay(RATE_LIMIT_RETRY_AFTER_MS)
                        currentRetry++
                    } else {
                        throw Exception("GitHub API rate limit exceeded. Please try again later.", e)
                    }
                } else if (currentRetry < maxRetries) {
                    // Exponential backoff for other errors
                    val backoffTime = INITIAL_BACKOFF_MS * (1 shl currentRetry) // 2^currentRetry
                    delay(backoffTime)
                    currentRetry++
                } else {
                    throw e
                }
            } catch (e: Exception) {
                if (currentRetry < maxRetries) {
                    lastException = e
                    val backoffTime = INITIAL_BACKOFF_MS * (1 shl currentRetry)
                    delay(backoffTime)
                    currentRetry++
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: Exception("Unknown error occurred")
    }

    /**
     * Retrieves all starred repositories for the authenticated user.
     * Implements pagination to fetch all starred repos regardless of count.
     * Uses caching strategy: returns cached data first (if available and not forcing refresh),
     * then fetches from API and updates cache.
     *
     * @param forceRefresh If true, skips cache and always fetches from API
     * @return Flow emitting Result states (Loading, Success, or Error)
     */
    override fun getStarredRepositories(forceRefresh: Boolean): Flow<Result<List<GitHubRepoModel>>> = flow {
        emit(Result.Loading)

        try {
            // If not forcing refresh, try to get from cache first
            if (!forceRefresh) {
                val cachedRepos = repositoryDao.getAllRepositories().first()
                if (cachedRepos.isNotEmpty()) {
                    emit(Result.Success(cachedRepos.map { it.toDomainModel() }))
                }
            }

            // Fetch all pages from API
            val allRepos = mutableListOf<GitHubRepoModel>()
            var page = 1
            var hasMore = true

            while (hasMore) {
                val pageRepos = retryWithBackoff {
                    apiService.getStarredRepositories(page = page, perPage = 100)
                }
                if (pageRepos.isEmpty()) {
                    hasMore = false
                } else {
                    allRepos.addAll(pageRepos)
                    page++
                    // GitHub API max is 100 items per page
                    // If we got less than 100, we're on the last page
                    if (pageRepos.size < 100) {
                        hasMore = false
                    }
                }
            }

            // Cache the results
            repositoryDao.deleteAll()
            repositoryDao.insertRepositories(allRepos.map { it.toEntity() })

            // Emit the fresh data
            emit(Result.Success(allRepos))
        } catch (e: Exception) {
            // If API fails, try to return cached data
            val cachedRepos = repositoryDao.getAllRepositories().first()
            if (cachedRepos.isNotEmpty()) {
                emit(Result.Success(cachedRepos.map { it.toDomainModel() }))
            } else {
                val userMessage = getUserFriendlyErrorMessage(e)
                emit(Result.Error(Exception(userMessage, e)))
            }
        }
    }

    override fun searchRepositories(query: String): Flow<Result<List<GitHubRepoModel>>> {
        return repositoryDao.searchRepositories(query).map { entities ->
            Result.Success(entities.map { it.toDomainModel() })
        }
    }

    override fun getRepositoriesByLanguage(language: String): Flow<Result<List<GitHubRepoModel>>> {
        return repositoryDao.getRepositoriesByLanguage(language).map { entities ->
            Result.Success(entities.map { it.toDomainModel() })
        }
    }

    override suspend fun getRepositoryByFullName(fullName: String): Result<GitHubRepoModel?> {
        return try {
            // Try to find in cache first - use first() to get current value
            val cachedRepos = repositoryDao.getAllRepositories().first()
            val foundRepo = cachedRepos.find { it.fullName == fullName }?.toDomainModel()
            Result.Success(foundRepo)
        } catch (e: Exception) {
            val userMessage = getUserFriendlyErrorMessage(e)
            Result.Error(Exception(userMessage, e))
        }
    }

    override suspend fun refreshStarredRepositories(): Result<Unit> {
        return try {
            // Fetch all pages
            val allRepos = mutableListOf<GitHubRepoModel>()
            var page = 1
            var hasMore = true

            while (hasMore) {
                val pageRepos = retryWithBackoff {
                    apiService.getStarredRepositories(page = page, perPage = 100)
                }
                if (pageRepos.isEmpty()) {
                    hasMore = false
                } else {
                    allRepos.addAll(pageRepos)
                    page++
                    if (pageRepos.size < 100) {
                        hasMore = false
                    }
                }
            }

            repositoryDao.deleteAll()
            repositoryDao.insertRepositories(allRepos.map { it.toEntity() })
            Result.Success(Unit)
        } catch (e: Exception) {
            val userMessage = getUserFriendlyErrorMessage(e)
            Result.Error(Exception(userMessage, e))
        }
    }
}
