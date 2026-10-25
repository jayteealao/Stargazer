package uk.adedamola.stargazer.data.auth

import androidx.room.withTransaction
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes every piece of local data that belongs to the previous GitHub account and records the
 * new account, in one database transaction: either all of it happens or none of it does.
 *
 * The wipe set: tag assignments, starred repositories (with their favorite and pin flags), tags,
 * saved search presets, the cached profile, the cached star lists (their items cascade) and the
 * sync state. After the commit, the in-memory progress of the starred-repo sync is reset.
 *
 * Any table added later that holds account-derived data must be cleared here too.
 */
@Singleton
class AccountDataWipe(
    private val database: AppDatabase,
    private val mediator: StarredReposRemoteMediator,
    private val beforeCommit: suspend () -> Unit
) {
    @Inject
    constructor(database: AppDatabase, mediator: StarredReposRemoteMediator) :
        this(database, mediator, {})

    suspend fun wipeAndRecord(account: AccountRecord) {
        database.withTransaction {
            database.repositoryTagDao().deleteAll()
            database.repositoryDao().deleteAll()
            database.tagDao().deleteAll()
            database.searchPresetDao().deleteAll()
            database.gitHubProfileDao().deleteAll()
            database.starListDao().deleteAll()
            database.syncMetadataDao().deleteAll()
            beforeCommit()
            database.accountDao().replace(account)
        }
        mediator.resetSyncProgress()
    }
}
