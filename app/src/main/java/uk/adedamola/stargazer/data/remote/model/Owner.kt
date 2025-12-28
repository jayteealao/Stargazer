package uk.adedamola.stargazer.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Owner(
    @SerialName("login")
    val login: String,
    @SerialName("id")
    val id: Int,
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("url")
    val url: String,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("type")
    val type: String
)
