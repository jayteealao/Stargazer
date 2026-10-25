package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.CancellationException
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import javax.inject.Inject

/**
 * Finishes a sign-in once GitHub approved the code: reads the account with the new key, records
 * the account, then stores the key. Storing the key opens Home. If any step fails, nothing is
 * stored and the key is dropped.
 */
class SignInCompleter(
    private val api: GitHubApiService,
    private val accountDao: AccountDao,
    private val tokenManager: TokenManager,
    private val clock: () -> Long
) {
    @Inject
    constructor(api: GitHubApiService, accountDao: AccountDao, tokenManager: TokenManager) :
        this(api, accountDao, tokenManager, System::currentTimeMillis)

    /** The failure is the exception from `GET /user` (IOException offline, HttpException otherwise). */
    suspend fun complete(key: String): Result<Unit> = try {
        val user = api.getAuthenticatedUser("Bearer $key")
        // The comparison with an account recorded earlier, and the wipe on an account change,
        // go here: between reading the new account and recording it.
        accountDao.replace(
            AccountRecord(userId = user.id, login = user.login, avatarUrl = user.avatarUrl, recordedAt = clock())
        )
        tokenManager.saveToken(key)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
