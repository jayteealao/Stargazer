package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "repository_tags",
    primaryKeys = ["repositoryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RepositoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["repositoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["repositoryId"]),
        Index(value = ["tagId"])
    ]
)
data class RepositoryTag(
    val repositoryId: Int,
    val tagId: Int
)

data class RepositoryWithTags(
    val repository: RepositoryEntity,
    val tags: List<Tag>
)

@Dao
interface RepositoryTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagToRepository(repositoryTag: RepositoryTag)

    @Query("DELETE FROM repository_tags WHERE repositoryId = :repositoryId AND tagId = :tagId")
    suspend fun removeTagFromRepository(repositoryId: Int, tagId: Int)

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM repository_tags WHERE repositoryId = :repositoryId)")
    fun getTagsForRepository(repositoryId: Int): Flow<List<Tag>>

    @Query("SELECT * FROM repositories WHERE id IN (SELECT repositoryId FROM repository_tags WHERE tagId = :tagId)")
    fun getRepositoriesWithTag(tagId: Int): Flow<List<RepositoryEntity>>

    @Query("DELETE FROM repository_tags WHERE repositoryId = :repositoryId")
    suspend fun deleteAllTagsForRepository(repositoryId: Int)

    @Query("DELETE FROM repository_tags WHERE tagId = :tagId")
    suspend fun deleteAllRepositoriesForTag(tagId: Int)
}
