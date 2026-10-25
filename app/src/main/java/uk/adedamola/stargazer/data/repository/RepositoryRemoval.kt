package uk.adedamola.stargazer.data.repository

import uk.adedamola.stargazer.data.local.database.AppDatabase

/**
 * Deletes a repository from this phone with its tag assignments; its favorite and pin go with the
 * row. Call it inside a transaction.
 */
internal suspend fun AppDatabase.removeRepository(id: Int) {
    repositoryTagDao().deleteAllTagsForRepository(id)
    repositoryDao().deleteById(id)
}
