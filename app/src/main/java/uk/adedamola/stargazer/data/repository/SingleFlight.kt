package uk.adedamola.stargazer.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs one call at a time. A call made while one runs waits for that one and gets its result,
 * instead of starting a second. When the running call fails or is cancelled, the waiting callers
 * get [fallback].
 */
internal class SingleFlight<T>(private val fallback: () -> T) {
    private val gate = Mutex()
    private var running: CompletableDeferred<T>? = null

    suspend fun run(block: suspend () -> T): T {
        val (job, owner) = gate.withLock {
            running?.let { it to false } ?: CompletableDeferred<T>().also { running = it }.let { it to true }
        }
        if (!owner) return job.await()
        var result = fallback()
        try {
            result = block()
            return result
        } finally {
            // Always release, also when the owner is cancelled, so later calls never wait forever.
            withContext(NonCancellable) { gate.withLock { running = null } }
            job.complete(result)
        }
    }
}
