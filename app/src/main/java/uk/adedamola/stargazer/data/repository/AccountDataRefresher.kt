package uk.adedamola.stargazer.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.local.database.StarListItemEntity
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.api.GitHubGraphQlApi
import uk.adedamola.stargazer.data.remote.model.GitHubUser
import uk.adedamola.stargazer.data.remote.model.GraphQlRequest
import uk.adedamola.stargazer.data.remote.model.ItemNode
import uk.adedamola.stargazer.data.remote.model.ListNode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The last refresh of one kind of account data. */
sealed interface RefreshStatus {
    data object Idle : RefreshStatus
    data object Refreshing : RefreshStatus
    data object Succeeded : RefreshStatus

    /** [offline] is true when the request never reached GitHub. */
    data class Failed(val offline: Boolean, val at: Long) : RefreshStatus
}

/**
 * Refreshes the signed-in account's GitHub profile and star lists into the local cache, which the
 * drawer and the profile screen read offline. The profile and the lists fail on their own: a lists
 * failure never touches the profile. A refresh that is already running is joined, not repeated.
 * Nothing is written for an account other than the recorded one, so a refresh that races an
 * account change never puts the old account's data back.
 */
@Singleton
class AccountDataRefresher(
    private val api: GitHubApiService,
    private val graphQl: GitHubGraphQlApi,
    private val database: AppDatabase,
    private val clock: () -> Long
) {
    @Inject
    constructor(api: GitHubApiService, graphQl: GitHubGraphQlApi, database: AppDatabase) :
        this(api, graphQl, database, System::currentTimeMillis)

    private val _profileStatus = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
    val profileStatus: StateFlow<RefreshStatus> = _profileStatus.asStateFlow()

    private val _listsStatus = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
    val listsStatus: StateFlow<RefreshStatus> = _listsStatus.asStateFlow()

    private val singleFlight = SingleFlight { }

    /** Refreshes the profile, then the lists. A call while one runs waits for that one. */
    suspend fun refresh() = singleFlight.run {
        refreshProfile()
        refreshLists()
    }

    suspend fun refreshProfile() {
        _profileStatus.value = RefreshStatus.Refreshing
        _profileStatus.value = attempt {
            val user = api.getAuthenticatedUser()
            database.withTransaction {
                if (database.accountDao().get()?.userId != user.id) return@withTransaction
                val dao = database.gitHubProfileDao()
                val keptStars = dao.get()?.takeIf { it.userId == user.id }?.starredCount
                dao.replace(user.toProfile(keptStars, clock()))
            }
        }
    }

    suspend fun refreshLists() {
        _listsStatus.value = RefreshStatus.Refreshing
        _listsStatus.value = attempt {
            val lists = mutableListOf<ListNode>()
            var viewerId: Long? = null
            var starred: Int? = null
            var after: String? = null
            do {
                val response = graphQl.starLists(GraphQlRequest.starLists(after))
                val viewer = response.data?.viewer
                if (!response.errors.isNullOrEmpty() || viewer?.lists == null) {
                    throw GraphQlFailure("GitHub did not return the star lists")
                }
                viewerId = viewer.databaseId
                starred = viewer.starredRepositories?.totalCount
                lists += viewer.lists.nodes.filterNotNull()
                after = viewer.lists.pageInfo.endCursor.takeIf { viewer.lists.pageInfo.hasNextPage }
            } while (after != null)

            val now = clock()
            val entities = mutableListOf<StarListEntity>()
            val items = LinkedHashSet<StarListItemEntity>()
            lists.forEachIndexed { position, list ->
                val id = list.id ?: return@forEachIndexed
                entities += StarListEntity(
                    id = id,
                    name = list.name.orEmpty(),
                    description = list.description,
                    isPrivate = list.isPrivate,
                    itemCount = list.items?.totalCount ?: 0,
                    position = position,
                    fetchedAt = now
                )
                allItems(id, list).forEach { repoId -> items += StarListItemEntity(id, repoId) }
            }

            database.withTransaction {
                val recorded = database.accountDao().get()?.userId
                if (recorded == null || viewerId != recorded) return@withTransaction
                database.starListDao().replaceAll(entities, items.toList())
                starred?.let { database.gitHubProfileDao().updateStarredCount(recorded, it) }
            }
        }
    }

    /** Every repository id in [list], fetching the pages after the first one. */
    private suspend fun allItems(listId: String, list: ListNode): List<Long> {
        val first = list.items ?: return emptyList()
        val ids = first.nodes.repoIds().toMutableList()
        var page = first.pageInfo
        while (page.hasNextPage) {
            val cursor = page.endCursor ?: break
            val response = graphQl.listItems(GraphQlRequest.listItems(listId, cursor))
            val next = response.data?.node?.items
            if (!response.errors.isNullOrEmpty() || next == null) {
                throw GraphQlFailure("GitHub did not return the rest of a star list")
            }
            ids += next.nodes.repoIds()
            page = next.pageInfo
        }
        return ids
    }

    private fun List<ItemNode?>.repoIds(): List<Long> = mapNotNull { it?.databaseId }

    /** Runs [block]; any failure becomes [RefreshStatus.Failed] and the cache stays as it was. */
    private suspend fun attempt(block: suspend () -> Unit): RefreshStatus = try {
        block()
        RefreshStatus.Succeeded
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RefreshStatus.Failed(offline = e is IOException, at = clock())
    }

    private class GraphQlFailure(message: String) : Exception(message)
}

/** The cached profile of [this] account. A blank blog is no blog. */
internal fun GitHubUser.toProfile(starredCount: Int?, fetchedAt: Long) = GitHubProfileEntity(
    userId = id,
    login = login,
    name = name?.takeIf { it.isNotBlank() },
    avatarUrl = avatarUrl,
    bio = bio?.takeIf { it.isNotBlank() },
    followers = followers,
    following = following,
    publicRepos = publicRepos,
    starredCount = starredCount,
    location = location?.takeIf { it.isNotBlank() },
    company = company?.takeIf { it.isNotBlank() },
    blog = blog?.takeIf { it.isNotBlank() },
    htmlUrl = htmlUrl?.takeIf { it.isNotBlank() } ?: "https://github.com/$login",
    fetchedAt = fetchedAt
)
