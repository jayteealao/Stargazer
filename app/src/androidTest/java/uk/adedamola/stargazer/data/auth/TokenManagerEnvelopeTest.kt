package uk.adedamola.stargazer.data.auth

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.di.SecureStorageModule
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** The Keystore envelope: encryption at rest and the "unreadable key means signed out" rule. */
@RunWith(AndroidJUnit4::class)
class TokenManagerEnvelopeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ciphertextKey = byteArrayPreferencesKey("ciphertext")
    private val fakeKey = "test-key-not-real-" + UUID.randomUUID()

    private lateinit var alias: String
    private lateinit var otherAlias: String
    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var job: kotlinx.coroutines.Job
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        alias = "test_alias_" + UUID.randomUUID()
        otherAlias = "test_alias_" + UUID.randomUUID()
        dir = File(context.cacheDir, "envelope-" + UUID.randomUUID()).apply { mkdirs() }
        file = File(dir, "key.preferences_pb")
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.IO)
        dataStore = SecureStorageModule.createAccessKeyDataStore(scope) { file }
    }

    @After
    fun tearDown() = runBlocking {
        job.cancelAndJoin()
        keyStore().apply {
            if (containsAlias(alias)) deleteEntry(alias)
            if (containsAlias(otherAlias)) deleteEntry(otherAlias)
        }
        dir.deleteRecursively()
        Unit
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun manager(cipherAlias: String = alias) =
        TokenManager(dataStore, AndroidKeystoreCipher(cipherAlias), scope)

    private suspend fun storedCiphertext(): ByteArray? = dataStore.data.first()[ciphertextKey]

    @Test
    fun roundTrip() = runBlocking {
        val manager = manager()
        manager.saveToken(fakeKey)

        assertEquals(fakeKey, manager.token.first())
        assertEquals(fakeKey, manager.currentKey())
        assertTrue(manager.hasToken())
    }

    @Test
    fun fileHoldsNoPlaintext() = runBlocking {
        manager().saveToken(fakeKey)

        val bytes = file.readBytes()
        assertTrue("key file written", bytes.isNotEmpty())
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertFalse("plain key in file", raw.contains(fakeKey))
        val base64 = Base64.encodeToString(fakeKey.toByteArray(), Base64.NO_WRAP)
        assertFalse("Base64 key in file", raw.contains(base64))
        assertFalse("UTF-16 key in file", String(bytes, Charsets.UTF_16LE).contains(fakeKey))
    }

    @Test
    fun missingAliasReadsAsNoKey() = runBlocking {
        val manager = manager()
        manager.saveToken(fakeKey)
        keyStore().deleteEntry(alias)

        assertNull(manager.token.first())
        assertNull("ciphertext deleted", storedCiphertext())
        assertFalse("reading created no key", keyStore().containsAlias(alias))
    }

    @Test
    fun tamperedCiphertextReadsAsNoKey() = runBlocking {
        val manager = manager()
        manager.saveToken(fakeKey)
        val blob = storedCiphertext()!!.copyOf()
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        dataStore.edit { it[ciphertextKey] = blob }

        assertNull(manager.token.first())
        assertNull("ciphertext deleted", storedCiphertext())
    }

    @Test
    fun foreignCiphertextReadsAsNoKey() = runBlocking {
        manager(alias).saveToken(fakeKey)

        val other = manager(otherAlias)
        assertNull(other.token.first())
        assertNull("ciphertext deleted", storedCiphertext())
        assertFalse("reading created no key", keyStore().containsAlias(otherAlias))
    }

    @Test
    fun corruptFileReadsAsNoKey() = runBlocking {
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })

        val manager = manager()
        assertNull(manager.token.first())
        assertNull(manager.currentKey())
    }

    @Test
    fun clearToken() = runBlocking {
        val manager = manager()
        manager.saveToken(fakeKey)
        assertEquals(fakeKey, manager.currentKey())

        manager.clearToken()

        assertNull(manager.currentKey())
        assertNull(manager.token.first())
        assertFalse(manager.hasToken())
    }
}
