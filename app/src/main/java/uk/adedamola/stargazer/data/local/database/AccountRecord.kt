package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The GitHub account whose data this device holds. At most one row.
 *
 * It lives in the backed-up database, apart from the excluded access key, so that after a
 * restore or a sign-out the same account (matched by numeric [userId]) keeps its local data.
 */
@Entity(tableName = "account")
data class AccountRecord(
    @PrimaryKey val userId: Long,
    val login: String,
    val avatarUrl: String,
    val recordedAt: Long
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM account LIMIT 1")
    suspend fun get(): AccountRecord?

    @Query("SELECT * FROM account LIMIT 1")
    fun observe(): Flow<AccountRecord?>

    @Query("DELETE FROM account")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(account: AccountRecord)

    /** Replaces the single account row. */
    @Transaction
    suspend fun replace(account: AccountRecord) {
        deleteAll()
        insert(account)
    }
}
