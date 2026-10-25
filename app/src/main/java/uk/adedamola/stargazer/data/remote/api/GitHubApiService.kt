package uk.adedamola.stargazer.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.remote.model.GitHubUser
import uk.adedamola.stargazer.data.remote.model.StarredRepository

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
     * Get starred repositories with starred_at timestamp for the authenticated user.
     * Uses special Accept header to get the starred_at timestamp.
     * @param page Page number for pagination
     * @param perPage Number of results per page (max 100)
     * @param sort Sort by "created" (when starred) or "updated" (when repo updated)
     * @param direction Sort direction "desc" or "asc"
     */
    @Headers("Accept: application/vnd.github.star+json")
    @GET("user/starred")
    suspend fun getStarredRepositoriesWithTimestamp(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc"
    ): List<StarredRepository>

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

    /**
     * Get the README content for a repository as raw text
     * @param owner Repository owner
     * @param repo Repository name
     */
    @Headers("Accept: application/vnd.github.raw+json")
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): String

    /**
     * Render markdown text as GitHub-Flavored Markdown HTML.
     * @param request The markdown rendering request containing text, mode, and context
     * @return Rendered HTML string
     */
    @Headers("Accept: application/vnd.github+json")
    @POST("markdown")
    suspend fun renderMarkdown(
        @Body request: MarkdownRenderRequest
    ): String

    /**
     * The signed-in account. [authorization] is set only during sign-in, to check a new key
     * before it is stored; otherwise the stored key is used.
     */
    @GET("user")
    suspend fun getAuthenticatedUser(
        @Header("Authorization") authorization: String? = null
    ): GitHubUser

    /**
     * Stars a repository for the signed-in account. GitHub answers 204. The request has no body:
     * Retrofit sends an empty one with `Content-Length: 0`, as GitHub asks for this call.
     */
    @PUT("user/starred/{owner}/{repo}")
    suspend fun starRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Unit>

    /** Removes the signed-in account's star from a repository. GitHub answers 204. */
    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstarRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Unit>

    /** Whether the signed-in account stars a repository: 204 when it does, 404 when it does not. */
    @GET("user/starred/{owner}/{repo}")
    suspend fun isStarred(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Unit>

    /** One repository as GitHub describes it. */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepository

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

@kotlinx.serialization.Serializable
data class MarkdownRenderRequest(
    val text: String,
    val mode: String = "gfm",
    val context: String? = null
)
