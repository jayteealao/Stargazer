package uk.adedamola.stargazer.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.ui.AuthState
import uk.adedamola.stargazer.ui.NavigationViewModel

/** Sign-out deletes the key only. */
@RunWith(AndroidJUnit4::class)
class SignOutTest {

    @Test
    fun signOutDeletesKeyOnly() = runBlocking<Unit> {
        val keys = TokenManagerFixture()
        val db = inMemoryDatabase()
        try {
            seedLocalData(db)
            keys.tokenManager.saveToken(fakeKey())
            val session = AccessSession(keys.tokenManager)
            val navigation = NavigationViewModel(keys.tokenManager)
            withTimeout(10_000) { navigation.authState.first { it == AuthState.Authenticated } }
            val before = localCounts(db)
            val account = db.accountDao().get()

            session.signOut()

            assertNull(keys.tokenManager.currentKey())
            assertEquals(before, localCounts(db))
            assertEquals(account, db.accountDao().get())
            assertEquals(1, before.getValue("account"))
            assertEquals(3, before.getValue("repositories"))
            assertFalse(session.accessEnded.value)
            withTimeout(10_000) { navigation.authState.first { it == AuthState.Unauthenticated } }
        } finally {
            db.close()
            keys.close()
        }
    }
}
