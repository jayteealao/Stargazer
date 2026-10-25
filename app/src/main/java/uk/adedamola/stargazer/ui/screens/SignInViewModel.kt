package uk.adedamola.stargazer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.auth.DeviceFlowSignIn
import uk.adedamola.stargazer.data.auth.DeviceFlowStep
import uk.adedamola.stargazer.data.auth.LocalDataCounts
import uk.adedamola.stargazer.data.auth.PendingSignIn
import uk.adedamola.stargazer.data.auth.SignInCompleter
import uk.adedamola.stargazer.data.auth.SignInOutcome
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark

/** The states of the sign-in screen, one per board. */
sealed interface SignInPhase {
    data object Idle : SignInPhase
    data object Requesting : SignInPhase
    data object Waiting : SignInPhase
    data object Completing : SignInPhase
    data object Expired : SignInPhase
    data object Denied : SignInPhase
    data object Offline : SignInPhase
    data class GitHubError(val label: String) : SignInPhase
    data object NoBrowser : SignInPhase
}

/** A question the person must answer before an approved sign-in finishes. */
sealed interface AccountDecision {
    /** A different GitHub account signed in: remove [old]'s data and continue, or cancel. */
    data class ChangeAccount(val old: AccountSummary, val new: AccountSummary) : AccountDecision

    /** Nobody knows whose data is on the phone: keep it for [account], or remove it. */
    data class KeepData(val account: AccountSummary, val counts: LocalDataCounts) : AccountDecision
}

data class SignInUiState(
    val phase: SignInPhase = SignInPhase.Idle,
    val userCode: String? = null,
    val verificationUri: String = DEVICE_VERIFICATION_URI,
    val secondsLeft: Long = 0,
    val accessEnded: Boolean = false,
    val decision: AccountDecision? = null
) {
    /** The primary button does nothing while a request is in flight. */
    val actionEnabled: Boolean
        get() = phase != SignInPhase.Requesting && phase != SignInPhase.Completing
}

const val DEVICE_VERIFICATION_URI = "https://github.com/login/device"

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val engine: DeviceFlowSignIn,
    private val completer: SignInCompleter,
    private val session: AccessSession
) : ViewModel() {

    private val local = MutableStateFlow(SignInUiState())
    private var flowJob: Job? = null
    private var tickJob: Job? = null

    /** The approved key waiting for the answer to [SignInUiState.decision]; memory only. */
    private var pending: PendingSignIn? = null

    val uiState: StateFlow<SignInUiState> =
        combine(local, session.accessEnded) { state, ended -> state.copy(accessEnded = ended) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SignInUiState(accessEnded = session.accessEnded.value))

    /** Starts a new sign-in. Any code shown before is abandoned. */
    fun signIn() {
        flowJob?.cancel()
        tickJob?.cancel()
        pending = null
        session.acknowledgeAccessEnded()
        flowJob = viewModelScope.launch {
            engine.run().collect { step -> onStep(step) }
        }
    }

    fun getNewCode() = signIn()

    /** No app on the phone can open github.com: keep the code on screen; polling goes on. */
    fun onBrowserMissing() {
        local.update { if (it.phase == SignInPhase.Waiting) it.copy(phase = SignInPhase.NoBrowser) else it }
    }

    private suspend fun onStep(step: DeviceFlowStep) {
        when (step) {
            DeviceFlowStep.Requesting -> local.value = SignInUiState(phase = SignInPhase.Requesting)
            is DeviceFlowStep.CodeReady -> {
                local.update {
                    it.copy(
                        phase = SignInPhase.Waiting,
                        userCode = step.userCode,
                        verificationUri = step.verificationUri,
                        secondsLeft = secondsLeft(step.expiresAt)
                    )
                }
                startTicker(step.expiresAt)
            }
            is DeviceFlowStep.Approved -> {
                tickJob?.cancel()
                local.update { it.copy(phase = SignInPhase.Completing) }
                val result = completer.complete(step.key)
                local.value = when (val outcome = result.getOrNull()) {
                    // Home opens because the key is stored; this screen is ready for a later sign-in.
                    SignInOutcome.Done -> SignInUiState()
                    is SignInOutcome.AccountChange -> {
                        pending = outcome.pending
                        local.value.copy(decision = AccountDecision.ChangeAccount(outcome.old, outcome.new))
                    }
                    is SignInOutcome.KeepData -> {
                        pending = outcome.pending
                        local.value.copy(decision = AccountDecision.KeepData(outcome.new, outcome.counts))
                    }
                    null -> local.value.copy(phase = failurePhase(result.exceptionOrNull()))
                }
            }
            DeviceFlowStep.Expired -> end(SignInPhase.Expired)
            DeviceFlowStep.Denied -> end(SignInPhase.Denied)
            DeviceFlowStep.Offline -> end(SignInPhase.Offline)
            is DeviceFlowStep.GitHubError -> end(SignInPhase.GitHubError(step.label))
        }
    }

    /** Continue on the account-change dialog: the old account's data is removed, then Home opens. */
    fun onContinue() = decide { completer.confirmAccountChange(it) }

    /** Keep on the keep-data dialog: the data stays and belongs to the new account. */
    fun onKeep() = decide { completer.keepData(it) }

    /** Remove on the keep-data dialog: the data is removed, then Home opens. */
    fun onRemove() = decide { completer.removeData(it) }

    /** Cancel or back on either dialog: the new key is forgotten, the data stays, sign-in starts over. */
    fun onCancel() {
        pending = null
        local.value = SignInUiState()
    }

    private fun decide(action: suspend (PendingSignIn) -> Result<Unit>) {
        val held = pending ?: return
        pending = null
        local.update { it.copy(decision = null) }
        viewModelScope.launch {
            val result = action(held)
            local.value = if (result.isSuccess) SignInUiState()
            else local.value.copy(phase = failurePhase(result.exceptionOrNull()))
        }
    }

    override fun onCleared() {
        pending = null
    }

    private fun end(phase: SignInPhase) {
        tickJob?.cancel()
        local.update { it.copy(phase = phase) }
    }

    private fun startTicker(expiresAt: TimeMark) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val left = -expiresAt.elapsedNow()
                local.update { it.copy(secondsLeft = secondsLeft(expiresAt)) }
                if (left <= Duration.ZERO) break
                // Wake on the next whole second, so the countdown never drifts.
                val toNextSecond = left - left.inWholeSeconds.seconds
                delay(if (toNextSecond > Duration.ZERO) toNextSecond else 1.seconds)
            }
        }
    }

    /** Whole seconds left, rounded up: a fresh 900-second code shows 15:00. */
    private fun secondsLeft(expiresAt: TimeMark): Long =
        ((-expiresAt.elapsedNow()).inWholeMilliseconds + 999).floorDiv(1000).coerceAtLeast(0)

    private fun failurePhase(e: Throwable?): SignInPhase = when (e) {
        is IOException -> SignInPhase.Offline
        is HttpException -> SignInPhase.GitHubError(e.code().toString())
        else -> SignInPhase.GitHubError("unreadable_response")
    }
}
