package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Cached GitHub profile of the signed-in account, shown offline. */
@Entity(tableName = "github_profile")
data class GitHubProfileEntity(
    @PrimaryKey val userId: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String,
    val bio: String?,
    val followers: Int,
    val following: Int,
    val publicRepos: Int,
    val starredCount: Int?,
    val location: String?,
    val company: String?,
    val blog: String?,
    val htmlUrl: String,
    val fetchedAt: Long
)

@Dao
interface GitHubProfileDao {
    @Query("SELECT * FROM github_profile LIMIT 1")
    fun observe(): Flow<GitHubProfileEntity?>

    @Query("SELECT * FROM github_profile LIMIT 1")
    suspend fun get(): GitHubProfileEntity?

    @Upsert
    suspend fun upsert(profile: GitHubProfileEntity)

    @Query("DELETE FROM github_profile")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(profile: GitHubProfileEntity)

    /** Keeps exactly one row, so the single-row reads stay right. */
    @Transaction
    suspend fun replace(profile: GitHubProfileEntity) {
        deleteAll()
        insert(profile)
    }

    @Query("UPDATE github_profile SET starredCount = :count WHERE userId = :userId")
    suspend fun updateStarredCount(userId: Long, count: Int)
}
