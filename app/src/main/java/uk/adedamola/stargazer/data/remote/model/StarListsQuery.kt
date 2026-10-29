package uk.adedamola.stargazer.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The signed-in account's GitHub star lists (read-only), a page of lists with the first page of
 * each list's repositories. Star lists have no REST endpoint, so this is a GraphQL query.
 */
const val STAR_LISTS_QUERY =
    "query(\$after: String) { viewer { databaseId starredRepositories { totalCount } " +
        "lists(first: 100, after: \$after) { pageInfo { hasNextPage endCursor } " +
        "nodes { id name description isPrivate items(first: 100) { totalCount " +
        "pageInfo { hasNextPage endCursor } nodes { ... on Repository { databaseId } } } } } } }"

/** The next page of one list's repositories, for a list with more than 100. */
const val LIST_ITEMS_QUERY =
    "query(\$id: ID!, \$after: String) { node(id: \$id) { ... on UserList { " +
        "items(first: 100, after: \$after) { pageInfo { hasNextPage endCursor } " +
        "nodes { ... on Repository { databaseId } } } } } }"

/** A GraphQL request. A variable left out of [variables] is null to GitHub. */
@Serializable
data class GraphQlRequest(val query: String, val variables: JsonObject = JsonObject(emptyMap())) {
    companion object {
        fun starLists(after: String?) = GraphQlRequest(
            STAR_LISTS_QUERY,
            buildJsonObject { after?.let { put("after", it) } }
        )

        fun listItems(listId: String, after: String) = GraphQlRequest(
            LIST_ITEMS_QUERY,
            buildJsonObject {
                put("id", listId)
                put("after", after)
            }
        )
    }
}

/** One GraphQL answer: [data] is null, or [errors] is set, when GitHub could not answer. */
@Serializable
data class GraphQlResponse<T>(val data: T? = null, val errors: List<GraphQlError>? = null)

@Serializable
data class GraphQlError(val message: String? = null, val type: String? = null)

@Serializable
data class StarListsData(val viewer: Viewer? = null)

@Serializable
data class ListItemsData(val node: ListNode? = null)

@Serializable
data class Viewer(
    val databaseId: Long? = null,
    val starredRepositories: CountOnly? = null,
    val lists: ListsConnection? = null
)

@Serializable
data class CountOnly(val totalCount: Int = 0)

@Serializable
data class PageInfo(val hasNextPage: Boolean = false, val endCursor: String? = null)

@Serializable
data class ListsConnection(val pageInfo: PageInfo = PageInfo(), val nodes: List<ListNode?> = emptyList())

@Serializable
data class ListNode(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val isPrivate: Boolean = false,
    val items: ItemsConnection? = null
)

@Serializable
data class ItemsConnection(
    val totalCount: Int = 0,
    val pageInfo: PageInfo = PageInfo(),
    val nodes: List<ItemNode?> = emptyList()
)

/** A list entry; [databaseId] is null for anything that is not a repository. */
@Serializable
data class ItemNode(val databaseId: Long? = null)
