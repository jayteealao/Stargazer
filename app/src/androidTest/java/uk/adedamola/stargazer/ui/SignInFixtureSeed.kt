package uk.adedamola.stargazer.ui

import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.TokenManager
import javax.inject.Inject

/**
 * Stores a plain fixture value as the app's key, for screen checks on an emulator: GitHub
 * rejects it with a real 401, which shows the access-ended path with no account. It runs only
 * with `-e fixtureKey <value>`; a normal test run skips it. Sign out afterwards to remove it.
 */
@HiltAndroidTest
class SignInFixtureSeed {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var tokenManager: TokenManager

    private val fixtureKey: String? = InstrumentationRegistry.getArguments().getString("fixtureKey")

    @Before
    fun setUp() {
        assumeTrue(fixtureKey != null)
        hiltRule.inject()
    }

    @Test
    fun seed() = runBlocking {
        tokenManager.saveToken(fixtureKey!!)
    }
}
