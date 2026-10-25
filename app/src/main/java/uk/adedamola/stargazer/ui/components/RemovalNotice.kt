package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uk.adedamola.stargazer.R
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactorySurfaceGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import uk.adedamola.stargazer.ui.theme.safeBars
import java.text.NumberFormat

/** Test tag of the removal notice's panel. */
const val REMOVAL_NOTICE_TAG = "removal-notice"

/**
 * Shown before a reconcile removes [count] repos (the first one after a sign-in, or one whose
 * answer looks wrong): a bottom panel over a dim scrim that the person dismisses only with the
 * button that names the removal. Back and taps outside do nothing,
 * so nothing leaves before the person has read the count. The panel's ground reaches the bottom
 * edge; its content stays clear of the navigation bar and the cutout.
 */
@Composable
fun RemovalNotice(count: Int, onContinue: () -> Unit) {
    val shown = NumberFormat.getIntegerInstance().format(count)
    val spoken = pluralStringResource(R.plurals.removal_notice_count, count, NumberFormat.getIntegerInstance().format(count))
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FactoryDarkGrey.copy(alpha = 0.6f))
                .windowInsetsPadding(WindowInsets.safeBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag(REMOVAL_NOTICE_TAG)
                    .semantics { paneTitle = TITLE }
                    .background(FactorySurfaceGrey)
                    .drawBehind {
                        drawLine(FactoryYellow, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                    }
                    .windowInsetsPadding(WindowInsets.safeBars.only(WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp)
            ) {
                DialogTitle(icon = Icons.Filled.Warning, text = TITLE)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = shown,
                    style = CountStyle,
                    color = FactoryYellow,
                    modifier = Modifier.semantics { contentDescription = spoken }
                )
                Spacer(Modifier.height(12.dp))
                Text(text = BODY, style = BodyStyle, color = FactoryTextWhite)
                Spacer(Modifier.height(18.dp))
                StargazerButton(text = "REMOVE $shown & CONTINUE", onClick = onContinue)
            }
        }
    }
}

private const val TITLE = "REPOS TO REMOVE"

private const val BODY = "These repos are no longer starred on your GitHub account, or are private and cannot be " +
    "read with your GitHub sign-in. They will be removed from this phone with their favorites, pins and tags. " +
    "This cannot be undone. Your stars on GitHub do not change."

private val CountStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 48.sp)
private val BodyStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp)
