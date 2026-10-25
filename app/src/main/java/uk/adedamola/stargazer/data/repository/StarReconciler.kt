package uk.adedamola.stargazer.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * the repositories stored here before the read that GitHub did not list.
     */
    data class Ready(val account: AccountRecord, val startedAt: Long, val candidates: Set<Int>) : Prepared

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
 * shows the count and calls [apply] on Continue. A finished reconcile stores the sign-in it ran for
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

    private val gate = Mutex()
    private var running: CompletableDeferred<Outcome>? = null

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
        return Prepared.Ready(account, startedAt, stored - listed)
    }

    /** True when [prepared] would remove repositories and no reconcile finished for this sign-in. */
    suspend fun noticeDue(prepared: Prepared.Ready): Boolean =
        removable(prepared).isNotEmpty() &&
            database.syncMetadataDao().getMetadata(MARKER)?.lastSyncTimestamp != prepared.account.recordedAt

    /** Removes what [prepared] found, in one transaction, and records the finished reconcile. */
    suspend fun apply(prepared: Prepared.Ready): Outcome = database.withTransaction {
        val current = database.accountDao().get()
        if (current?.userId != prepared.account.userId || current.recordedAt != prepared.account.recordedAt) {
            return@withTransaction Outcome.AccountChanged
        }
        val removed = removable(prepared)
        removed.forEach { id ->
            database.repositoryTagDao().deleteAllTagsForRepository(id)
            database.repositoryDao().deleteById(id)
        }
        database.syncMetadataDao().updateMetadata(
            SyncMetadata(dataType = MARKER, lastSyncTimestamp = prepared.account.recordedAt, isInitialSyncComplete = true)
        )
        Outcome.Done(removed.size)
    }

    /**
     * Reads the list, then removes what GitHub no longer lists, or returns [Outcome.NoticeNeeded]
     * when the person must be told first. A call while one runs waits for that one.
     */
    suspend fun reconcile(): Outcome {
        val (job, owner) = gate.withLock {
            running?.let { it to false } ?: CompletableDeferred<Outcome>().also { running = it }.let { it to true }
        }
        if (!owner) return job.await()
        var outcome: Outcome = Outcome.Failed(null)
        try {
            outcome = when (val prepared = prepare()) {
                is Prepared.Failed -> Outcome.Failed(prepared.error)
                is Prepared.Ready ->
                    if (noticeDue(prepared)) Outcome.NoticeNeeded(removable(prepared).size, prepared)
                    else apply(prepared)
            }
            return outcome
        } finally {
            gate.withLock { running = null }
            job.complete(outcome)
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
    }
}
