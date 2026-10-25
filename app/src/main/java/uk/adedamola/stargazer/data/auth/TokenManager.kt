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

/** Synchronous read of the current access key, for the HTTP layer. */
interface AccessKeyProvider {
    fun currentKey(): String?
}

/**
 * Stores the GitHub access key encrypted (see [AccessKeyCipher]) in its own DataStore file.
 *
 * Any failure to read the stored key (missing or invalidated Keystore key, restored or
 * tampered ciphertext, corrupt file) deletes the ciphertext and reads as "no key": the
 * person is signed out, and no error surfaces.
 */
class TokenManager(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AccessKeyCipher,
    scope: CoroutineScope,
) : AccessKeyProvider {

    private val ciphertextKey = byteArrayPreferencesKey("ciphertext")

    private val cacheLock = Any()
    @Volatile private var cachedKey: String? = null
    @Volatile private var cacheReady = false

    /** Emits the current access key, or null when signed out. */
    val token: Flow<String?> = dataStore.data
        .map { preferences -> preferences[ciphertextKey]?.let { decryptOrForget(it) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    init {
        scope.launch { token.collect { setCache(it) } }
    }

    suspend fun saveToken(token: String) {
        // Encrypt inside the edit so it is serialized with the failure path's key deletion.
        dataStore.edit { preferences -> preferences[ciphertextKey] = cipher.encrypt(token) }
        setCache(token)
    }

    /** Deletes the stored key. The Keystore key stays for the next sign-in. */
    suspend fun clearToken() {
        dataStore.edit { preferences -> preferences.remove(ciphertextKey) }
        setCache(null)
    }

    suspend fun hasToken(): Boolean = token.first() != null

    /**
     * Returns the cached key. Before the first value is cached it blocks once on the store;
     * callers are OkHttp dispatcher threads, never the main thread.
     */
    override fun currentKey(): String? {
        if (cacheReady) return cachedKey
        val key = runBlocking { token.first() }
        synchronized(cacheLock) {
            if (!cacheReady) {
                cachedKey = key
                cacheReady = true
            }
            return cachedKey
        }
    }

    private fun setCache(key: String?) {
        synchronized(cacheLock) {
            cachedKey = key
            cacheReady = true
        }
    }

    private suspend fun decryptOrForget(blob: ByteArray): String? =
        try {
            cipher.decrypt(blob)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Stored access key unreadable (${e.javaClass.simpleName}); treating as signed out")
            dataStore.edit { preferences ->
                // Only forget the blob that failed; a newer key saved meanwhile stays.
                if (preferences[ciphertextKey]?.contentEquals(blob) == true) {
                    preferences.remove(ciphertextKey)
                    cipher.deleteKey()
                }
            }
            null
        }

    private companion object {
        const val TAG = "TokenManager"
    }
}
