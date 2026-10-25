package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.adedamola.stargazer.data.remote.GitHubErrorText
import uk.adedamola.stargazer.data.repository.StarMessage
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite
import uk.adedamola.stargazer.ui.theme.FactoryWarnContainer
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import java.util.TimeZone

private val SnackGround = Color(0xFF333333)

/** What the app snackbar shows: the dark Undo style, or the yellow failure style. */
data class StarSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val isFailure: Boolean,
    override val duration: SnackbarDuration,
    override val withDismissAction: Boolean = false,
) : SnackbarVisuals

/** The Undo offer of an unstar; it stays until the Undo window ends. */
fun undoVisuals(fullName: String) =
    StarSnackbarVisuals("UNSTARRED $fullName", "UNDO", isFailure = false, duration = SnackbarDuration.Indefinite)

/** A star or unstar result in the failure style, with RETRY when trying again can help. */
fun failureVisuals(message: StarMessage, zone: TimeZone = TimeZone.getDefault()) = StarSnackbarVisuals(
    message = snackbarText(message, zone),
    actionLabel = if (message is StarMessage.UnstarFailed || message is StarMessage.StarFailed) "RETRY" else null,
    isFailure = true,
    duration = SnackbarDuration.Short,
)

/** The snackbar line for [message]. */
fun snackbarText(message: StarMessage, zone: TimeZone = TimeZone.getDefault()): String = when (message) {
    is StarMessage.UnstarFailed -> "UNSTAR_FAILED — ${message.ref.fullName} is back with its tags."
    is StarMessage.RestarFailed -> "RESTAR_FAILED — ${message.ref.fullName} stays unstarred."
    is StarMessage.StarFailed -> "STAR_FAILED — GitHub did not confirm ${message.ref.fullName}."
    else -> statusText(message, zone)
}

/** The inline status line under a star control for [message]. */
fun statusText(message: StarMessage, zone: TimeZone = TimeZone.getDefault()): String = when (message) {
    is StarMessage.UnstarFailed -> "UNSTAR_FAILED — GitHub did not confirm. The repo stays starred."
    is StarMessage.RestarFailed -> "RESTAR_FAILED — ${message.ref.fullName} stays unstarred."
    is StarMessage.StarFailed -> "STAR_FAILED — GitHub did not confirm."
    is StarMessage.ConnectionNeeded -> "CONNECTION_NEEDED — starring needs the internet."
    is StarMessage.RateLimited -> "RATE_LIMITED — GitHub accepts more changes at ${GitHubErrorText.clockText(message.at, zone)}."
}

/** The app snackbar: square, bordered, no elevation, above the navigation bar (Scaffold insets). */
@Composable
fun StarSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data -> StarSnackbar(data) }
}

@Composable
private fun StarSnackbar(data: SnackbarData) {
    val failure = (data.visuals as? StarSnackbarVisuals)?.isFailure == true
    val accent = if (failure) FactoryYellow else FactoryOrange
    Row(
        modifier = Modifier
            .padding(16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (failure) FactoryWarnContainer else SnackGround, RectangleShape)
            .border(1.dp, accent, RectangleShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (failure) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = FactoryYellow, modifier = Modifier.size(18.dp))
        }
        Text(
            text = data.visuals.message,
            color = FactoryTextWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        data.visuals.actionLabel?.let { action ->
            Text(
                text = action,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable { data.performAction() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** The status line under a star control: a yellow glyph and the terminal-voice text. */
@Composable
fun StarStatusLine(message: StarMessage, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val icon: ImageVector = when (message) {
        is StarMessage.ConnectionNeeded -> Icons.Filled.CloudOff
        is StarMessage.RateLimited -> Icons.Filled.Schedule
        else -> Icons.Filled.ErrorOutline
    }
    Row(
        modifier = modifier
            .padding(top = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = FactoryYellow, modifier = Modifier.size(14.dp))
        Text(
            text = statusText(message),
            color = FactoryTextWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onRetry != null) {
            Text(
                text = "[ RETRY ]",
                color = FactoryCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onRetry),
            )
        }
    }
}
