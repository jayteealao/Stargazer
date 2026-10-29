package uk.adedamola.stargazer.data.auth

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Synchronous read of the current access key, for the HTTP layer. */
interface AccessKeyProvider {
    fun currentKey(): String?
}

/**
 * Stores the GitHub access key encrypted (see [AccessKeyCipher]) in its own DataStore file.
 *
 * The stored key is deleted only when it can never be read again (see
 * [isPermanentDecryptFailure]: missing or invalidated Keystore key, restored or tampered
 * ciphertext, unknown format). That read reports "no key": the person is signed out, and no
 * error surfaces. Any other failure, such as a short-lived Keystore error, also reads as
 * "no key" for that one read, but keeps the ciphertext and the Keystore key, and is never
 * cached: the next read tries again.
 */
class TokenManager(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AccessKeyCipher,
    scope: CoroutineScope,
) : AccessKeyProvider {

    private val ciphertextKey = byteArrayPreferencesKey("ciphertext")

    private val cacheLock = Any()
    private val conditionalClearLock = Mutex()
    // Serializes our own writes with the store watcher, so a late store update never
    // overwrites the key that a newer save or delete just set.
    private val writeLock = Mutex()
    @Volatile private var cachedKey: String? = null
    @Volatile private var cacheReady = false

    /** The outcome of one read of the stored key. */
    private sealed interface KeyRead {
        data class Present(val key: String) : KeyRead
        /** No key is stored, or the stored key could never be read and was deleted. */
        object Absent : KeyRead
        /** The stored key could not be read this time; it is kept for the next read. */
        object Unavailable : KeyRead
    }

    private val reads: Flow<KeyRead> = dataStore.data
        .map { preferences -> preferences[ciphertextKey]?.let { readKey(it) } ?: KeyRead.Absent }
        .flowOn(Dispatchers.IO)

    /** Emits the current access key, or null when signed out or the key is unreadable right now. */
    val token: Flow<String?> = reads
        .map { (it as? KeyRead.Present)?.key }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    init {
        // Re-read the store under the write lock instead of trusting the emitted value,
        // which may be older than a save or delete that completed meanwhile.
        scope.launch { token.collect { writeLock.withLock { cacheRead(reads.first()) } } }
    }

    suspend fun saveToken(token: String) = writeLock.withLock {
        // Encrypt inside the edit so it is serialized with the failure path's key deletion.
        dataStore.edit { preferences -> preferences[ciphertextKey] = cipher.encrypt(token) }
        setCache(token)
    }

    /** Deletes the stored key. The Keystore key stays for the next sign-in. */
    suspend fun clearToken() = writeLock.withLock {
        dataStore.edit { preferences -> preferences.remove(ciphertextKey) }
        setCache(null)
    }

    /**
     * Deletes the stored key only while it still equals [expected], and reports whether this
     * call deleted it. Concurrent callers with the same key see `true` once; a key saved after
     * [expected] was read is never removed.
     */
    suspend fun clearTokenIf(expected: String): Boolean = conditionalClearLock.withLock {
        if (currentKey() != expected) return@withLock false
        clearToken()
        true
    }

    suspend fun hasToken(): Boolean = token.first() != null

    /**
     * Returns the cached key. Before the first value is cached it blocks once on the store;
     * callers are OkHttp dispatcher threads, never the main thread.
     */
    override fun currentKey(): String? {
        if (cacheReady) return cachedKey
        val read = runBlocking { reads.first() }
        synchronized(cacheLock) {
            if (cacheReady) return cachedKey
            return when (read) {
                is KeyRead.Present -> read.key.also { cachedKey = it; cacheReady = true }
                KeyRead.Absent -> null.also { cachedKey = null; cacheReady = true }
                // Not cached: the next call reads the store again.
                KeyRead.Unavailable -> null
            }
        }
    }

    private fun cacheRead(read: KeyRead) {
        when (read) {
            is KeyRead.Present -> setCache(read.key)
            KeyRead.Absent -> setCache(null)
            KeyRead.Unavailable -> synchronized(cacheLock) {
                cachedKey = null
                cacheReady = false
            }
        }
    }

    private fun setCache(key: String?) {
        synchronized(cacheLock) {
            cachedKey = key
            cacheReady = true
        }
    }

    private suspend fun readKey(blob: ByteArray): KeyRead =
        try {
            KeyRead.Present(cipher.decrypt(blob))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isPermanentDecryptFailure(e)) {
                Log.w(TAG, "Stored access key unreadable (${e.javaClass.simpleName}); treating as signed out")
                dataStore.edit { preferences ->
                    // Only forget the blob that failed; a newer key saved meanwhile stays.
                    if (preferences[ciphertextKey]?.contentEquals(blob) == true) {
                        preferences.remove(ciphertextKey)
                        cipher.deleteKey()
                    }
                }
                KeyRead.Absent
            } else {
                Log.w(TAG, "Stored access key unreadable (${e.javaClass.simpleName}); kept for the next read")
                KeyRead.Unavailable
            }
        }

    private companion object {
        const val TAG = "TokenManager"
    }
}
