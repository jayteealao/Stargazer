package uk.adedamola.stargazer.data.repository

import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import javax.inject.Inject
import javax.inject.Singleton

/** One page of GitHub-wide search results. [total] is GitHub's count for the whole query. */
data class SearchPage(val total: Int, val items: List<GitHubRepository>)

/**
 * Searches all of GitHub for repositories. Repositories already starred on this phone are left
 * out: they show under the local matches. A repository whose unstar is waiting is not starred
 * here, so it stays in the results. One page of [PAGE_SIZE], no paging, because each search
 * spends a point of GitHub's search limit.
 */
@Singleton
class GitHubSearch @Inject constructor(
    private val api: GitHubApiService,
    private val repositoryDao: RepositoryDao,
) {
    suspend fun search(query: String): SearchPage {
        val response = api.searchRepositories(query, page = 1, perPage = PAGE_SIZE)
        val starredHere = repositoryDao.getStarredIdsAmong(response.items.map { it.id }).toSet()
        return SearchPage(response.totalCount, response.items.filter { it.id !in starredHere })
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
