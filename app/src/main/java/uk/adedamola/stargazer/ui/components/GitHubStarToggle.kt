package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.adedamola.stargazer.data.repository.StarControlState
import uk.adedamola.stargazer.data.repository.StarMessage
import uk.adedamola.stargazer.ui.theme.FactoryDisabledBorder
import uk.adedamola.stargazer.ui.theme.FactoryDisabledText
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryRestOutline
import uk.adedamola.stargazer.ui.theme.FactoryTextGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite

/**
 * The GitHub star control: a square, labelled toggle, so it never reads as the yellow favorite
 * star of the cards. `STARRED` in orange, `STAR` in white, `SAVING…` with a dashed border while
 * the request runs, dim when offline or rate limited. No bounce: a frequent action.
 */
@Composable
fun GitHubStarToggle(
    state: StarControlState,
    fullName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val disabled = !state.inFlight && !state.enabled
    val label = when {
        state.inFlight -> "SAVING…"
        state.starred -> "STARRED"
        else -> "STAR"
    }
    val icon: ImageVector = when {
        state.inFlight -> Icons.Filled.Schedule
        disabled && state.status is StarMessage.RateLimited -> Icons.Filled.Schedule
        disabled -> Icons.Filled.CloudOff
        state.starred -> Icons.Filled.Star
        else -> Icons.Filled.StarBorder
    }
    val content = when {
        disabled -> FactoryDisabledText
        state.inFlight -> FactoryTextGrey
        state.starred -> FactoryOrange
        else -> FactoryTextWhite
    }
    val border = when {
        disabled -> FactoryDisabledBorder
        state.starred && !state.inFlight -> FactoryOrange
        else -> FactoryRestOutline
    }
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            // A switch named after its visible word: "Star owner/repo", on or off. Only the
            // in-flight state needs words of its own.
            .semantics(mergeDescendants = true) {
                contentDescription = "Star $fullName"
                if (state.inFlight) {
                    stateDescription = "Saving"
                } else if (disabled) {
                    // The glyph and dimming alone do not say why the switch cannot act.
                    stateDescription = state.status
                        ?.takeIf { it is StarMessage.RateLimited || it is StarMessage.ConnectionNeeded }
                        ?.let { statusText(it) }
                        ?: "Unavailable"
                }
            }
            .toggleable(
                value = state.starred,
                enabled = state.enabled && !state.inFlight,
                role = Role.Switch,
                onValueChange = { onClick() }
            )
            .height(40.dp)
            .then(
                if (state.inFlight) {
                    Modifier.drawBehind {
                        val stroke = 1.dp.toPx()
                        drawRect(
                            color = border,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                        )
                    }
                } else {
                    Modifier.border(1.dp, border, RectangleShape)
                }
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            color = content,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}
