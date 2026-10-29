package uk.adedamola.stargazer.data.auth

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException

/**
 * Which decrypt failures delete the stored key: only those after which it can never be read
 * again. Every other failure keeps it for the next read.
 */
@RunWith(AndroidJUnit4::class)
class DecryptFailureRuleTest {

    @Test
    fun permanentCausesDeleteTheKey() {
        listOf(
            MissingEncryptionKeyException(),
            KeyPermanentlyInvalidatedException(),
            UnrecoverableKeyException("x"),
            UnrecoverableKeyException("x").apply { initCause(KeyPermanentlyInvalidatedException()) },
            AEADBadTagException(),
            IllegalArgumentException(),
        ).forEach { e ->
            assertTrue("permanent: $e (cause ${e.cause})", isPermanentDecryptFailure(e))
        }
    }

    @Test
    fun transientCausesKeepTheKey() {
        listOf(
            InvalidKeyException("Keystore operation failed"),
            UserNotAuthenticatedException(),
            UnrecoverableKeyException("x").apply { initCause(KeyStoreException("busy")) },
            KeyStoreException(),
            ProviderException(),
            IllegalBlockSizeException(),
            IllegalStateException(),
            RuntimeException(),
        ).forEach { e ->
            assertFalse("transient: $e (cause ${e.cause})", isPermanentDecryptFailure(e))
        }
    }
}
