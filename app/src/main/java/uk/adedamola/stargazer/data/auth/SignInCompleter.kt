package uk.adedamola.stargazer.data.auth

import kotlinx.coroutines.CancellationException
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubUser
import javax.inject.Inject

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

    /** A different GitHub account signed in; its data would replace [old]'s ([counts] of it). */
    data class AccountChange(
        val old: AccountSummary,
        val new: AccountSummary,
        val counts: LocalDataCounts,
        val pending: PendingSignIn
    ) : SignInOutcome

    /** Nobody knows whose data is on the phone (the earlier version's token no longer worked, or no account was recorded). */
    data class KeepData(
        val new: AccountSummary,
        val counts: LocalDataCounts,
        val pending: PendingSignIn
    ) : SignInOutcome

    /**
     * GitHub approved [pending], but the earlier version's token could not be checked (offline,
     * a rate limit, a GitHub error). Nothing is stored; [SignInCompleter.recheck] tries again with
     * the same approval, so the person never approves a second code.
     */
    data class CheckPending(val pending: PendingSignIn) : SignInOutcome
}

/**
 * Finishes a sign-in once GitHub approved the code: reads the account with the new key and
 * compares its numeric user id with the account recorded on this phone (logins can be renamed, so
 * they are never compared).
 *
 * - No recorded account and nothing stored, or the same user id: the account is recorded and the
 *   key stored.
 * - A different user id: [SignInOutcome.AccountChange]; nothing is stored until the person answers.
 * - The data owner is unknown (a dead token from the earlier version, or local data with no recorded
 *   account): [SignInOutcome.KeepData].
 * - The earlier version's token cannot be checked yet: [SignInOutcome.CheckPending]; nothing is
 *   stored, and [recheck] tries again with the same approval.
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
    private val localData: LocalDataSummary,
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
        localData: LocalDataSummary
    ) : this(
        api, accountDao, tokenManager, wipe, legacyIdentifier, legacyStore, localData, System::currentTimeMillis
    )

    /** The failure is IOException (offline) or HttpException. */
    suspend fun complete(key: String): Result<SignInOutcome> = runSafely {
        val user = api.getAuthenticatedUser("Bearer $key")
        decide(PendingSignIn(key, user))
    }

    /**
     * Checks the earlier version's token again for an approval that [complete] returned as
     * [SignInOutcome.CheckPending], and goes on as [complete] would. No new code is requested.
     */
    suspend fun recheck(pending: PendingSignIn): Result<SignInOutcome> = runSafely { decide(pending) }

    private suspend fun decide(pending: PendingSignIn): SignInOutcome {
        if (legacyIdentifier.identify() == LegacyIdentify.Unresolved) return SignInOutcome.CheckPending(pending)
        val user = pending.user
        val recorded = accountDao.get()
        // No record but data on the phone: an earlier version that was signed out before the
        // update left it, and nobody knows whose it is.
        val counts = localData.counts()
        val unknownOwner = legacyStore.ownerUnknown() || (recorded == null && counts.isNotEmpty())
        return when {
            unknownOwner -> SignInOutcome.KeepData(
                new = user.summary(),
                counts = counts,
                pending = pending
            )
            recorded == null || recorded.userId == user.id -> {
                // Same account: refresh a renamed login or a new avatar, keep every table.
                accountDao.replace(user.record())
                tokenManager.saveToken(pending.key)
                SignInOutcome.Done
            }
            else -> {
                SignInOutcome.AccountChange(
                    old = AccountSummary(recorded.userId, recorded.login, localData.profileName(recorded.userId), recorded.avatarUrl),
                    new = user.summary(),
                    counts = counts,
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
