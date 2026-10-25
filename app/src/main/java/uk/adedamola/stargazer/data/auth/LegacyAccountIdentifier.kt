package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import javax.inject.Inject
import javax.inject.Singleton

/** What the one-time check of the earlier version's token found. */
enum class LegacyIdentify {
    /** No old token is left; nothing to do. */
    NoOldToken,

    /** The old token worked; the account it belongs to is recorded and the token removed. */
    Identified,

    /** The old token was rejected (401); it is removed and the data owner is unknown. */
    OwnerUnknown,

    /** GitHub could not be asked (offline, rate limit, server error); the token is kept for later. */
    Unresolved
}

/**
 * Identifies whose data an update from the pasted-token version holds: the old token is used once
 * for `GET /user`, then removed. It runs at app start and again before every sign-in completes;
 * a mutex makes concurrent calls run one after the other.
 */
@Singleton
class LegacyAccountIdentifier(
    private val api: GitHubApiService,
    private val accountDao: AccountDao,
    private val store: LegacyAccountStore,
    private val clock: () -> Long
) {
    @Inject
    constructor(api: GitHubApiService, accountDao: AccountDao, store: LegacyAccountStore) :
        this(api, accountDao, store, System::currentTimeMillis)

    private val mutex = Mutex()

    suspend fun identify(): LegacyIdentify = mutex.withLock {
        val old = store.oldToken() ?: return@withLock LegacyIdentify.NoOldToken
        try {
            val user = api.getAuthenticatedUser("Bearer $old")
            // A record written by a later sign-in is newer truth; never overwrite it.
            if (accountDao.get() == null && !store.ownerUnknown()) {
                accountDao.replace(
                    AccountRecord(userId = user.id, login = user.login, avatarUrl = user.avatarUrl, recordedAt = clock())
                )
            }
            store.clearOldToken()
            LegacyIdentify.Identified
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401) {
                store.clearOldToken()
                if (accountDao.get() == null) store.setOwnerUnknown(true)
                LegacyIdentify.OwnerUnknown
            } else {
                LegacyIdentify.Unresolved
            }
        } catch (e: Exception) {
            LegacyIdentify.Unresolved
        }
    }
}
