package uk.adedamola.stargazer.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.GitHubProfileDao
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import javax.inject.Inject

/**
 * The signed-in account as the screens read it. A cached profile belongs to the recorded account
 * only when their user ids match; a profile left from another account is never shown.
 */
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val profileDao: GitHubProfileDao,
    private val repositoryDao: RepositoryDao,
) {
    /** The account for the drawer header: the cached profile, else the record (login and avatar). */
    fun observeAccount(): Flow<AccountSummary?> =
        combine(profileDao.observe(), accountDao.observe()) { profile, record ->
            when {
                record == null -> null
                profile != null && profile.userId == record.userId ->
                    AccountSummary(profile.userId, profile.login, profile.name, profile.avatarUrl)
                else -> AccountSummary(record.userId, record.login, null, record.avatarUrl)
            }
        }

    /** The recorded account's numeric user id; emits only when it changes. */
    fun observeUserId(): Flow<Long?> = accountDao.observe().map { it?.userId }.distinctUntilChanged()

    /** The cached profile of the recorded account (or the only one cached when none is recorded). */
    fun observeProfile(): Flow<GitHubProfileEntity?> =
        combine(profileDao.observe(), accountDao.observe()) { profile, record ->
            profile?.takeIf { record == null || it.userId == record.userId }
        }

    /** True when any profile is cached. */
    suspend fun hasCachedProfile(): Boolean = profileDao.get() != null

    /** How many repositories are starred on this phone. */
    fun observeLocalStarCount(): Flow<Int> = repositoryDao.observeStarredCount()
}
