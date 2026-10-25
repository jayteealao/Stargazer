package uk.adedamola.stargazer.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.repository.RefreshStatus
import uk.adedamola.stargazer.data.repository.SortOption
import uk.adedamola.stargazer.ui.components.FilterDrawerContent
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.safeBars

/**
 * The drawer's account header and star lists as drawn on the boards: texts, actions, the
 * truncation of worst-case content, and no touch target under the bars or the cutout.
 */
class AccountDrawerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val check = ScreenCheck(composeRule, "profile-lists")

    private val ada = AccountSummary(FIXTURE_USER_ID, "ada-builds", "Ada Okafor", null)
    private val boardLists = listOf(
        "Android libraries" to 48, "Rust tools" to 31, "Read later" to 112, "Design systems" to 9, "Compose samples" to 22
    ).mapIndexed { i, (name, count) -> StarListEntity("L$i", name, null, false, count, i, 1L) }
    private val longName = "Benachrichtigungseinstellungen and other very long list names"
    private val worstLists = (0 until 30).map { i ->
        val name = when (i) { 0 -> longName; 1 -> "Read later"; else -> "List %02d".format(i + 1) }
        StarListEntity("W$i", name, null, false, when (i) { 0 -> 999; 1 -> 0; else -> 100 + i }, i, 1L)
    }

    private var profileClicks = 0
    private var cleared = 0
    private var retries = 0
    private val selections = mutableListOf<String?>()

    private fun drawer(
        account: AccountSummary? = ada,
        lists: List<StarListEntity> = boardLists,
        selected: String? = null,
        status: RefreshStatus = RefreshStatus.Succeeded,
    ) = check.render { Drawer(account, lists, selected, status) }

    @Composable
    private fun Drawer(account: AccountSummary?, lists: List<StarListEntity>, selected: String?, status: RefreshStatus) {
        ModalDrawerSheet(
            drawerContainerColor = FactoryDarkGrey,
            windowInsets = WindowInsets.safeBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
        ) {
            FilterDrawerContent(
                sortOption = SortOption.STARRED,
                showFavoritesOnly = false,
                showPinnedOnly = false,
                selectedTag = null,
                availableTags = emptyList(),
                selectedLanguage = null,
                availableLanguages = emptyList(),
                minStars = null,
                maxStars = null,
                savedPresets = emptyList(),
                matchingReposCount = 48,
                onSortChange = {},
                onFavoritesToggle = {},
                onPinnedToggle = {},
                onTagSelect = {},
                onLanguageSelect = {},
                onStarRangeChange = { _, _ -> },
                onSavePreset = {},
                onLoadPreset = {},
                onDeletePreset = {},
                onClearFilters = { cleared++ },
                onApplyFilters = {},
                onLogout = {},
                account = account,
                starLists = lists,
                selectedListId = selected,
                listsStatus = status,
                onListSelect = { selections += it },
                onListsRetry = { retries++ },
                onProfileClick = { profileClicks++ },
            )
        }
    }

    private fun assertOneLineEllipsized(text: String) {
        val layout = check.layout(text)
        assertEquals("$text lines", 1, layout.lineCount)
        assertTrue("$text is cut with an ellipsis", layout.isLineEllipsized(0))
    }

    @Test
    fun headerDefault() {
        drawer()
        check.assertShown("Ada Okafor", "@ada-builds")
        composeRule.onNodeWithTag("drawer-account-header").performClick()
        assertEquals(1, profileClicks)
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-account-header--default")
    }

    @Test
    fun headerNoName() {
        drawer(account = ada.copy(name = null))
        check.assertShown("@ada-builds", "GitHub account")
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-account-header--no-name")
    }

    @Test
    fun headerWorst() {
        val name = "Aleksandra Wiśniewska-Kowalczyk"
        val login = "@aleksandra-wisniewska-kowalczyk-dev-01"
        drawer(account = AccountSummary(FIXTURE_USER_ID, login.drop(1), name, null))
        assertOneLineEllipsized(name)
        assertOneLineEllipsized(login)
        val header = composeRule.onNodeWithTag("drawer-account-header").fetchSemanticsNode().boundsInWindow
        listOf(name, login).forEach { text ->
            val b = check.textNode(text).boundsInWindow
            assertTrue("$text ends before the chevron", b.right <= header.right - 40f)
        }
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-account-header--worst")
    }

    @Test
    fun listsDefault() {
        drawer(selected = "L0")
        check.assertShown("ALL_LISTS", "ANDROID LIBRARIES", "48", "RUST TOOLS", "31", "READ LATER", "112", "DESIGN SYSTEMS", "9", "COMPOSE SAMPLES", "22")
        check.assertShown("LIST: ANDROID LIBRARIES", "MATCHES:", "⭐ STAR_LISTS")
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-star-lists--default")

        composeRule.onNodeWithText("RUST TOOLS").performClick()
        composeRule.onNodeWithText("ALL_LISTS").performClick()
        assertEquals(listOf("L1", null), selections)
        composeRule.onNodeWithText("[ CLEAR_ALL ]").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun listsLoading() {
        drawer(lists = emptyList(), status = RefreshStatus.Refreshing)
        composeRule.onNodeWithTag("star-lists-loading").fetchSemanticsNode()
        check.assertAbsent("NO_LISTS_YET", "LISTS_UNAVAILABLE")
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-star-lists--loading")
    }

    @Test
    fun listsEmpty() {
        drawer(lists = emptyList(), status = RefreshStatus.Succeeded)
        check.assertShown("NO_LISTS_YET", "Group stars into lists on github.com. They appear here after the next refresh.")
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-star-lists--empty")
    }

    @Test
    fun listsError() {
        drawer(lists = emptyList(), status = RefreshStatus.Failed(offline = false, at = 1L))
        check.assertShown("LISTS_UNAVAILABLE", "GitHub did not return your lists. Your stars still work.", "Ada Okafor")
        composeRule.onNodeWithText("[ RETRY ]").performClick()
        assertEquals(1, retries)
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-star-lists--error")
    }

    @Test
    fun listsWorst() {
        drawer(lists = worstLists, selected = "W1")
        val upper = longName.uppercase()
        assertOneLineEllipsized(upper)
        check.assertShown("999", "0", "LIST: READ LATER")
        val name = check.textNode(upper).boundsInWindow
        val count = check.textNode("999").boundsInWindow
        assertTrue("the long name ends before its count", name.right <= count.left)
        check.assertTargetsInsideSafeArea()
        check.capture("drawer-star-lists--worst")
    }

    @Test
    fun drawerNodes() {
        drawer()
        check.assertShown("STARGAZER_SYSTEM", "Ada Okafor", "[ SIGN_OUT ]")
        check.assertAbsent("UPDATE_GITHUB_TOKEN")
    }
}
