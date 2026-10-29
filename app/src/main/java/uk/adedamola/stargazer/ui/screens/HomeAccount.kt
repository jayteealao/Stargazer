package uk.adedamola.stargazer.ui.screens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.data.repository.RefreshStatus
import uk.adedamola.stargazer.data.repository.StarListRepository
import javax.inject.Inject

/** Home's signed-in account: the drawer header, the GitHub star lists and their refresh. */
class HomeAccount(
    private val scope: CoroutineScope,
    accounts: AccountRepository,
    starListRepository: StarListRepository,
    private val refresher: AccountDataRefresher,
) {

    /**
     * The signed-in account for the drawer header: the cached profile, else the account record
     * (login and avatar), so the header shows right after sign-in.
     */
    val account: StateFlow<AccountSummary?> = accounts.observeAccount()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The account's GitHub star lists, as last cached (read-only). */
    val starLists: StateFlow<List<StarListEntity>> = starListRepository.observeLists()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val listsStatus: StateFlow<RefreshStatus> = refresher.listsStatus

    /** Emits when another account's data replaces the old one (not for the account at start). */
    val accountChanges: Flow<Long?> = accounts.observeUserId().drop(1)

    /** Refreshes the profile and the star lists on their own, so the starred list never waits. */
    fun refresh() {
        scope.launch { refresher.refresh() }
    }

    class Factory @Inject constructor(
        private val accounts: AccountRepository,
        private val starListRepository: StarListRepository,
        private val refresher: AccountDataRefresher,
    ) {
        fun create(scope: CoroutineScope) = HomeAccount(scope, accounts, starListRepository, refresher)
    }
}
