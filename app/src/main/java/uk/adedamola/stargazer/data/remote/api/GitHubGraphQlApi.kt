package uk.adedamola.stargazer.data.remote.api

import retrofit2.http.Body
import retrofit2.http.POST
import uk.adedamola.stargazer.data.remote.model.GraphQlRequest
import uk.adedamola.stargazer.data.remote.model.GraphQlResponse
import uk.adedamola.stargazer.data.remote.model.ListItemsData
import uk.adedamola.stargazer.data.remote.model.StarListsData

/**
 * GitHub's GraphQL endpoint, on the same authenticated client as the REST API: it sends the
 * stored key, and a 401 ends access the same way.
 */
interface GitHubGraphQlApi {
    @POST("graphql")
    suspend fun starLists(@Body request: GraphQlRequest): GraphQlResponse<StarListsData>

    @POST("graphql")
    suspend fun listItems(@Body request: GraphQlRequest): GraphQlResponse<ListItemsData>
}
