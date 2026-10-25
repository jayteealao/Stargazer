package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import uk.adedamola.stargazer.R
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.auth.LocalDataCounts
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactorySurfaceGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import uk.adedamola.stargazer.ui.theme.safeBars
import java.text.NumberFormat

/** Test tag of each dialog's card, for the safe-area check. */
const val ACCOUNT_CHANGE_DIALOG_TAG = "account-change-dialog"
const val KEEP_DATA_DIALOG_TAG = "keep-data-dialog"

/**
 * Asked when a different GitHub account signs in: its data replaces the recorded account's.
 * Cancel (and back) keeps the data and abandons the sign-in.
 */
@Composable
fun AccountChangeDialog(
    old: AccountSummary,
    new: AccountSummary,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    AccountDialogWindow(title = "CHANGE ACCOUNT", tag = ACCOUNT_CHANGE_DIALOG_TAG, onDismiss = onCancel) {
        DialogTitle(icon = Icons.Filled.Warning, text = "CHANGE ACCOUNT")
        Spacer(Modifier.height(14.dp))
        Text(
            text = buildAnnotatedString {
                append("Signed in as ")
                bold("@${new.login}")
                append(". Data from ")
                bold("@${old.login}")
                append(" will be removed from this phone:")
            },
            style = BodyStyle,
            color = FactoryTextWhite
        )
        Spacer(Modifier.height(12.dp))
        WIPE_LINES.forEach { line ->
            Text(text = "– $line", style = ListStyle, color = FactoryTextGrey)
        }
        Spacer(Modifier.height(16.dp))
        PairLabel("FROM")
        Spacer(Modifier.height(10.dp))
        AccountRow(old)
        Spacer(Modifier.height(10.dp))
        PairLabel("TO")
        Spacer(Modifier.height(10.dp))
        AccountRow(new)
        DialogActions(
            secondary = "CANCEL",
            onSecondary = onCancel,
            primary = "REMOVE & CONTINUE",
            onPrimary = onContinue
        )
    }
}

/**
 * Asked at the first sign-in after an update whose old token no longer worked: nobody knows
 * whose data is on the phone. Back dismisses like Cancel: the sign-in is abandoned, the data stays.
 */
@Composable
fun KeepDataDialog(
    account: AccountSummary,
    counts: LocalDataCounts,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val format = NumberFormat.getIntegerInstance()
    val repos = pluralStringResource(R.plurals.keep_data_repos, counts.repos, format.format(counts.repos))
    val tags = pluralStringResource(R.plurals.keep_data_tags, counts.tags, format.format(counts.tags))
    val presets = pluralStringResource(R.plurals.keep_data_presets, counts.presets, format.format(counts.presets))
    AccountDialogWindow(title = "KEEP LOCAL DATA?", tag = KEEP_DATA_DIALOG_TAG, onDismiss = onDismiss) {
        DialogTitle(icon = Icons.Outlined.Info, text = "KEEP LOCAL DATA?")
        Spacer(Modifier.height(14.dp))
        Text(
            text = buildAnnotatedString {
                append("Data from an earlier sign-in is on this phone: ")
                bold(repos)
                append(", $tags, $presets. Stargazer cannot tell which GitHub account it came from.")
            },
            style = BodyStyle,
            color = FactoryTextWhite
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(account)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        append("Keep it for ")
                        bold("@${account.login}")
                        append("?")
                    },
                    style = RowStyle,
                    color = FactoryTextWhite
                )
                Text(text = "Remove deletes it from this phone.", style = RowStyle, color = FactoryTextGrey)
            }
        }
        DialogActions(secondary = "REMOVE", onSecondary = onRemove, primary = "KEEP", onPrimary = onKeep)
    }
}

/** Avatar, then the name and the @login, each on one line. Only the login when there is no name. */
@Composable
fun AccountRow(summary: AccountSummary, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AccountAvatar(summary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            summary.name?.let {
                Text(
                    text = it,
                    style = RowStyle.copy(fontWeight = FontWeight.Bold),
                    color = FactoryTextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "@${summary.login}",
                style = RowStyle,
                color = FactoryTextGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A circle with the account's initials, tinted by user id (grey when the name is missing), with
 * the GitHub avatar drawn over it once it loads. The row's text names the account.
 */
@Composable
fun AccountAvatar(summary: AccountSummary, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val tint = if (summary.name == null) NoNameTint else AvatarTints[Math.floorMod(summary.userId, AvatarTints.size.toLong()).toInt()]
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(tint),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(summary),
            // 11sp at the dialogs' 32dp, scaled with the circle for the header and the profile.
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = (size.value * 11f / 32f).sp),
            color = FactoryDarkGrey
        )
        if (!summary.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = summary.avatarUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(CircleShape)
            )
        }
    }
}

/**
 * The shared window of both dialogs: drawn edge to edge over a dark scrim, the card kept inside
 * the system bars and the cutout, at most 600dp wide. Tapping outside does nothing; back cancels.
 */
@Composable
private fun AccountDialogWindow(
    title: String,
    tag: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FactoryDarkGrey.copy(alpha = 0.6f))
                .windowInsetsPadding(WindowInsets.safeBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag(tag)
                    .semantics { paneTitle = title },
                shape = RectangleShape,
                color = FactorySurfaceGrey,
                border = BorderStroke(1.dp, FactoryYellow),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DialogTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = FactoryYellow, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.5.sp, lineHeight = 22.sp),
            color = FactoryTextWhite
        )
    }
}

@Composable
private fun PairLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = FactoryTextGrey)
}

@Composable
private fun DialogActions(secondary: String, onSecondary: () -> Unit, primary: String, onPrimary: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onSecondary,
            shape = RectangleShape,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text(text = secondary, style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp), color = FactoryCyan)
        }
        StargazerButton(
            text = primary,
            onClick = onPrimary,
            modifier = Modifier.width(IntrinsicSize.Max).minimumInteractiveComponentSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

private fun AnnotatedString.Builder.bold(text: String) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(text) }
}

/** First letters of up to two words of the name; the login's first letter when there is no name. */
internal fun initials(summary: AccountSummary): String {
    val words = summary.name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    val letters = if (words.isEmpty()) {
        summary.login.take(1)
    } else {
        words.take(2).joinToString("") { word -> String(Character.toChars(word.codePointAt(0))) }
    }
    return letters.uppercase()
}

/** What Continue removes; the same set [uk.adedamola.stargazer.data.auth.AccountDataWipe] clears. */
private val WIPE_LINES = listOf(
    "starred repos cache",
    "favorites, pins and tags",
    "saved search presets",
    "profile and star lists"
)

private val BodyStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp)
private val ListStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)
private val RowStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)

/** Light tints of the palette's orange, cyan and yellow, as on the boards. */
private val AvatarTints = listOf(Color(0xFFFFB380), Color(0xFF80E5FF), Color(0xFFFFE680))
private val NoNameTint = Color(0xFFCCCCCC)
