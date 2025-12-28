package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "repositories")
data class RepositoryEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String,
    val htmlUrl: String,
    val description: String?,
    val fork: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val pushedAt: String?,
    val homepage: String?,
    val size: Int,
    val stargazersCount: Int,
    val watchersCount: Int,
    val language: String?,
    val forksCount: Int,
    val openIssuesCount: Int,
    val defaultBranch: String,
    val topics: String, // Stored as comma-separated string
    val visibility: String,
    val licenseName: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories ORDER BY stargazersCount DESC")
    fun getAllRepositories(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun getRepositoryById(id: Int): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchRepositories(query: String): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE language = :language ORDER BY stargazersCount DESC")
    fun getRepositoriesByLanguage(language: String): Flow<List<RepositoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepository(repository: RepositoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepositories(repositories: List<RepositoryEntity>)

    @Query("DELETE FROM repositories")
    suspend fun deleteAll()

    @Query("DELETE FROM repositories WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
