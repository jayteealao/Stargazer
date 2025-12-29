package uk.adedamola.stargazer.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.RepositoryTagDao
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.SearchPresetDao
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.local.database.TagDao
import uk.adedamola.stargazer.data.mappers.toDomainModel
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class SortOption {
    STARS, FORKS, UPDATED, CREATED, NAME
}

/**
 * Filter options for repository queries
 */
data class FilterOptions(
    val language: String? = null,
    val minStars: Int? = null,
    val maxStars: Int? = null,
    val hasDescription: Boolean = false,
    val hasHomepage: Boolean = false,
    val hasTopics: Boolean = false,
    val favoritesOnly: Boolean = false,
    val pinnedOnly: Boolean = false,
    val tagIds: List<Int> = emptyList()
)

/**
 * Repository for managing local organization features:
 * - Tags/Collections for categorizing repositories
 * - Favorites and Pinned repositories
 * - Saved search presets
 * - Advanced sorting and filtering
 */
@Singleton
class OrganizationRepository @Inject constructor(
    private val repositoryDao: RepositoryDao,
    private val tagDao: TagDao,
    private val repositoryTagDao: RepositoryTagDao,
    private val searchPresetDao: SearchPresetDao
) {

    companion object {
        private const val PAGE_SIZE = 20
        private const val PREFETCH_DISTANCE = 5
        private const val INITIAL_LOAD_SIZE = 40
    }

    // Paging methods for displaying repository lists
    fun getRepositoriesPagingSorted(sortBy: SortOption): Flow<PagingData<GitHubRepository>> {
        val pagingSourceFactory = when (sortBy) {
            SortOption.STARS -> { repositoryDao.getRepositoriesByStarsPaging() }
            SortOption.FORKS -> { repositoryDao.getRepositoriesByForksPaging() }
            SortOption.UPDATED -> { repositoryDao.getRepositoriesByUpdatedPaging() }
            SortOption.CREATED -> { repositoryDao.getRepositoriesByCreatedPaging() }
            SortOption.NAME -> { repositoryDao.getRepositoriesByNamePaging() }
        }

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    fun getFavoriteRepositoriesPaging(): Flow<PagingData<GitHubRepository>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { repositoryDao.getFavoriteRepositoriesPaging() }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    fun getPinnedRepositoriesPaging(): Flow<PagingData<GitHubRepository>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { repositoryDao.getPinnedRepositoriesPaging() }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    // Tags
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun createTag(name: String, color: String): Long {
        return tagDao.insertTag(Tag(name = name, color = color))
    }

    suspend fun deleteTag(tag: Tag) {
        tagDao.deleteTag(tag)
    }

    suspend fun addTagToRepository(repositoryId: Int, tagId: Int) {
        repositoryTagDao.addTagToRepository(RepositoryTag(repositoryId, tagId))
    }

    suspend fun removeTagFromRepository(repositoryId: Int, tagId: Int) {
        repositoryTagDao.removeTagFromRepository(repositoryId, tagId)
    }

    fun getTagsForRepository(repositoryId: Int): Flow<List<Tag>> {
        return repositoryTagDao.getTagsForRepository(repositoryId)
    }

    fun getRepositoriesWithTag(tagId: Int): Flow<List<RepositoryEntity>> {
        return repositoryTagDao.getRepositoriesWithTag(tagId)
    }

    fun getRepositoriesWithTagPaging(tagId: Int): Flow<PagingData<GitHubRepository>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { repositoryTagDao.getRepositoriesWithTagPaging(tagId) }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    // Repository queries
    suspend fun getRepositoryById(id: Int): RepositoryEntity? {
        return repositoryDao.getRepositoryById(id)
    }

    // Favorites and Pinned
    suspend fun toggleFavorite(repositoryId: Int, isFavorite: Boolean) {
        repositoryDao.updateFavorite(repositoryId, isFavorite)
    }

    suspend fun togglePinned(repositoryId: Int, isPinned: Boolean) {
        repositoryDao.updatePinned(repositoryId, isPinned)
    }

    fun getFavoriteRepositories(): Flow<List<RepositoryEntity>> {
        return repositoryDao.getFavoriteRepositories()
    }

    fun getPinnedRepositories(): Flow<List<RepositoryEntity>> {
        return repositoryDao.getPinnedRepositories()
    }

    // Sorting
    fun getRepositoriesSorted(sortBy: SortOption): Flow<List<RepositoryEntity>> {
        return when (sortBy) {
            SortOption.STARS -> repositoryDao.getRepositoriesByStars()
            SortOption.FORKS -> repositoryDao.getRepositoriesByForks()
            SortOption.UPDATED -> repositoryDao.getRepositoriesByUpdated()
            SortOption.CREATED -> repositoryDao.getRepositoriesByCreated()
            SortOption.NAME -> repositoryDao.getRepositoriesByName()
        }
    }

    // Search Presets
    fun getAllPresets(): Flow<List<SearchPreset>> = searchPresetDao.getAllPresets()

    suspend fun savePreset(preset: SearchPreset): Long {
        return searchPresetDao.insertPreset(preset)
    }

    suspend fun deletePreset(preset: SearchPreset) {
        searchPresetDao.deletePreset(preset)
    }

    suspend fun getPresetById(id: Int): SearchPreset? {
        return searchPresetDao.getPresetById(id)
    }
}
