package uk.adedamola.stargazer.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.adedamola.stargazer.ui.components.AccountChangeDialog
import uk.adedamola.stargazer.ui.components.KeepDataDialog
import uk.adedamola.stargazer.ui.components.StargazerButton
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactorySurfaceGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite
import uk.adedamola.stargazer.ui.theme.FactoryWarnContainer
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import uk.adedamola.stargazer.ui.theme.safeBars

/** What GitHub's sign-in asks of the person, said on the idle screen. */
private const val PERMISSION_LINE = "Stargazer asks only for Starring (read and write). No access to your code."

/** The access-ended banner as TalkBack reads it. */
const val ACCESS_ENDED_DESCRIPTION = "GitHub access ended — sign in again"

/**
 * The sign-in screen with its view model, the clipboard and the browser. [openBrowser] opens
 * GitHub's code page and returns false when no app on the phone can open it.
 */
@Composable
fun SignInRoute(
    viewModel: SignInViewModel = hiltViewModel(),
    openBrowser: (Context, Uri) -> Boolean = ::openDeviceVerificationPage,
    cursorBlinks: Boolean = animationsEnabled(LocalContext.current)
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    SignInScreen(
        state = state,
        onSignIn = viewModel::signIn,
        onCopyAndOpen = {
            state.userCode?.let { copyCode(context, it) }
            if (!openBrowser(context, safeVerificationUri(state.verificationUri))) viewModel.onBrowserMissing()
        },
        onCopyCode = { state.userCode?.let { copyCode(context, it) } },
        cursorBlinks = cursorBlinks
    )
    when (val decision = state.decision) {
        is AccountDecision.ChangeAccount -> AccountChangeDialog(
            old = decision.old,
            new = decision.new,
            onContinue = viewModel::onContinue,
            onCancel = viewModel::onCancel
        )
        is AccountDecision.KeepData -> KeepDataDialog(
            account = decision.account,
            counts = decision.counts,
            onKeep = viewModel::onKeep,
            onRemove = viewModel::onRemove,
            onDismiss = viewModel::onCancel
        )
        null -> Unit
    }
}

/** Stateless sign-in screen: one prompt line, the code, one hint line and one button. */
@Composable
fun SignInScreen(
    state: SignInUiState,
    onSignIn: () -> Unit,
    onCopyAndOpen: () -> Unit,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier,
    cursorBlinks: Boolean = true
) {
    val phase = state.phase
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(FactoryDarkGrey)
            .windowInsetsPadding(WindowInsets.safeBars)
    ) {
        // Fills the window when it fits; scrolls, button included, when large text or a short
        // landscape window makes it taller.
        val windowHeight = maxHeight
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .heightIn(min = windowHeight)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            if (state.accessEnded && phase == SignInPhase.Idle) {
                AccessEndedBanner()
                Spacer(modifier = Modifier.height(24.dp))
            }

            PromptLine(cursor = cursorFor(phase), blinks = cursorBlinks)
            Spacer(modifier = Modifier.height(20.dp))

            when (phase) {
                SignInPhase.Idle -> IdleBody()
                SignInPhase.Requesting -> RequestingBody()
                SignInPhase.Waiting, SignInPhase.Completing -> {
                    UserCode(code = state.userCode.orEmpty())
                    Spacer(modifier = Modifier.height(12.dp))
                    HintLine(
                        if (phase == SignInPhase.Completing) "linking your account…"
                        else "enter on github.com · ${formatLeft(state.secondsLeft)} left"
                    )
                }
                SignInPhase.Expired -> {
                    UserCode(code = state.userCode.orEmpty(), expired = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    StatusText("CODE_EXPIRED — this code is older than 15 minutes.", FactoryYellow, announce = true)
                }
                SignInPhase.Denied -> ErrorLines(
                    "ACCESS_DENIED — GitHub reports that the request was cancelled.",
                    "Nothing was linked. Start again when you are ready."
                )
                SignInPhase.Offline -> ErrorLines("NO_CONNECTION — connect to the internet to get a code.", null)
                is SignInPhase.GitHubError -> ErrorLines(
                    "GITHUB_ERROR — github.com did not answer. Try again in a minute.",
                    "code ${phase.label}"
                )
                SignInPhase.NoBrowser -> {
                    UserCode(code = state.userCode.orEmpty())
                    Spacer(modifier = Modifier.height(12.dp))
                    HintLine("no browser found on this phone · ${formatLeft(state.secondsLeft)} left")
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                        StatusText("On any device, open", FactoryTextWhite)
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusText(safeVerificationUri(state.verificationUri).toString().removePrefix("https://"), FactoryCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HintLine("and enter the code above.")
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            val (label, action) = when (phase) {
                SignInPhase.Idle, SignInPhase.Denied -> "Sign in with GitHub" to onSignIn
                SignInPhase.Requesting, SignInPhase.Waiting, SignInPhase.Completing ->
                    "Copy code & open GitHub" to onCopyAndOpen
                SignInPhase.Expired -> "Get a new code" to onSignIn
                SignInPhase.Offline, is SignInPhase.GitHubError -> "Try again" to onSignIn
                SignInPhase.NoBrowser -> "Copy code" to onCopyCode
            }
            StargazerButton(text = label, onClick = action, enabled = state.actionEnabled)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private enum class Cursor { Live, Resting, None }

private fun cursorFor(phase: SignInPhase): Cursor = when (phase) {
    SignInPhase.Requesting, SignInPhase.Waiting, SignInPhase.Completing, SignInPhase.NoBrowser -> Cursor.Live
    SignInPhase.Idle -> Cursor.Resting
    else -> Cursor.None
}

/** `$ link github` with a block cursor that blinks only while the app talks to GitHub. */
@Composable
private fun PromptLine(cursor: Cursor, blinks: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = FactoryOrange)) { append("$") }
                append(" link github")
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = FactoryTextWhite,
            modifier = Modifier.semantics { heading() }
        )
        if (cursor != Cursor.None) {
            Spacer(modifier = Modifier.size(width = 4.dp, height = 1.dp))
            val color = if (cursor == Cursor.Live) FactoryOrange else FactoryTextGrey
            val blockModifier = Modifier
                .size(width = 9.dp, height = 17.dp)
                .clearAndSetSemantics { }
            if (cursor == Cursor.Live && blinks) {
                // 1 s on-off steps, read in the draw phase so the screen never recomposes for it.
                val phase by rememberInfiniteTransition(label = "cursor").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
                    label = "cursor-phase"
                )
                Box(blockModifier.graphicsLayer { alpha = if (phase < 0.5f) 1f else 0f }.background(color))
            } else {
                Box(blockModifier.background(color))
            }
        }
    }
}

/** The code at 48sp with a grey hyphen; TalkBack reads it one character at a time. */
@Composable
private fun UserCode(code: String, expired: Boolean = false) {
    val text = remember(code, expired) {
        buildAnnotatedString {
            code.forEach { c ->
                if (c == '-') withStyle(SpanStyle(color = FactoryTextGrey)) { append(c) } else append(c)
            }
        }
    }
    val base = if (expired) FactoryTextGrey.copy(alpha = 0.6f) else FactoryTextWhite
    BasicText(
        text = if (expired) AnnotatedString(code) else text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 48.sp,
            letterSpacing = 6.sp,
            color = base,
            textDecoration = if (expired) TextDecoration.LineThrough else TextDecoration.None
        ),
        maxLines = 1,
        softWrap = false,
        // Stays 48sp when it fits; shrinks instead of clipping at large text sizes.
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 48.sp, stepSize = 2.sp),
        modifier = Modifier.semantics {
            contentDescription = (if (expired) "expired code " else "code ") + code.toList().joinToString(" ")
            // Read out when a new code appears; the countdown below stays silent.
            liveRegion = LiveRegionMode.Polite
        }
    )
}

@Composable
private fun HintLine(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = FactoryTextGrey,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
}

@Composable
private fun StatusText(text: String, color: Color, announce: Boolean = false) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = color,
        modifier = if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Assertive } else Modifier
    )
}

@Composable
private fun IdleBody() {
    Text(
        text = "Link your GitHub account to sync your stars, lists and profile.",
        fontFamily = FontFamily.Monospace,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = FactoryTextWhite
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = PERMISSION_LINE,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = FactoryTextGrey
    )
}

@Composable
private fun RequestingBody() {
    // A still block where the code will appear: no shimmer in a dim room.
    Box(
        modifier = Modifier
            .size(width = 270.dp, height = 44.dp)
            .background(FactorySurfaceGrey)
            .clearAndSetSemantics { }
    )
    Spacer(modifier = Modifier.height(24.dp))
    HintLine("requesting a code from github.com…")
}

/** A yellow SCREAMING_SNAKE status with its icon, then plain words in grey. */
@Composable
private fun ErrorLines(status: String, detail: String?) {
    // One node, read out as soon as it appears: focus stays on the button below.
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = FactoryYellow,
                modifier = Modifier.padding(top = 2.dp).size(18.dp)
            )
            StatusText(status, FactoryYellow)
        }
        if (detail != null) {
            Spacer(modifier = Modifier.height(10.dp))
            HintLine(detail)
        }
    }
}

@Composable
private fun AccessEndedBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FactoryYellow)
            .background(FactoryWarnContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = ACCESS_ENDED_DESCRIPTION },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = FactoryYellow,
            modifier = Modifier.padding(top = 1.dp).size(18.dp)
        )
        Column {
            Text(
                text = "GITHUB_ACCESS_ENDED",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = FactoryTextWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "GitHub no longer accepts this app’s access. Your stars, tags and favorites are still here. Sign in again to continue.",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = FactoryTextWhite
            )
        }
    }
}

private fun formatLeft(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", code))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
    }
}

/**
 * The code page GitHub named, when it is an https page on github.com; GitHub's own code page
 * otherwise, so a changed answer can never send the person elsewhere.
 */
internal fun safeVerificationUri(raw: String): Uri {
    val uri = Uri.parse(raw)
    return if (uri.scheme == "https" && uri.host == "github.com") uri else Uri.parse(DEVICE_VERIFICATION_URI)
}

/**
 * Opens GitHub's code page in an in-app browser tab, else in any app that opens web links.
 * Returns false when nothing on the phone can open it.
 */
fun openDeviceVerificationPage(context: Context, uri: Uri): Boolean = try {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    true
} catch (e: ActivityNotFoundException) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
