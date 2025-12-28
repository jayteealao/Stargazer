package uk.adedamola.stargazer.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.mappers.toDomainModel
import uk.adedamola.stargazer.data.mappers.toEntity
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubRepository as GitHubRepoModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val apiService: GitHubApiService,
    private val repositoryDao: RepositoryDao
) : GitHubRepository {

    override fun getStarredRepositories(forceRefresh: Boolean): Flow<Result<List<GitHubRepoModel>>> = flow {
        emit(Result.Loading)

        try {
            // If not forcing refresh, try to get from cache first
            if (!forceRefresh) {
                repositoryDao.getAllRepositories().collect { cachedRepos ->
                    if (cachedRepos.isNotEmpty()) {
                        emit(Result.Success(cachedRepos.map { it.toDomainModel() }))
                    }
                }
            }

            // Fetch from API
            val remoteRepos = apiService.getStarredRepositories(perPage = 100)

            // Cache the results
            repositoryDao.deleteAll()
            repositoryDao.insertRepositories(remoteRepos.map { it.toEntity() })

            // Emit the fresh data
            emit(Result.Success(remoteRepos))
        } catch (e: Exception) {
            // If API fails, try to return cached data
            repositoryDao.getAllRepositories().collect { cachedRepos ->
                if (cachedRepos.isNotEmpty()) {
                    emit(Result.Success(cachedRepos.map { it.toDomainModel() }))
                } else {
                    emit(Result.Error(e))
                }
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
            // Try to find in cache first
            val cachedRepos = repositoryDao.getAllRepositories()
            var foundRepo: GitHubRepoModel? = null
            cachedRepos.collect { repos ->
                foundRepo = repos.find { it.fullName == fullName }?.toDomainModel()
            }
            Result.Success(foundRepo)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun refreshStarredRepositories(): Result<Unit> {
        return try {
            val remoteRepos = apiService.getStarredRepositories(perPage = 100)
            repositoryDao.deleteAll()
            repositoryDao.insertRepositories(remoteRepos.map { it.toEntity() })
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
