package uk.adedamola.stargazer.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import javax.inject.Inject
import javax.inject.Singleton

/** A full read of GitHub's starred list, ready to remove what it no longer lists. */
sealed interface Prepared {
    /**
     * Every page was read for [account], starting at [startedAt] (clock millis). [candidates] are
     * the repositories stored here before the read that GitHub did not list. [stored] and
     * [listed] count the repositories stored here and listed by GitHub at the read.
     */
    data class Ready(
        val account: AccountRecord,
        val startedAt: Long,
        val candidates: Set<Int>,
        val stored: Int,
        val listed: Int,
    ) : Prepared

    /** No signed-in account, or a page failed: nothing may be removed. */
    data class Failed(val error: Throwable?) : Prepared
}

/** What one reconcile did. */
sealed interface Outcome {
    /** [count] repositories would be removed, and the person is told first; nothing is removed yet. */
    data class NoticeNeeded(val count: Int, val prepared: Prepared.Ready) : Outcome

    /** [removed] repositories left this phone with their favorite, pin and tags. */
    data class Done(val removed: Int) : Outcome

    /** Another account signed in since the read started; nothing was removed. */
    data object AccountChanged : Outcome

    /** The read did not finish; nothing was removed. */
    data class Failed(val error: Throwable?) : Outcome
}

/**
 * Brings the repositories stored on this phone in line with GitHub's starred list: on a
 * pull-to-refresh it reads every page of the list and removes the repositories GitHub no longer
 * lists, with their favorite, pin and tag assignments. Background sync stays add-only.
 *
 * Nothing is removed until the whole list has been read. Only repositories stored before the read
 * started can be removed. A repository with a pending unstar, a request in flight, or a star,
 * unstar or undo made here since the read started is left alone. Nothing is removed when the
 * account changed since the read started.
 *
 * The first reconcile after a sign-in that would remove anything waits for the person: the caller
 * shows the count and calls [apply] on Continue. So does any reconcile whose answer looks wrong: an
 * empty list while repositories are stored here, or one that would remove most of them.
 * A repository the read did not list is asked about once more before it can go (the list can
 * shift while its pages are read), when there are few such repositories. A finished reconcile stores the sign-in it ran for
 * (the account's `recordedAt`), which a new sign-in always changes. Nothing here logs a key or a
 * body.
 */
@Singleton
class StarReconciler(
    private val api: GitHubApiService,
    private val database: AppDatabase,
    private val engine: StarEngine,
    private val clock: () -> Long,
) {
    @Inject
    constructor(api: GitHubApiService, database: AppDatabase, engine: StarEngine) :
        this(api, database, engine, System::currentTimeMillis)

    private val singleFlight = SingleFlight<Outcome> { Outcome.Failed(null) }

    /** Reads GitHub's whole starred list; any failure on any page writes nothing. */
    suspend fun prepare(): Prepared {
        val account = database.accountDao().get() ?: return Prepared.Failed(null)
        val startedAt = clock()
        val stored = database.repositoryDao().getAllIds().toSet()
        val listed = HashSet<Int>()
        try {
            var page = 1
            while (true) {
                val repos = api.getStarredRepositoriesWithTimestamp(
                    page = page,
                    perPage = PAGE_SIZE,
                    sort = "created",
                    direction = "desc"
                )
                repos.mapTo(listed) { it.repo.id }
                if (repos.size < PAGE_SIZE) break
                page++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Prepared.Failed(e)
        }
        val candidates = try {
            confirmUnstarred(stored - listed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Prepared.Failed(e)
        }
        return Prepared.Ready(account, startedAt, candidates, stored = stored.size, listed = listed.size)
    }

    /**
     * The [candidates] GitHub confirms it no longer stars, one request each, when there are at most
     * [CONFIRM_LIMIT]: a star removed elsewhere while the pages were read shifts the later pages,
     * so the read can miss a repository that is still starred. Any answer but "not starred" keeps
     * the repository. A larger set goes unconfirmed, and needs the person's Continue when it is
     * most of the list.
     */
    private suspend fun confirmUnstarred(candidates: Set<Int>): Set<Int> {
        if (candidates.isEmpty() || candidates.size > CONFIRM_LIMIT) return candidates
        val dao = database.repositoryDao()
        return candidates.filterTo(HashSet()) { id ->
            val repo = dao.getRepositoryById(id) ?: return@filterTo false
            api.isStarred(repo.ownerLogin, repo.name).code() == 404
        }
    }

    /**
     * True when [prepared] would remove repositories and the person must be told first: no
     * reconcile finished for this sign-in, or the answer looks wrong (see [implausible]).
     */
    suspend fun noticeDue(prepared: Prepared.Ready): Boolean = noticeDue(prepared, removable(prepared))

    private suspend fun noticeDue(prepared: Prepared.Ready, removable: Set<Int>): Boolean =
        removable.isNotEmpty() && (
            implausible(prepared, removable.size) ||
                database.syncMetadataDao().getMetadata(MARKER)?.lastSyncTimestamp != prepared.account.recordedAt
            )

    /**
     * An empty list while repositories are stored here, or a removal of most of them, is more
     * likely a wrong answer than the person's own unstars, so it is never applied without asking.
     */
    private fun implausible(prepared: Prepared.Ready, removing: Int): Boolean =
        (prepared.listed == 0 && prepared.stored > 0) ||
            (removing > LARGE_REMOVAL && removing * 2 > prepared.stored)

    /** Removes what [prepared] found, in one transaction, and records the finished reconcile. */
    suspend fun apply(prepared: Prepared.Ready): Outcome = database.withTransaction {
        val current = database.accountDao().get()
        if (current?.userId != prepared.account.userId || current.recordedAt != prepared.account.recordedAt) {
            return@withTransaction Outcome.AccountChanged
        }
        val removed = removable(prepared)
        removed.forEach { id -> database.removeRepository(id) }
        database.syncMetadataDao().updateMetadata(
            SyncMetadata(dataType = MARKER, lastSyncTimestamp = prepared.account.recordedAt, isInitialSyncComplete = true)
        )
        Outcome.Done(removed.size)
    }

    /**
     * Reads the list, then removes what GitHub no longer lists, or returns [Outcome.NoticeNeeded]
     * when the person must be told first. A call while one runs waits for that one.
     */
    suspend fun reconcile(): Outcome = singleFlight.run {
        when (val prepared = prepare()) {
            is Prepared.Failed -> Outcome.Failed(prepared.error)
            is Prepared.Ready -> {
                val removable = removable(prepared)
                if (noticeDue(prepared, removable)) Outcome.NoticeNeeded(removable.size, prepared)
                else apply(prepared)
            }
        }
    }

    /** The candidates still stored and not held by a pending unstar, a request or a recent change. */
    private suspend fun removable(prepared: Prepared.Ready): Set<Int> {
        val held = database.pendingUnstarDao().getAll().map { it.repositoryId }.toSet() +
            engine.inFlight.value +
            engine.touchedSince(prepared.startedAt)
        val stored = database.repositoryDao().getAllIds().toSet()
        return prepared.candidates.filterTo(HashSet()) { it in stored && it !in held }
    }

    companion object {
        /** The `sync_metadata` row of the last finished reconcile; the account wipe clears it. */
        const val MARKER = "starred_repos_reconcile"
        private const val PAGE_SIZE = 100
        /** At most this many unlisted repositories are confirmed one by one. */
        private const val CONFIRM_LIMIT = 50
        /** A removal above this count, and of more than half the list, asks first. */
        private const val LARGE_REMOVAL = 50
    }
}
