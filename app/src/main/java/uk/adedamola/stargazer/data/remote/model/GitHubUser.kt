package uk.adedamola.stargazer.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The signed-in GitHub account, from `GET /user`. [id] is the stable numeric account id. */
@Serializable
data class GitHubUser(
    val id: Long,
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val name: String? = null
)
