package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.CancellationException
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.GitHubProfileDao
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.SearchPresetDao
import uk.adedamola.stargazer.data.local.database.TagDao
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubUser
import java.io.IOException
import javax.inject.Inject

/** A GitHub account as the account dialogs show it. [name] is null when unknown or not set. */
data class AccountSummary(
    val userId: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?
)

/** How much local data the keep-data question is about. */
data class LocalDataCounts(val repos: Int, val tags: Int, val presets: Int)

/**
 * An approved sign-in that waits for the person's answer. The key stays in memory only: it is
 * stored after Continue, Keep or Remove, and forgotten on Cancel.
 */
class PendingSignIn internal constructor(val key: String, val user: GitHubUser) {
    override fun toString(): String = "PendingSignIn(userId=${user.id}, key=<hidden>)"
}

sealed interface SignInOutcome {
    /** The key is stored; Home opens. */
    data object Done : SignInOutcome

    /** A different GitHub account signed in; its data would replace [old]'s. */
    data class AccountChange(
        val old: AccountSummary,
        val new: AccountSummary,
        val pending: PendingSignIn
    ) : SignInOutcome

    /** The earlier version's token no longer worked, so nobody knows whose data is on the phone. */
    data class KeepData(
        val new: AccountSummary,
        val counts: LocalDataCounts,
        val pending: PendingSignIn
    ) : SignInOutcome
}

/**
 * Finishes a sign-in once GitHub approved the code: reads the account with the new key and
 * compares its numeric user id with the account recorded on this phone (logins can be renamed, so
 * they are never compared).
 *
 * - No recorded account, or the same user id: the account is recorded and the key stored.
 * - A different user id: [SignInOutcome.AccountChange]; nothing is stored until the person answers.
 * - The data owner is unknown (dead token from the earlier version): [SignInOutcome.KeepData].
 *
 * Cancel needs no call here: the pending key was never stored, so the caller drops the
 * [PendingSignIn] and every table and the recorded account stay as they were.
 *
 * The key is always stored last, because storing it opens Home and starts a sync.
 */
class SignInCompleter(
    private val api: GitHubApiService,
    private val accountDao: AccountDao,
    private val tokenManager: TokenManager,
    private val wipe: AccountDataWipe,
    private val legacyIdentifier: LegacyAccountIdentifier,
    private val legacyStore: LegacyAccountStore,
    private val repositoryDao: RepositoryDao,
    private val tagDao: TagDao,
    private val presetDao: SearchPresetDao,
    private val profileDao: GitHubProfileDao,
    private val clock: () -> Long
) {
    @Inject
    constructor(
        api: GitHubApiService,
        accountDao: AccountDao,
        tokenManager: TokenManager,
        wipe: AccountDataWipe,
        legacyIdentifier: LegacyAccountIdentifier,
        legacyStore: LegacyAccountStore,
        repositoryDao: RepositoryDao,
        tagDao: TagDao,
        presetDao: SearchPresetDao,
        profileDao: GitHubProfileDao
    ) : this(
        api, accountDao, tokenManager, wipe, legacyIdentifier, legacyStore,
        repositoryDao, tagDao, presetDao, profileDao, System::currentTimeMillis
    )

    /** The failure is IOException (offline, or the earlier version's token could not be checked) or HttpException. */
    suspend fun complete(key: String): Result<SignInOutcome> = runSafely {
        val user = api.getAuthenticatedUser("Bearer $key")
        if (legacyIdentifier.identify() == LegacyIdentify.Unresolved) {
            throw IOException("The earlier sign-in could not be checked")
        }
        val pending = PendingSignIn(key, user)
        val recorded = accountDao.get()
        when {
            legacyStore.ownerUnknown() -> SignInOutcome.KeepData(
                new = user.summary(),
                counts = LocalDataCounts(
                    repos = repositoryDao.getRepositoryCount(),
                    tags = tagDao.count(),
                    presets = presetDao.count()
                ),
                pending = pending
            )
            recorded == null || recorded.userId == user.id -> {
                // Same account: refresh a renamed login or a new avatar, keep every table.
                accountDao.replace(user.record())
                tokenManager.saveToken(key)
                SignInOutcome.Done
            }
            else -> {
                val profileName = profileDao.get()?.takeIf { it.userId == recorded.userId }?.name
                SignInOutcome.AccountChange(
                    old = AccountSummary(recorded.userId, recorded.login, profileName, recorded.avatarUrl),
                    new = user.summary(),
                    pending = pending
                )
            }
        }
    }

    /** Continue on the account-change dialog: remove the old account's data, then sign in. */
    suspend fun confirmAccountChange(pending: PendingSignIn): Result<Unit> = runSafely {
        wipe.wipeAndRecord(pending.user.record())
        tokenManager.saveToken(pending.key)
    }

    /** Keep on the keep-data dialog: the data on the phone now belongs to this account. */
    suspend fun keepData(pending: PendingSignIn): Result<Unit> = runSafely {
        accountDao.replace(pending.user.record())
        legacyStore.setOwnerUnknown(false)
        tokenManager.saveToken(pending.key)
    }

    /** Remove on the keep-data dialog: delete the data of the unknown owner, then sign in. */
    suspend fun removeData(pending: PendingSignIn): Result<Unit> = runSafely {
        wipe.wipeAndRecord(pending.user.record())
        legacyStore.setOwnerUnknown(false)
        tokenManager.saveToken(pending.key)
    }

    private fun GitHubUser.record() =
        AccountRecord(userId = id, login = login, avatarUrl = avatarUrl, recordedAt = clock())

    private fun GitHubUser.summary() = AccountSummary(id, login, name?.takeIf { it.isNotBlank() }, avatarUrl)

    private suspend fun <T> runSafely(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
