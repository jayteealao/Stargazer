package uk.adedamola.stargazer.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The signed-in GitHub account, from `GET /user`. [id] is the stable numeric account id. The
 * profile fields default when GitHub leaves them out, so a short answer still reads.
 */
@Serializable
data class GitHubUser(
    val id: Long,
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val name: String? = null,
    val bio: String? = null,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("public_repos") val publicRepos: Int = 0,
    val location: String? = null,
    val company: String? = null,
    val blog: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)
