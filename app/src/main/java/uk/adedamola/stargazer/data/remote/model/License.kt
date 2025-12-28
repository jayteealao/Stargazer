package uk.adedamola.stargazer.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class License(
    @SerialName("key")
    val key: String,
    @SerialName("name")
    val name: String,
    @SerialName("spdx_id")
    val spdxId: String?,
    @SerialName("url")
    val url: String?
)
