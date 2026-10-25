package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.paging.PagingSource
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A GitHub star list (read-only in the app), cached locally. [id] is the GraphQL node id. */
@Entity(tableName = "star_lists")
data class StarListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val isPrivate: Boolean,
    val itemCount: Int,
    val position: Int,
    val fetchedAt: Long
)

/**
 * Membership of a repository in a star list. No foreign key to `repositories`: GitHub allows
 * a list to hold repositories that are not starred.
 */
@Entity(
    tableName = "star_list_items",
    primaryKeys = ["listId", "repositoryId"],
    foreignKeys = [
        ForeignKey(
            entity = StarListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("repositoryId")]
)
data class StarListItemEntity(
    val listId: String,
    val repositoryId: Long
)

@Dao
interface StarListDao {
    @Query("SELECT * FROM star_lists ORDER BY position ASC")
    fun observeLists(): Flow<List<StarListEntity>>

    @Upsert
    suspend fun upsertLists(lists: List<StarListEntity>)

    @Upsert
    suspend fun upsertItems(items: List<StarListItemEntity>)

    /** Deletes every list; their items cascade. */
    @Query("DELETE FROM star_lists")
    suspend fun deleteAll()

    @Query("SELECT * FROM star_lists ORDER BY position ASC")
    suspend fun getLists(): List<StarListEntity>

    @Insert
    suspend fun insertLists(lists: List<StarListEntity>)

    @Insert
    suspend fun insertItems(items: List<StarListItemEntity>)

    /** Replaces every cached list and its items with GitHub's latest answer, all or nothing. */
    @Transaction
    suspend fun replaceAll(lists: List<StarListEntity>, items: List<StarListItemEntity>) {
        deleteAll()
        insertLists(lists)
        insertItems(items)
    }

    /**
     * The starred repositories on this phone that belong to [listId], newest star first. A list
     * entry that is not starred here has no row in `repositories`, so it never shows.
     */
    @Query(
        "SELECT * FROM repositories WHERE id IN " +
            "(SELECT repositoryId FROM star_list_items WHERE listId = :listId) ORDER BY starredAt DESC"
    )
    fun repositoriesInListPaging(listId: String): PagingSource<Int, RepositoryEntity>
}
