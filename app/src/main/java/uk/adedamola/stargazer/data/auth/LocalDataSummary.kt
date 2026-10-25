package uk.adedamola.stargazer.data.auth

import uk.adedamola.stargazer.data.local.database.GitHubProfileDao
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.SearchPresetDao
import uk.adedamola.stargazer.data.local.database.TagDao
import javax.inject.Inject

/** A GitHub account as the account dialogs show it. [name] is null when unknown or not set. */
data class AccountSummary(
    val userId: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?
)

/** How much local data an account question is about. */
data class LocalDataCounts(val repos: Int, val tags: Int, val presets: Int) {
    fun isNotEmpty() = repos > 0 || tags > 0 || presets > 0
}

/**
 * What the sign-in questions say about the data on this phone: how much there is, and the name
 * of the account it belongs to. The counts cover what [AccountDataWipe] removes; every stored
 * repository counts, a repository whose unstar is pending included, because the wipe removes it too.
 */
class LocalDataSummary @Inject constructor(
    private val repositoryDao: RepositoryDao,
    private val tagDao: TagDao,
    private val presetDao: SearchPresetDao,
    private val profileDao: GitHubProfileDao,
) {
    suspend fun counts() = LocalDataCounts(
        repos = repositoryDao.getRepositoryCount(),
        tags = tagDao.count(),
        presets = presetDao.count()
    )

    /** The cached display name of [userId], or null when no profile of that account is cached. */
    suspend fun profileName(userId: Long): String? = profileDao.get()?.takeIf { it.userId == userId }?.name
}
