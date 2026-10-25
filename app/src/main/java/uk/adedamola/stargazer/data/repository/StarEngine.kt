package uk.adedamola.stargazer.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import retrofit2.Response
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.PendingUnstarEntity
import uk.adedamola.stargazer.data.mappers.toEntity
import uk.adedamola.stargazer.data.remote.Connectivity
import uk.adedamola.stargazer.data.remote.GitHubErrorText
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** One repository as GitHub's star endpoints name it. */
data class RepoRef(val id: Int, val owner: String, val name: String) {
    val fullName: String get() = "$owner/$name"
}

/** The star endpoints' name of this repository. */
fun GitHubRepository.toRef() = RepoRef(id, owner.login, name)

/** What a tap on a star control did. */
sealed interface Attempt {
    /** The request went out (its answer arrives later, as state or a [StarMessage]). */
    data object Sent : Attempt
    /** No connection: nothing changed and nothing was sent. */
    data object Offline : Attempt
    /** GitHub asked for a pause until [until] (epoch millis): nothing was sent. */
    data class RateLimited(val until: Long) : Attempt
    /** A request for this repository is still running: nothing more was sent. */
    data object Busy : Attempt
}

/** A result the person is told about. */
sealed interface StarMessage {
    val repoId: Int?

    /** GitHub did not confirm an unstar; the repository is back with all its data. */
    data class UnstarFailed(val ref: RepoRef) : StarMessage { override val repoId get() = ref.id }
    /** The Undo re-star failed; the repository stays unstarred and its local data is gone. */
    data class RestarFailed(val ref: RepoRef) : StarMessage { override val repoId get() = ref.id }
    /** GitHub did not confirm a star; nothing changed here. */
    data class StarFailed(val ref: RepoRef) : StarMessage { override val repoId get() = ref.id }
    /** No connection; nothing changed here. */
    data class ConnectionNeeded(override val repoId: Int?) : StarMessage
    /** GitHub accepts more changes at [at] (epoch millis). */
    data class RateLimited(override val repoId: Int?, val at: Long) : StarMessage
}

/** The Undo offer of the latest unstar, shown until its window ends. */
data class UndoOffer(val repoId: Int, val fullName: String)

/** Everything a star control shows for one repository. */
data class StarControlState(
    val starred: Boolean,
    val inFlight: Boolean,
    val enabled: Boolean,
    val status: StarMessage?,
    val rateLimitedUntil: Long?,
)

/**
 * Stars and unstars repositories on GitHub for the signed-in account.
 *
 * An unstar is sent at once. The repository leaves Home at once, but its row, favorite, pin and
 * tags stay until GitHub confirms and the Undo window ends; a refused or failed request restores
 * it with all its data. Undo re-stars. The pending unstar is kept in the database, so a process
 * killed during the window is settled against GitHub on the next [resumePending].
 *
 * One request at a time per repository; offline and rate-limited taps send nothing. A 401 goes
 * through the app's access-ended path on the HTTP client. Nothing here logs a key or a body.
 *
 * Built for the app by `StarModule`, with the real clock and the app-wide star scope.
 */
class StarEngine(
    private val api: GitHubApiService,
    private val database: AppDatabase,
    private val connectivity: Connectivity,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val sleep: suspend (Long) -> Unit,
    private val windowMillis: Long = WINDOW_MILLIS,
) {

    private val pendingDao = database.pendingUnstarDao()
    private val repositoryDao = database.repositoryDao()
    private val locks = ConcurrentHashMap<Int, Mutex>()
    private val windowJobs = ConcurrentHashMap<Int, Job>()

    /** When each repository was last starred, unstarred or restored here (clock millis). */
    private val touched = ConcurrentHashMap<Int, Long>()

    private val _inFlight = MutableStateFlow<Set<Int>>(emptySet())
    val inFlight: StateFlow<Set<Int>> = _inFlight.asStateFlow()

    private val _rateLimitedUntil = MutableStateFlow<Long?>(null)
    val rateLimitedUntil: StateFlow<Long?> = _rateLimitedUntil.asStateFlow()

    private val _undoOffer = MutableStateFlow<UndoOffer?>(null)
    val undoOffer: StateFlow<UndoOffer?> = _undoOffer.asStateFlow()

    private val _messages = MutableSharedFlow<StarMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<StarMessage> = _messages.asSharedFlow()

    private val _lastFailure = MutableStateFlow<Map<Int, StarMessage>>(emptyMap())
    val lastFailure: StateFlow<Map<Int, StarMessage>> = _lastFailure.asStateFlow()

    /** Repositories hidden from Home while their unstar is pending. */
    val hiddenIds: Flow<Set<Int>> = pendingDao.observeIds().map { it.toSet() }.distinctUntilChanged()

    fun controlState(repoId: Int): Flow<StarControlState> = combine(
        combine(repositoryDao.observeById(repoId), pendingDao.observe(repoId)) { row, pending ->
            row != null && pending == null
        },
        _inFlight.map { repoId in it },
        _rateLimitedUntil,
        connectivity.online,
        _lastFailure.map { it[repoId] },
    ) { starred, inFlight, until, online, failure ->
        val limited = until != null && clock() < until
        StarControlState(
            starred = starred,
            inFlight = inFlight,
            enabled = online && !limited && !inFlight,
            status = when {
                !online -> StarMessage.ConnectionNeeded(repoId)
                limited -> StarMessage.RateLimited(repoId, until!!)
                else -> failure
            },
            rateLimitedUntil = until.takeIf { limited },
        )
    }.distinctUntilChanged()

    /** Unstars [ref]: the DELETE goes out now and the repository leaves Home now. */
    suspend fun unstar(ref: RepoRef): Attempt {
        refusal(ref.id)?.let { return it }
        touch(ref.id)
        val lock = lockFor(ref.id)
        if (!lock.tryLock()) return Attempt.Busy
        try {
            clearFailure(ref.id)
            val now = clock()
            val windowEndsAt = now + windowMillis
            pendingDao.insert(
                PendingUnstarEntity(ref.id, ref.owner, ref.name, PendingUnstarEntity.SENDING, windowEndsAt, now)
            )
            _undoOffer.value = UndoOffer(ref.id, ref.fullName)
            scope.launch {
                try {
                    val answer = send(ref.id) { api.unstarRepository(ref.owner, ref.name) }
                    if (answer.isSuccess && answer.getOrThrow().isSuccessful) {
                        pendingDao.setState(ref.id, PendingUnstarEntity.CONFIRMED)
                        windowJobs[ref.id] = scope.launch {
                            sleep((windowEndsAt - clock()).coerceAtLeast(0))
                            lock.withLock { finishLocked(ref.id) }
                        }
                    } else {
                        // Refused or failed: the repository comes back with everything it had.
                        pendingDao.delete(ref.id)
                        clearOffer(ref.id)
                        report(failureOf(answer, ref) { StarMessage.UnstarFailed(it) })
                    }
                } finally {
                    lock.unlock()
                }
            }
        } catch (e: Throwable) {
            lock.unlock()
            throw e
        }
        return Attempt.Sent
    }

    /**
     * Undoes the unstar of [repoId]: a PUT, after any DELETE still running for it. When
     * the DELETE already failed, the repository is back and nothing is sent. Undo that cannot
     * be sent now (offline, rate limited) keeps the window open until it can, so the
     * repository's local data is not removed while the person is waiting to undo.
     */
    suspend fun undo(repoId: Int): Attempt {
        refusal(repoId)?.let { refused ->
            holdWindow(repoId)
            return refused
        }
        touch(repoId)
        val lock = lockFor(repoId)
        scope.launch {
            lock.withLock {
                windowJobs.remove(repoId)?.cancel()
                val pending = pendingDao.get(repoId) ?: return@withLock
                val ref = RepoRef(repoId, pending.owner, pending.name)
                clearOffer(repoId)
                clearFailure(repoId)
                val answer = send(repoId) { api.starRepository(ref.owner, ref.name) }
                if (answer.isSuccess && answer.getOrThrow().isSuccessful) {
                    pendingDao.delete(repoId)
                } else {
                    val message = failureOf(answer, ref) { StarMessage.RestarFailed(it) }
                    report(message)
                    if (message is StarMessage.RestarFailed) {
                        // GitHub refused: it has the repository unstarred, so keep the app in line.
                        finishLocked(repoId)
                    } else {
                        // No connection or a rate limit: Undo stays on offer until it can be sent.
                        _undoOffer.value = UndoOffer(repoId, ref.fullName)
                        holdWindowLocked(repoId)
                    }
                }
            }
        }
        return Attempt.Sent
    }

    /**
     * Stars [ref]. [repo] is GitHub's description of it, stored so that the repository
     * enters the starred list. A repository whose unstar is pending is restored as by Undo.
     */
    suspend fun star(ref: RepoRef, repo: GitHubRepository?): Attempt {
        refusal(ref.id)?.let { return it }
        touch(ref.id)
        if (pendingDao.get(ref.id) != null) return undo(ref.id)
        val lock = lockFor(ref.id)
        if (!lock.tryLock()) return Attempt.Busy
        clearFailure(ref.id)
        scope.launch {
            try {
                val answer = send(ref.id) { api.starRepository(ref.owner, ref.name) }
                if (answer.isSuccess && answer.getOrThrow().isSuccessful) {
                    // Keeps any favorite or pin a row of this repository already has. The phone's
                    // time only orders the row until the next sync stores GitHub's time; the sync
                    // cutoff never reads it.
                    (repo ?: fetchRepository(ref))?.let {
                        repositoryDao.upsertRepositories(listOf(it.toEntity(starredAt = clock())))
                    }
                } else {
                    report(failureOf(answer, ref) { StarMessage.StarFailed(it) })
                }
            } finally {
                lock.unlock()
            }
        }
        return Attempt.Sent
    }

    /**
     * Settles every pending unstar that no running request or window in this process owns, by
     * asking GitHub whether the repository is still starred: starred means the DELETE
     * never landed, so the repository comes back; not starred finishes the unstar. No answer
     * keeps the record for the next call.
     */
    suspend fun resumePending() {
        if (!connectivity.isOnline()) return
        for (pending in pendingDao.getAll()) {
            val id = pending.repositoryId
            if (windowJobs[id]?.isActive == true) continue
            val lock = lockFor(id)
            if (!lock.tryLock()) continue
            try {
                val answer = runCatching { api.isStarred(pending.owner, pending.name) }.getOrNull() ?: continue
                when (answer.code()) {
                    204 -> pendingDao.delete(id)
                    404 -> finishLocked(id)
                }
            } finally {
                lock.unlock()
            }
        }
    }

    /**
     * Keeps the Undo window of [repoId] open while Undo cannot be sent: the window starts again,
     * in full, once the phone is online and GitHub's rate limit has passed. Only a confirmed
     * unstar has a window; an unstar whose DELETE is still running is left alone.
     */
    private fun holdWindow(repoId: Int) {
        val running = windowJobs[repoId] ?: return
        running.cancel()
        holdWindowLocked(repoId)
    }

    private fun holdWindowLocked(repoId: Int) {
        windowJobs[repoId] = scope.launch {
            connectivity.online.first { it }
            _rateLimitedUntil.first { until -> until == null || clock() >= until }
            sleep(windowMillis)
            lockFor(repoId).withLock { finishLocked(repoId) }
        }
    }

    /** Deletes the repository, its tag assignments and its pending record in one transaction. */
    private suspend fun finishLocked(repoId: Int) {
        windowJobs.remove(repoId)
        database.withTransaction {
            database.removeRepository(repoId)
            pendingDao.delete(repoId)
        }
        clearOffer(repoId)
    }

    /** GitHub's description of [ref], or null when it cannot be read now (the next sync adds it). */
    private suspend fun fetchRepository(ref: RepoRef): GitHubRepository? = try {
        api.getRepository(ref.owner, ref.name)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun send(repoId: Int, call: suspend () -> Response<Unit>): kotlin.Result<Response<Unit>> {
        _inFlight.update { it + repoId }
        return try {
            kotlin.Result.success(call())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // the process is going away: leave the record for resumePending()
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        } finally {
            _inFlight.update { it - repoId }
        }
    }

    /** The message for a request that did not succeed; records a rate limit when GitHub set one. */
    private fun failureOf(
        answer: kotlin.Result<Response<Unit>>,
        ref: RepoRef,
        otherwise: (RepoRef) -> StarMessage,
    ): StarMessage {
        answer.exceptionOrNull()?.let { e ->
            return if (e is IOException) StarMessage.ConnectionNeeded(ref.id) else otherwise(ref)
        }
        val response = answer.getOrThrow()
        if (response.code() == 403 || response.code() == 429) {
            GitHubErrorText.retryAtMillis(HttpException(response), clock())?.let { at ->
                limitUntil(at)
                return StarMessage.RateLimited(ref.id, at)
            }
        }
        return otherwise(ref)
    }

    private fun limitUntil(at: Long) {
        _rateLimitedUntil.value = at
        scope.launch {
            delay((at - clock()).coerceAtLeast(0))
            _rateLimitedUntil.compareAndSet(at, null)
        }
    }

    /** Why a tap must send nothing, or null when it may go out. */
    private fun refusal(repoId: Int): Attempt? {
        val refused = when {
            !connectivity.isOnline() -> Attempt.Offline
            else -> _rateLimitedUntil.value?.takeIf { clock() < it }?.let { Attempt.RateLimited(it) }
        } ?: return null
        val message = when (refused) {
            is Attempt.RateLimited -> StarMessage.RateLimited(repoId, refused.until)
            else -> StarMessage.ConnectionNeeded(repoId)
        }
        _lastFailure.update { it + (repoId to message) }
        return refused
    }

    private fun report(message: StarMessage) {
        message.repoId?.let { id -> _lastFailure.update { it + (id to message) } }
        _messages.tryEmit(message)
    }

    private fun clearFailure(repoId: Int) = _lastFailure.update { it - repoId }

    private fun clearOffer(repoId: Int) = _undoOffer.update { if (it?.repoId == repoId) null else it }

    /**
     * Repositories starred, unstarred or restored here at or after [since] (clock millis). A bulk
     * removal that read GitHub's list before such a change leaves these repositories alone.
     */
    fun touchedSince(since: Long): Set<Int> = touched.filterValues { it >= since }.keys.toSet()

    private fun touch(repoId: Int) {
        touched[repoId] = clock()
    }

    private fun lockFor(repoId: Int): Mutex = locks.getOrPut(repoId) { Mutex() }

    companion object {
        /** How long Undo is offered after an unstar. */
        const val WINDOW_MILLIS = 5_000L
    }
}
