package uk.adedamola.stargazer.ui.screens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.Attempt
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.StarMessage
import uk.adedamola.stargazer.data.repository.UndoOffer
import uk.adedamola.stargazer.data.repository.toRef
import javax.inject.Inject

/**
 * Home's star and unstar actions: the swipe on a row, the star control of a GitHub result, Undo
 * and Retry. A tap that sends nothing (offline, rate limited) is reported as a message, because
 * Home has no status line under its controls.
 */
class HomeStarActions(
    private val scope: CoroutineScope,
    private val starEngine: StarEngine,
    private val search: HomeGitHubSearch,
) {

    /** Repositories hidden from Home while their unstar is pending. */
    val hiddenIds: Flow<Set<Int>> = starEngine.hiddenIds

    /** The latest unstar's Undo offer. */
    val undoOffer: StateFlow<UndoOffer?> = starEngine.undoOffer

    private val refusals = MutableSharedFlow<StarMessage>(extraBufferCapacity = 8)

    /** Results the person is told about: GitHub's answers and taps refused here. */
    val messages: Flow<StarMessage> = merge(starEngine.messages, refusals)

    /** Unstars a Home row (the swipe or its accessibility action). Refused: the row stays. */
    suspend fun unstarFromSwipe(repo: GitHubRepository): Attempt =
        starEngine.unstar(repo.toRef()).also(::reportRefusal)

    /** Stars or unstars a GitHub search result. */
    fun toggleSearchStar(repo: GitHubRepository) {
        scope.launch {
            val ref = repo.toRef()
            reportRefusal(if (search.isStarred(repo.id)) starEngine.unstar(ref) else starEngine.star(ref, repo))
        }
    }

    fun undo(repoId: Int) {
        scope.launch { reportRefusal(starEngine.undo(repoId)) }
    }

    /** Tries a failed star or unstar again. */
    fun retry(message: StarMessage) {
        scope.launch {
            when (message) {
                is StarMessage.UnstarFailed -> reportRefusal(starEngine.unstar(message.ref))
                // When the result has left the screen, the engine reads the repository from GitHub.
                is StarMessage.StarFailed -> reportRefusal(starEngine.star(message.ref, search.resultFor(message.ref.id)))
                else -> Unit
            }
        }
    }

    /** Settles unstars left open by a process that was killed during the Undo window. */
    fun resumePending() {
        scope.launch { starEngine.resumePending() }
    }

    private fun reportRefusal(attempt: Attempt) {
        when (attempt) {
            Attempt.Offline -> refusals.tryEmit(StarMessage.ConnectionNeeded(null))
            is Attempt.RateLimited -> refusals.tryEmit(StarMessage.RateLimited(null, attempt.until))
            else -> Unit
        }
    }

    class Factory @Inject constructor(private val starEngine: StarEngine) {
        fun create(scope: CoroutineScope, search: HomeGitHubSearch) = HomeStarActions(scope, starEngine, search)
    }
}
