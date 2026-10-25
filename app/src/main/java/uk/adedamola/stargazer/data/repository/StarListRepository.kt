package uk.adedamola.stargazer.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.adedamola.stargazer.data.local.database.StarListDao
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.mappers.toDomainModel
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import javax.inject.Inject
import javax.inject.Singleton

/** The cached GitHub star lists (read-only) and the Home filter by list. */
@Singleton
class StarListRepository @Inject constructor(
    private val starListDao: StarListDao
) {
    fun observeLists(): Flow<List<StarListEntity>> = starListDao.observeLists()

    /** The starred repositories on this phone that belong to [listId]; nothing is added. */
    fun repositoriesInListPaging(listId: String): Flow<PagingData<GitHubRepository>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { starListDao.repositoriesInListPaging(listId) }
        ).flow.map { pagingData -> pagingData.map { it.toDomainModel() } }

    private companion object {
        const val PAGE_SIZE = 20
        const val PREFETCH_DISTANCE = 5
        const val INITIAL_LOAD_SIZE = 40
    }
}
