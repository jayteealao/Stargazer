package uk.adedamola.stargazer.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wrapper model for starred repository response when using
 * Accept: application/vnd.github.star+json header.
 *
 * This format includes the starred_at timestamp indicating when
 * the user starred the repository.
 */
@Serializable
data class StarredRepository(
    @SerialName("starred_at")
    val starredAt: String,  // ISO 8601 timestamp, e.g., "2024-01-15T10:30:00Z"
    @SerialName("repo")
    val repo: GitHubRepository
)
