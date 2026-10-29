package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * An unstar that GitHub has not confirmed yet, or whose Undo window is still open. While a row
 * exists, its repository is hidden from Home but keeps its favorite, pin and tags; the repository
 * and its local data are deleted only after GitHub confirms and the window ends. The row survives
 * process death, so the next start can settle it against GitHub. No foreign key: the row must
 * never disappear with the repository row by a cascade.
 */
@Entity(tableName = "pending_unstars")
data class PendingUnstarEntity(
    @PrimaryKey val repositoryId: Int,
    val owner: String,
    val name: String,
    /** [SENDING] until GitHub answers 204, then [CONFIRMED]. */
    val state: String,
    val windowEndsAt: Long,
    val createdAt: Long,
) {
    companion object {
        const val SENDING = "SENDING"
        const val CONFIRMED = "CONFIRMED"
    }
}

@Dao
interface PendingUnstarDao {
    @Query("SELECT repositoryId FROM pending_unstars")
    fun observeIds(): Flow<List<Int>>

    @Query("SELECT * FROM pending_unstars WHERE repositoryId = :id")
    fun observe(id: Int): Flow<PendingUnstarEntity?>

    @Query("SELECT * FROM pending_unstars WHERE repositoryId = :id")
    suspend fun get(id: Int): PendingUnstarEntity?

    @Query("SELECT * FROM pending_unstars")
    suspend fun getAll(): List<PendingUnstarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: PendingUnstarEntity)

    @Query("UPDATE pending_unstars SET state = :state WHERE repositoryId = :id")
    suspend fun setState(id: Int, state: String)

    @Query("DELETE FROM pending_unstars WHERE repositoryId = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM pending_unstars")
    suspend fun deleteAll()
}
