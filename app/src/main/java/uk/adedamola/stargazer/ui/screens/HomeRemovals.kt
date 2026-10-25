package uk.adedamola.stargazer.ui.screens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.repository.Outcome
import uk.adedamola.stargazer.data.repository.Prepared
import uk.adedamola.stargazer.data.repository.StarReconciler
import javax.inject.Inject

/**
 * Home's reconcile on pull-to-refresh and the one-time removal notice. App start and background
 * sync never remove. A failed read removes nothing and says nothing: the paging refresh shows its
 * own error, and the next pull tries again.
 */
class HomeRemovals(private val scope: CoroutineScope, private val reconciler: StarReconciler) {

    private val _reconciling = MutableStateFlow(false)

    /** True while a pull-to-refresh reads GitHub's whole starred list. */
    val reconciling: StateFlow<Boolean> = _reconciling.asStateFlow()

    private val _notice = MutableStateFlow<Int?>(null)

    /** How many repos the first reconcile after a sign-in would remove; null when nothing waits. */
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    /** The read the notice is about, removed on Continue. */
    private var held: Prepared.Ready? = null

    /** Removes the repos GitHub no longer lists as starred, or holds them for the notice. */
    fun reconcile() {
        if (_notice.value != null) return
        _reconciling.value = true
        scope.launch {
            try {
                val outcome = reconciler.reconcile()
                if (outcome is Outcome.NoticeNeeded) {
                    held = outcome.prepared
                    _notice.value = outcome.count
                }
            } finally {
                _reconciling.value = false
            }
        }
    }

    /** Continue on the notice: the repos it counted leave, with their local data. */
    fun confirm() {
        val prepared = held ?: return
        held = null
        _notice.value = null
        _reconciling.value = true
        scope.launch {
            try {
                reconciler.apply(prepared)
            } finally {
                _reconciling.value = false
            }
        }
    }

    /** Another account signed in: a notice about the old account's repos no longer applies. */
    fun drop() {
        held = null
        _notice.value = null
    }

    class Factory @Inject constructor(private val reconciler: StarReconciler) {
        fun create(scope: CoroutineScope) = HomeRemovals(scope, reconciler)
    }
}
