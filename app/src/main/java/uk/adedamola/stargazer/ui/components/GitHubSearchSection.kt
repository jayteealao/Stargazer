package uk.adedamola.stargazer.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.StarControlState
import uk.adedamola.stargazer.ui.screens.GitHubSearchState
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryTextGrey
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import java.text.NumberFormat

/** A search section label: `IN_YOUR_STARS · 3`, `ON_GITHUB · 2,814`. */
@Composable
fun SearchSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = FactoryCyan,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        modifier = modifier.semantics { heading() },
    )
}

/** `label · n`, with the count in the phone's number format. */
fun sectionLabel(label: String, count: Int): String = "$label · ${NumberFormat.getIntegerInstance().format(count)}"

/**
 * The `ON_GITHUB` part of search: GitHub-wide results with a star control in place of pin and
 * favorite, and no tag chips. A tap opens the repository on github.com: the detail screen reads
 * starred repos only, and a repo starred here is on Home. The count and the status lines are read
 * out when they arrive, since they appear below what the person is typing.
 */
fun LazyListScope.gitHubSearchSection(
    state: GitHubSearchState,
    controlStates: Map<Int, StarControlState>,
    onToggle: (GitHubRepository) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    if (state is GitHubSearchState.Idle) return
    item(key = "on-github-label") {
        SearchSectionLabel(
            if (state is GitHubSearchState.Loaded) sectionLabel("ON_GITHUB", state.total) else "ON_GITHUB",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    when (state) {
        GitHubSearchState.Loading -> item(key = "on-github-status") { StatusText("SEARCHING_GITHUB…", FactoryTextGrey) }
        GitHubSearchState.Offline -> item(key = "on-github-status") {
            StatusText("CONNECTION_NEEDED — GitHub search needs the internet.", FactoryYellow)
        }
        is GitHubSearchState.Failed -> item(key = "on-github-status") { StatusText(state.text, FactoryYellow) }
        is GitHubSearchState.Loaded -> {
            if (state.items.isEmpty()) {
                item(key = "on-github-status") { StatusText("NO_OTHER_MATCHES_ON_GITHUB", FactoryTextGrey) }
            }
            items(state.items, key = { "gh-${it.id}" }) { repo ->
                val uriHandler = LocalUriHandler.current
                val control = controlStates[repo.id]
                    ?: StarControlState(starred = false, inFlight = false, enabled = false, status = null, rateLimitedUntil = null)
                RepoCard(
                    repoId = repo.id,
                    repoName = repo.name,
                    repoDescription = repo.description ?: "No description available",
                    language = repo.language ?: "Unknown",
                    stars = repo.stargazersCount,
                    owner = repo.owner.login,
                    ownerAvatarUrl = repo.owner.avatarUrl,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    showTags = false,
                    sharedKeyPrefix = "gh-",
                    modifier = Modifier.clickable(onClickLabel = "Open on GitHub") {
                        runCatching { uriHandler.openUri(repo.htmlUrl) }
                    },
                    trailing = {
                        GitHubStarToggle(state = control, fullName = repo.fullName, onClick = { onToggle(repo) })
                    },
                )
            }
        }
        GitHubSearchState.Idle -> Unit
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
