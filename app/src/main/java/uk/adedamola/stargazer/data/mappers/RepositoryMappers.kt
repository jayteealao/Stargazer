package uk.adedamola.stargazer.data.mappers

import uk.adedamola.stargazer.data.local.database.RepositoryEntity
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.remote.model.StarredRepository
import java.time.Instant

fun GitHubRepository.toEntity(starredAt: Long? = null): RepositoryEntity {
    return RepositoryEntity(
        id = id,
        name = name,
        fullName = fullName,
        ownerLogin = owner.login,
        ownerAvatarUrl = owner.avatarUrl,
        htmlUrl = htmlUrl,
        description = description,
        fork = fork,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pushedAt = pushedAt,
        homepage = homepage,
        size = size,
        stargazersCount = stargazersCount,
        watchersCount = watchersCount,
        language = language,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        defaultBranch = defaultBranch,
        topics = topics.joinToString(","),
        visibility = visibility,
        licenseName = license?.name,
        starredAt = starredAt
    )
}

fun StarredRepository.toEntity(): RepositoryEntity {
    val starredAtMillis = try {
        Instant.parse(starredAt).toEpochMilli()
    } catch (e: Exception) {
        null
    }
    return repo.toEntity(starredAt = starredAtMillis)
}

fun RepositoryEntity.toDomainModel(): GitHubRepository {
    return GitHubRepository(
        id = id,
        name = name,
        fullName = fullName,
        owner = uk.adedamola.stargazer.data.remote.model.Owner(
            login = ownerLogin,
            id = 0, // We don't store owner ID in entity
            avatarUrl = ownerAvatarUrl,
            url = "",
            htmlUrl = "",
            type = "User"
        ),
        htmlUrl = htmlUrl,
        description = description,
        fork = fork,
        url = "",
        createdAt = createdAt,
        updatedAt = updatedAt,
        pushedAt = pushedAt,
        homepage = homepage,
        size = size,
        stargazersCount = stargazersCount,
        watchersCount = watchersCount,
        language = language,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        defaultBranch = defaultBranch,
        topics = if (topics.isNotEmpty()) topics.split(",") else emptyList(),
        visibility = visibility,
        license = licenseName?.let {
            uk.adedamola.stargazer.data.remote.model.License(
                key = it.lowercase().replace(" ", "-"),
                name = it,
                spdxId = null,
                url = null
            )
        }
    )
}
