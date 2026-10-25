package uk.adedamola.stargazer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.ui.components.AccountAvatar
import uk.adedamola.stargazer.ui.components.StargazerButton
import uk.adedamola.stargazer.ui.theme.FactoryBorder
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite
import uk.adedamola.stargazer.ui.theme.FactoryWarnContainer
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import uk.adedamola.stargazer.ui.theme.safeBars
import java.text.NumberFormat
import java.util.Locale

/** The profile screen bound to its view model; links open in the browser. */
@Composable
fun ProfileRoute(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    ProfileScreen(
        state = state,
        onBack = onBack,
        onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
        onRefresh = viewModel::refresh
    )
}

/**
 * The signed-in account's GitHub profile, as last cached: loaded (with an offline line when the
 * last refresh never reached GitHub), loading the first time, or an error with nothing cached.
 * Empty fields are left out. [now] stamps the "saved … ago" line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onRefresh: () -> Unit,
    now: () -> Long = System::currentTimeMillis
) {
    Scaffold(
        containerColor = FactoryDarkGrey,
        contentWindowInsets = WindowInsets.safeBars,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FactoryTextWhite
                        )
                    }
                },
                title = {
                    Text(
                        text = "PROFILE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        letterSpacing = 1.5.sp,
                        color = FactoryTextGrey,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FactoryDarkGrey)
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state is ProfileUiState.Loaded && state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    when (state) {
                        is ProfileUiState.Loaded -> ProfileBody(state, onOpenLink, now)
                        ProfileUiState.Loading -> ProfileSkeleton()
                        ProfileUiState.Error -> ProfileError()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileBody(state: ProfileUiState.Loaded, onOpenLink: (String) -> Unit, now: () -> Long) {
    val profile = state.profile
    AccountAvatar(AccountSummary(profile.userId, profile.login, profile.name, profile.avatarUrl), size = 88.dp)
    Spacer(Modifier.height(20.dp))
    Text(
        text = profile.name ?: "@${profile.login}",
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = FactoryTextWhite,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() }
    )
    if (profile.name != null) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = "@${profile.login}",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = FactoryTextGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    profile.bio?.let { bio ->
        Spacer(Modifier.height(14.dp))
        Text(text = bio, fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.5.sp, color = FactoryTextWhite)
    }
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = FactoryBorder)
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        ProfileStat(profile.followers, "FOLLOWERS")
        ProfileStat(profile.following, "FOLLOWING")
        ProfileStat(profile.publicRepos, "REPOS")
        ProfileStat(state.starCount, "STARS")
    }
    HorizontalDivider(color = FactoryBorder)
    Spacer(Modifier.height(10.dp))
    profile.location?.let { ProfileFact(Icons.Outlined.LocationOn, it, "Location") }
    profile.company?.let { ProfileFact(Icons.Outlined.Business, it, "Company") }
    profile.blog?.let { blog ->
        ProfileFact(Icons.Outlined.Link, blog, "Website", color = FactoryCyan, onClick = { onOpenLink(blogUrl(blog)) })
    }
    if (state.offline) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = FactoryTextGrey, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "OFFLINE — saved ${savedAgo(profile.fetchedAt, now())}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = FactoryTextGrey
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    StargazerButton(
        text = "OPEN ON GITHUB",
        onClick = { onOpenLink(profile.htmlUrl) },
        icon = Icons.AutoMirrored.Filled.OpenInNew
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ProfileStat(value: Int, label: String) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
        Text(
            text = formatCompact(value),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontFeatureSettings = "tnum"),
            color = FactoryTextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        // One line at the default text size; a label wraps rather than clips at large sizes.
        Text(text = label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 0.5.sp, color = FactoryTextGrey, maxLines = 2)
    }
}

@Composable
private fun ProfileFact(
    icon: ImageVector,
    text: String,
    label: String,
    color: Color = FactoryTextWhite,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (onClick != null) 48.dp else 30.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open $label", role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = FactoryTextGrey, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Still blocks in the shape of the profile: no shimmer in a dim room. */
@Composable
private fun ProfileSkeleton() {
    val block = Color.White.copy(alpha = 0.06f)
    Column(
        Modifier
            .testTag("profile-loading")
            .semantics {
                contentDescription = "Loading profile"
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Box(Modifier.size(88.dp).background(block, CircleShape))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.width(200.dp).height(24.dp).background(block))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(120.dp).height(14.dp).background(block))
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(46.dp).background(block))
    }
}

@Composable
private fun ProfileError() {
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FactoryYellow)
            .background(FactoryWarnContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = FactoryYellow,
            modifier = Modifier.padding(top = 1.dp).size(18.dp)
        )
        Column {
            Text(text = "PROFILE_UNAVAILABLE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FactoryTextWhite)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "GitHub did not return your profile, and no saved copy exists yet. Pull down to retry.",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = FactoryTextWhite
            )
        }
    }
}

/** "2 hours ago", in the phone's language. */
private fun savedAgo(savedAt: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(savedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()

/** GitHub keeps the blog as typed; one without a scheme opens as https. */
internal fun blogUrl(blog: String): String =
    if (blog.startsWith("http://", ignoreCase = true) || blog.startsWith("https://", ignoreCase = true)) blog else "https://$blog"

/**
 * A count as the profile shows it: grouped below 100,000 (`1,284`, `48,912`), compact above
 * (`123k`, `1.3M`). `CompactDecimalFormat` needs API 24, and minSdk is 23.
 */
fun formatCompact(n: Int, locale: Locale = Locale.getDefault()): String {
    if (n < 100_000) return NumberFormat.getIntegerInstance(locale).format(n)
    // From 999,500 the thousands would round to "1,000k"; that is "1M".
    val (value, unit) = if (n >= 999_500) n / 1_000_000.0 to "M" else n / 1_000.0 to "k"
    val digits = if (value < 10) 1 else 0
    val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = digits
        minimumFractionDigits = 0
    }
    return format.format(value) + unit
}
