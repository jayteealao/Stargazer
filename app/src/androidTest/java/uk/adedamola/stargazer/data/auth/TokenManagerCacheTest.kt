package uk.adedamola.stargazer.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-memory key must always match the last write. A store update that arrives late must
 * never bring back a key that was just deleted, nor hide a key that was just saved.
 */
@RunWith(AndroidJUnit4::class)
class TokenManagerCacheTest {

    @Test
    fun aLateStoreUpdateNeverOverridesTheLastWrite() = runBlocking<Unit> {
        repeat(ROUNDS) { round ->
            val keys = TokenManagerFixture()
            try {
                val key = fakeKey()
                keys.tokenManager.saveToken(key)
                assertEquals("round $round: key readable right after save", key, keys.tokenManager.currentKey())

                val results = (1..8).map { async(Dispatchers.IO) { keys.tokenManager.clearTokenIf(key) } }.awaitAll()

                assertEquals("round $round: deleted exactly once", 1, results.count { it })
                assertNull("round $round: no key right after delete", keys.tokenManager.currentKey())
            } finally {
                keys.close()
            }
        }
    }

    private companion object {
        const val ROUNDS = 150
    }
}
