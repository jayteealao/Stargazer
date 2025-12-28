package uk.adedamola.stargazer.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepository(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("owner")
    val owner: Owner,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("description")
    val description: String?,
    @SerialName("fork")
    val fork: Boolean,
    @SerialName("url")
    val url: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("pushed_at")
    val pushedAt: String?,
    @SerialName("homepage")
    val homepage: String?,
    @SerialName("size")
    val size: Int,
    @SerialName("stargazers_count")
    val stargazersCount: Int,
    @SerialName("watchers_count")
    val watchersCount: Int,
    @SerialName("language")
    val language: String?,
    @SerialName("forks_count")
    val forksCount: Int,
    @SerialName("open_issues_count")
    val openIssuesCount: Int,
    @SerialName("default_branch")
    val defaultBranch: String,
    @SerialName("topics")
    val topics: List<String> = emptyList(),
    @SerialName("visibility")
    val visibility: String,
    @SerialName("license")
    val license: License?
)
