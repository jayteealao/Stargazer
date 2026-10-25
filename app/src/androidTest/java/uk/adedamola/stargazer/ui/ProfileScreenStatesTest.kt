package uk.adedamola.stargazer.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.local.database.GitHubProfileEntity
import uk.adedamola.stargazer.ui.screens.ProfileScreen
import uk.adedamola.stargazer.ui.screens.ProfileUiState

/**
 * Each profile state as drawn on the boards: every field, the offline line, loading and error,
 * worst-case wrapping and truncation, and no touch target under the bars or the cutout.
 */
class ProfileScreenStatesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val check = ScreenCheck(composeRule, "profile-lists")
    private val now = 1_761_000_000_000L
    private val twoHours = 2 * 60 * 60 * 1000L
    private val opened = mutableListOf<String>()
    private var backs = 0

    private val ada = GitHubProfileEntity(
        userId = FIXTURE_USER_ID, login = "ada-builds", name = "Ada Okafor", avatarUrl = "",
        bio = "Android engineer. I collect libraries, then forget them. Stargazer helps me remember.",
        followers = 1284, following = 211, publicRepos = 64, starredCount = 1307,
        location = "Lagos, Nigeria", company = "Fieldwork Labs", blog = "ada.example.com",
        htmlUrl = "https://github.com/ada-builds", fetchedAt = now - twoHours
    )
    private val worstBio = ("Staff engineer · Kotlin, Rust, Compose · Benachrichtigungseinstellungen-Refactoring · " +
        "speaker · maintainer of 40+ libraries · opinions are mine · https://very-long.example").take(160)
    private val worst = ada.copy(
        login = "aleksandra-wisniewska-kowalczyk-dev-01", name = "Aleksandra Wiśniewska-Kowalczyk",
        bio = worstBio, followers = 1_300_000, following = 0, publicRepos = 1000, starredCount = 48_912,
        location = "Szczebrzeszyn, Lubelskie Voivodeship, Poland",
        company = "@an-organization-with-a-long-handle and @second-org",
        blog = "https://very-long-personal-domain.example.org/about/me"
    )

    private fun loaded(profile: GitHubProfileEntity, offline: Boolean = false) =
        ProfileUiState.Loaded(profile, profile.starredCount ?: 0, offline, refreshing = false)

    private fun show(state: ProfileUiState) = check.render {
        ProfileScreen(state = state, onBack = { backs++ }, onOpenLink = { opened += it }, onRefresh = {}, now = { now })
    }

    @Test
    fun default() {
        show(loaded(ada))
        check.assertShown(
            "PROFILE", "Ada Okafor", "@ada-builds", ada.bio!!,
            "1,284", "FOLLOWERS", "211", "FOLLOWING", "64", "REPOS", "1,307", "STARS",
            "Lagos, Nigeria", "Fieldwork Labs", "ada.example.com", "OPEN ON GITHUB"
        )
        check.assertAbsent("OFFLINE")
        check.assertTargetsInsideSafeArea()
        check.capture("profile--default")

        composeRule.onNodeWithText("OPEN ON GITHUB").performClick()
        composeRule.onNodeWithText("ada.example.com").performClick()
        assertEquals(listOf("https://github.com/ada-builds", "https://ada.example.com"), opened)
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun emptyFieldsAreLeftOut() {
        show(loaded(ada.copy(bio = null, location = null, company = null, blog = null)))
        check.assertShown("Ada Okafor", "OPEN ON GITHUB")
        check.assertAbsent(ada.bio!!, "Lagos", "Fieldwork", "ada.example.com")
        listOf("Location", "Company", "Website").forEach { label ->
            assertTrue(
                "$label row is left out",
                composeRule.onAllNodes(hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test
    fun offlineCached() {
        show(loaded(ada, offline = true))
        check.assertShown("Ada Okafor", "1,284", "OFFLINE — saved 2 hours ago", "OPEN ON GITHUB")
        check.assertTargetsInsideSafeArea()
        check.capture("profile--offline-cached")
    }

    @Test
    fun loading() {
        show(ProfileUiState.Loading)
        composeRule.onNodeWithTag("profile-loading").fetchSemanticsNode()
        check.assertShown("PROFILE")
        check.assertAbsent("OPEN ON GITHUB", "PROFILE_UNAVAILABLE")
        check.assertTargetsInsideSafeArea()
        check.capture("profile--loading")
    }

    @Test
    fun error() {
        show(ProfileUiState.Error)
        check.assertShown(
            "PROFILE_UNAVAILABLE",
            "GitHub did not return your profile, and no saved copy exists yet. Pull down to retry."
        )
        check.assertTargetsInsideSafeArea()
        check.capture("profile--error")
    }

    @Test
    fun worst() {
        show(loaded(worst))
        assertTrue("name wraps to at most two lines", check.layout(worst.name!!).lineCount <= 2)
        val login = check.layout("@${worst.login}")
        assertEquals(1, login.lineCount)
        val bio = check.layout(worstBio)
        assertTrue("the bio shows in full", !bio.hasVisualOverflow && !bio.isLineEllipsized(bio.lineCount - 1))
        check.assertShown("1.3M", "0", "1,000", "48,912")
        listOf(worst.location!!, worst.company!!, worst.blog!!).forEach { fact ->
            assertEquals("$fact lines", 1, check.layout(fact).lineCount)
        }
        check.assertTargetsInsideSafeArea()
        check.capture("profile--worst")
    }
}
