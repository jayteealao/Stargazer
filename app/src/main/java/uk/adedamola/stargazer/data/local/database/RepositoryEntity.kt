package uk.adedamola.stargazer.data.local.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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
    val cachedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val starredAt: Long? = null  // Timestamp when user starred the repo on GitHub
)

@Dao
interface RepositoryDao {
    // Paging queries for main list displays
    @Query("SELECT * FROM repositories ORDER BY stargazersCount DESC")
    fun getAllRepositoriesPaging(): PagingSource<Int, RepositoryEntity>

    @Query("""
        SELECT * FROM repositories
        WHERE name LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR fullName LIKE '%' || :query || '%'
           OR ownerLogin LIKE '%' || :query || '%'
           OR language LIKE '%' || :query || '%'
           OR topics LIKE '%' || :query || '%'
        ORDER BY stargazersCount DESC
    """)
    fun searchRepositoriesPaging(query: String): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE language = :language ORDER BY stargazersCount DESC")
    fun getRepositoriesByLanguagePaging(language: String): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE stargazersCount >= :minStars AND stargazersCount <= :maxStars ORDER BY stargazersCount DESC")
    fun getRepositoriesByStarRangePaging(minStars: Int, maxStars: Int): PagingSource<Int, RepositoryEntity>

    // Non-paging queries for single items or counts
    @Query("SELECT * FROM repositories ORDER BY stargazersCount DESC")
    fun getAllRepositories(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun getRepositoryById(id: Int): RepositoryEntity?

    @Query("""
        SELECT * FROM repositories
        WHERE name LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR fullName LIKE '%' || :query || '%'
           OR ownerLogin LIKE '%' || :query || '%'
           OR language LIKE '%' || :query || '%'
           OR topics LIKE '%' || :query || '%'
        ORDER BY stargazersCount DESC
    """)
    fun searchRepositories(query: String): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE language = :language ORDER BY stargazersCount DESC")
    fun getRepositoriesByLanguage(language: String): Flow<List<RepositoryEntity>>

    @Query("SELECT DISTINCT language FROM repositories WHERE language IS NOT NULL AND language != '' ORDER BY language ASC")
    fun getDistinctLanguages(): Flow<List<String>>

    /**
     * Upserts repositories from API while preserving local user data.
     * Uses custom SQL to update only API fields, keeping isFavorite and isPinned intact.
     */
    @Transaction
    suspend fun upsertRepositories(repositories: List<RepositoryEntity>) {
        repositories.forEach { repo ->
            val existing = getRepositoryById(repo.id)
            if (existing != null) {
                // Update only API fields, preserve local fields
                updateRepositoryFromApi(
                    id = repo.id,
                    name = repo.name,
                    fullName = repo.fullName,
                    ownerLogin = repo.ownerLogin,
                    ownerAvatarUrl = repo.ownerAvatarUrl,
                    htmlUrl = repo.htmlUrl,
                    description = repo.description,
                    fork = repo.fork,
                    createdAt = repo.createdAt,
                    updatedAt = repo.updatedAt,
                    pushedAt = repo.pushedAt,
                    homepage = repo.homepage,
                    size = repo.size,
                    stargazersCount = repo.stargazersCount,
                    watchersCount = repo.watchersCount,
                    language = repo.language,
                    forksCount = repo.forksCount,
                    openIssuesCount = repo.openIssuesCount,
                    defaultBranch = repo.defaultBranch,
                    topics = repo.topics,
                    visibility = repo.visibility,
                    licenseName = repo.licenseName,
                    cachedAt = repo.cachedAt,
                    starredAt = repo.starredAt ?: existing.starredAt
                    // isFavorite and isPinned are NOT updated - they're preserved!
                )
            } else {
                // New repo - insert with defaults
                insertRepositoryInternal(repo)
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRepositoryInternal(repository: RepositoryEntity)

    @Query("""
        UPDATE repositories SET
            name = :name,
            fullName = :fullName,
            ownerLogin = :ownerLogin,
            ownerAvatarUrl = :ownerAvatarUrl,
            htmlUrl = :htmlUrl,
            description = :description,
            fork = :fork,
            createdAt = :createdAt,
            updatedAt = :updatedAt,
            pushedAt = :pushedAt,
            homepage = :homepage,
            size = :size,
            stargazersCount = :stargazersCount,
            watchersCount = :watchersCount,
            language = :language,
            forksCount = :forksCount,
            openIssuesCount = :openIssuesCount,
            defaultBranch = :defaultBranch,
            topics = :topics,
            visibility = :visibility,
            licenseName = :licenseName,
            cachedAt = :cachedAt,
            starredAt = :starredAt
        WHERE id = :id
    """)
    suspend fun updateRepositoryFromApi(
        id: Int,
        name: String,
        fullName: String,
        ownerLogin: String,
        ownerAvatarUrl: String,
        htmlUrl: String,
        description: String?,
        fork: Boolean,
        createdAt: String,
        updatedAt: String,
        pushedAt: String?,
        homepage: String?,
        size: Int,
        stargazersCount: Int,
        watchersCount: Int,
        language: String?,
        forksCount: Int,
        openIssuesCount: Int,
        defaultBranch: String,
        topics: String,
        visibility: String,
        licenseName: String?,
        cachedAt: Long,
        starredAt: Long?
    )

    // Keep old methods for compatibility but mark as deprecated
    @Deprecated("Use upsertRepositories instead to preserve local user data")
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepository(repository: RepositoryEntity)

    @Deprecated("Use upsertRepositories instead to preserve local user data")
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepositories(repositories: List<RepositoryEntity>)

    @Query("DELETE FROM repositories")
    suspend fun deleteAll()

    @Query("DELETE FROM repositories WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    // Favorites and Pinned
    @Query("UPDATE repositories SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFavorite: Boolean)

    @Query("UPDATE repositories SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: Int, isPinned: Boolean)

    // Paging queries for favorites and pinned
    @Query("SELECT * FROM repositories WHERE isFavorite = 1 ORDER BY stargazersCount DESC")
    fun getFavoriteRepositoriesPaging(): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE isPinned = 1 ORDER BY stargazersCount DESC")
    fun getPinnedRepositoriesPaging(): PagingSource<Int, RepositoryEntity>

    // Paging queries with different sort orders
    @Query("SELECT * FROM repositories ORDER BY starredAt DESC")
    fun getRepositoriesByStarredAtPaging(): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories ORDER BY stargazersCount DESC")
    fun getRepositoriesByStarsPaging(): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories ORDER BY forksCount DESC")
    fun getRepositoriesByForksPaging(): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories ORDER BY updatedAt DESC")
    fun getRepositoriesByUpdatedPaging(): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories ORDER BY createdAt DESC")
    fun getRepositoriesByCreatedPaging(): PagingSource<Int, RepositoryEntity>

    @Query("SELECT * FROM repositories ORDER BY name ASC")
    fun getRepositoriesByNamePaging(): PagingSource<Int, RepositoryEntity>

    // Non-paging versions (for compatibility)
    @Query("SELECT * FROM repositories WHERE isFavorite = 1 ORDER BY stargazersCount DESC")
    fun getFavoriteRepositories(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE isPinned = 1 ORDER BY stargazersCount DESC")
    fun getPinnedRepositories(): Flow<List<RepositoryEntity>>

    // Sorting options (non-paging)
    @Query("SELECT * FROM repositories ORDER BY stargazersCount DESC")
    fun getRepositoriesByStars(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories ORDER BY forksCount DESC")
    fun getRepositoriesByForks(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories ORDER BY updatedAt DESC")
    fun getRepositoriesByUpdated(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories ORDER BY createdAt DESC")
    fun getRepositoriesByCreated(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories ORDER BY name ASC")
    fun getRepositoriesByName(): Flow<List<RepositoryEntity>>

    // Advanced filters
    @Query("SELECT * FROM repositories WHERE description IS NOT NULL AND description != ''")
    fun getRepositoriesWithDescription(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE homepage IS NOT NULL AND homepage != ''")
    fun getRepositoriesWithHomepage(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE topics != ''")
    fun getRepositoriesWithTopics(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE stargazersCount >= :minStars AND stargazersCount <= :maxStars")
    fun getRepositoriesByStarRange(minStars: Int, maxStars: Int): Flow<List<RepositoryEntity>>

    // Sync-related queries
    @Query("SELECT MAX(starredAt) FROM repositories")
    suspend fun getNewestStarredAt(): Long?

    @Query("SELECT COUNT(*) FROM repositories")
    suspend fun getRepositoryCount(): Int

    @Query("SELECT COUNT(*) FROM repositories WHERE starredAt IS NOT NULL")
    suspend fun getRepositoryCountWithStarredAt(): Int

    @Query("SELECT * FROM repositories ORDER BY starredAt DESC")
    fun getRepositoriesByStarredAt(): Flow<List<RepositoryEntity>>
}
