package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Tracks the last sync time for different data types
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey
    val dataType: String,  // e.g., "starred_repos"
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val lastItemCreatedAt: String? = null  // GitHub's created_at of the most recent item
)

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE dataType = :dataType")
    suspend fun getMetadata(dataType: String): SyncMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateMetadata(metadata: SyncMetadata)

    @Query("DELETE FROM sync_metadata WHERE dataType = :dataType")
    suspend fun deleteMetadata(dataType: String)
}
