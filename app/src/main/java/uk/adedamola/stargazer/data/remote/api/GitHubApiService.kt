package uk.adedamola.stargazer.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import uk.adedamola.stargazer.data.remote.model.GitHubRepository

interface GitHubApiService {

    /**
     * Get starred repositories for the authenticated user
     * @param page Page number for pagination
     * @param perPage Number of results per page (max 100)
     */
    @GET("user/starred")
    suspend fun getStarredRepositories(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "created"
    ): List<GitHubRepository>

    /**
     * Get starred repositories for a specific user
     * @param username GitHub username
     * @param page Page number for pagination
     * @param perPage Number of results per page (max 100)
     */
    @GET("users/{username}/starred")
    suspend fun getUserStarredRepositories(
        @Path("username") username: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): List<GitHubRepository>

    /**
     * Search repositories
     * @param query Search query
     * @param page Page number for pagination
     * @param perPage Number of results per page (max 100)
     */
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): SearchResponse

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}

@kotlinx.serialization.Serializable
data class SearchResponse(
    @kotlinx.serialization.SerialName("total_count")
    val totalCount: Int,
    @kotlinx.serialization.SerialName("incomplete_results")
    val incompleteResults: Boolean,
    @kotlinx.serialization.SerialName("items")
    val items: List<GitHubRepository>
)
