package uk.adedamola.stargazer.data.repository

import kotlinx.coroutines.flow.Flow
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.local.database.RepositoryTag
import uk.adedamola.stargazer.data.local.database.RepositoryTagDao
import uk.adedamola.stargazer.data.local.database.SearchPreset
import uk.adedamola.stargazer.data.local.database.SearchPresetDao
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.local.database.TagDao
import javax.inject.Inject
import javax.inject.Singleton

enum class SortOption {
    STARS, FORKS, UPDATED, CREATED, NAME
}

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

@Singleton
class OrganizationRepository @Inject constructor(
    private val repositoryDao: RepositoryDao,
    private val tagDao: TagDao,
    private val repositoryTagDao: RepositoryTagDao,
    private val searchPresetDao: SearchPresetDao
) {

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
