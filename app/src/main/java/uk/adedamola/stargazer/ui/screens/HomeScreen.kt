package uk.adedamola.stargazer.ui.screens

import uk.adedamola.stargazer.ui.theme.FactoryTextWhite
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.paging.SyncPhase
import uk.adedamola.stargazer.data.remote.GitHubErrorText
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.Attempt
import uk.adedamola.stargazer.ui.components.CreateTagDialog
import uk.adedamola.stargazer.ui.components.FilterDrawerContent
import uk.adedamola.stargazer.ui.components.RemovalNotice
import uk.adedamola.stargazer.ui.components.RepoCard
import uk.adedamola.stargazer.ui.components.SearchBar
import uk.adedamola.stargazer.ui.components.SearchSectionLabel
import uk.adedamola.stargazer.ui.components.StarSnackbarHost
import uk.adedamola.stargazer.ui.components.failureVisuals
import uk.adedamola.stargazer.ui.components.gitHubSearchSection
import uk.adedamola.stargazer.ui.components.sectionLabel
import uk.adedamola.stargazer.ui.components.undoVisuals
import uk.adedamola.stargazer.ui.components.TagAssignmentSheet
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import uk.adedamola.stargazer.ui.theme.PrimaryContainer
import uk.adedamola.stargazer.ui.theme.safeBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRepoClick: (String, Int) -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onProfileClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val repositories = viewModel.repositories.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val showPinnedOnly by viewModel.showPinnedOnly.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val availableLanguages by viewModel.availableLanguages.collectAsState()
    val minStars by viewModel.minStars.collectAsState()
    val maxStars by viewModel.maxStars.collectAsState()
    val savedPresets by viewModel.savedPresets.collectAsState()
    val account by viewModel.account.collectAsState()
    val starLists by viewModel.starLists.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val listsStatus by viewModel.listsStatus.collectAsState()
    val gitHubSearch by viewModel.gitHubSearchState.collectAsState()
    val searchControlStates by viewModel.searchControlStates.collectAsState()
    val undoOffer by viewModel.undoOffer.collectAsState()
    val reconciling by viewModel.reconciling.collectAsState()
    val removalNotice by viewModel.removalNotice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // The Undo offer stays up until its window ends; clearing the offer dismisses it.
    LaunchedEffect(undoOffer) {
        val offer = undoOffer ?: return@LaunchedEffect
        if (snackbarHostState.showSnackbar(undoVisuals(offer.fullName)) == SnackbarResult.ActionPerformed) {
            viewModel.undo(offer.repoId)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.starMessages.collect { message ->
            launch {
                if (snackbarHostState.showSnackbar(failureVisuals(message)) == SnackbarResult.ActionPerformed) {
                    viewModel.retry(message)
                }
            }
        }
    }

    // Local search results are labelled and followed by GitHub-wide results.
    val searching = searchQuery.isNotBlank() && !showFavoritesOnly && !showPinnedOnly &&
        selectedTagId == null && selectedListId == null

    // Track repository states locally (favorite, pinned, tags)
    val repositoryStates = remember { mutableStateMapOf<Int, RepositoryState>() }
    val (showUnstarHint, dismissUnstarHint) = rememberUnstarHint()

    var isRefreshing by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var showTagAssignmentSheet by remember { mutableStateOf(false) }
    var selectedRepositoryForTags by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = FactoryDarkGrey,
                windowInsets = WindowInsets.safeBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            ) {
                FilterDrawerContent(
                    sortOption = sortOption,
                    showFavoritesOnly = showFavoritesOnly,
                    showPinnedOnly = showPinnedOnly,
                    selectedTag = allTags.find { it.id == selectedTagId },
                    availableTags = allTags,
                    selectedLanguage = selectedLanguage,
                    availableLanguages = availableLanguages,
                    minStars = minStars,
                    maxStars = maxStars,
                    savedPresets = savedPresets,
                    matchingReposCount = repositories.itemCount,
                    onSortChange = { viewModel.setSortOption(it) },
                    onFavoritesToggle = { viewModel.toggleFavoritesFilter() },
                    onPinnedToggle = { viewModel.togglePinnedFilter() },
                    onTagSelect = { tag -> viewModel.filterByTag(tag?.id) },
                    onLanguageSelect = { language -> viewModel.filterByLanguage(language) },
                    onStarRangeChange = { min, max -> viewModel.setStarRange(min, max) },
                    onSavePreset = { name -> viewModel.saveCurrentAsPreset(name) },
                    onLoadPreset = { preset ->
                        viewModel.loadPreset(preset)
                        scope.launch { drawerState.close() }
                    },
                    onDeletePreset = { preset -> viewModel.deletePreset(preset) },
                    onClearFilters = { viewModel.clearAllFilters() },
                    onApplyFilters = {
                        scope.launch { drawerState.close() }
                    },
                    onLogout = { viewModel.logout() },
                    account = account,
                    starLists = starLists,
                    selectedListId = selectedListId,
                    listsStatus = listsStatus,
                    onListSelect = viewModel::filterByList,
                    onListsRetry = viewModel::refreshAccountData,
                    onProfileClick = {
                        scope.launch { drawerState.close() }
                        onProfileClick()
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = FactoryDarkGrey,
            contentWindowInsets = WindowInsets.safeBars,
            snackbarHost = { StarSnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets.safeBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    title = {
                        Text(
                            text = "STARGAZER",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open menu",
                                tint = FactoryOrange
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = FactoryDarkGrey,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search Bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange
                )

                // Sync Progress Indicator
                if (syncProgress.isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = FactoryOrange.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(0.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = FactoryOrange,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when (syncProgress.phase) {
                                SyncPhase.INITIAL_SYNC -> "SYNCING_STARRED_REPOS... ${syncProgress.loadedCount} LOADED"
                                SyncPhase.INCREMENTAL_SYNC -> "CHECKING_FOR_NEW_STARS..."
                                else -> "SYNCING..."
                            },
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // A refresh that failed over a filled list: one status line, the list stays.
                val failedRefresh = repositories.loadState.refresh as? LoadState.Error
                if (failedRefresh != null && repositories.itemCount > 0) {
                    Text(
                        text = GitHubErrorText.describe(failedRefresh.error),
                        fontFamily = FontFamily.Monospace,
                        // Warnings are yellow across the app.
                        color = FactoryYellow,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                // Repository List
                PullToRefreshBox(
                    // The refresh also reads the whole starred list; the indicator waits for it.
                    isRefreshing = isRefreshing || reconciling,
                    onRefresh = {
                        isRefreshing = true
                        repositories.refresh()
                        viewModel.refresh()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                val loadState = repositories.loadState

                // Handle refresh state
                LaunchedEffect(loadState.refresh) {
                    when (loadState.refresh) {
                        is LoadState.Loading -> isRefreshing = true
                        is LoadState.NotLoading -> isRefreshing = false
                        is LoadState.Error -> isRefreshing = false
                    }
                }

                when {
                    // Initial loading
                    !searching && loadState.refresh is LoadState.Loading && repositories.itemCount == 0 -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = FactoryOrange)
                                Text(
                                    text = "LOADING_DATA_STREAM...",
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    }

                    // Error during initial load
                    !searching && loadState.refresh is LoadState.Error && repositories.itemCount == 0 -> {
                        val error = (loadState.refresh as LoadState.Error).error
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = GitHubErrorText.describe(error),
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                                Text(
                                    text = "PULL_TO_RETRY",
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Empty state
                    !searching && loadState.refresh is LoadState.NotLoading && repositories.itemCount == 0 -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NO_REPOSITORIES_FOUND",
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Success state with data
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (searching) {
                                item(key = "in-your-stars-label") {
                                    SearchSectionLabel(sectionLabel("IN_YOUR_STARS", repositories.itemCount))
                                }
                            } else if (showUnstarHint && repositories.itemCount > 0) {
                                item(key = "unstar-hint") {
                                    UnstarHint(onDismiss = dismissUnstarHint)
                                }
                            }
                            items(
                                count = repositories.itemCount,
                                key = repositories.itemKey { it.id }
                            ) { index ->
                                val repo = repositories[index]
                                if (repo != null) UnstarSwipe(
                                    repo = repo,
                                    onUnstar = {
                                        viewModel.unstarFromSwipe(repo).also { if (it == Attempt.Sent) dismissUnstarHint() }
                                    },
                                    modifier = Modifier.animateItem()
                                ) { unstarAction ->
                                    // Load repository state if not cached
                                    LaunchedEffect(repo.id) {
                                        if (!repositoryStates.containsKey(repo.id)) {
                                            repositoryStates[repo.id] = viewModel.getRepositoryState(repo.id)
                                        }
                                    }

                                    val repoState = repositoryStates[repo.id]
                                    RepoCard(
                                        repoId = repo.id,
                                        repoName = repo.name,
                                        repoDescription = repo.description ?: "No description available",
                                        language = repo.language ?: "Unknown",
                                        stars = repo.stargazersCount,
                                        owner = repo.owner.login,
                                        ownerAvatarUrl = repo.owner.avatarUrl,
                                        isFavorite = repoState?.isFavorite ?: false,
                                        isPinned = repoState?.isPinned ?: false,
                                        tags = repoState?.tags ?: emptyList(),
                                        onFavoriteClick = {
                                            viewModel.toggleFavorite(
                                                repo.id,
                                                repoState?.isFavorite ?: false
                                            )
                                            // Update local state immediately for UI responsiveness
                                            repositoryStates[repo.id] = (repoState ?: RepositoryState()).copy(
                                                isFavorite = !(repoState?.isFavorite ?: false)
                                            )
                                        },
                                        onPinClick = {
                                            viewModel.togglePinned(
                                                repo.id,
                                                repoState?.isPinned ?: false
                                            )
                                            // Update local state immediately for UI responsiveness
                                            repositoryStates[repo.id] = (repoState ?: RepositoryState()).copy(
                                                isPinned = !(repoState?.isPinned ?: false)
                                            )
                                        },
                                        onTagsClick = {
                                            selectedRepositoryForTags = repo.id to repo.fullName
                                            showTagAssignmentSheet = true
                                        },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        // The card is the node TalkBack and Switch Access focus, so it
                                        // carries the gesture-free unstar.
                                        modifier = Modifier
                                            .then(unstarAction)
                                            .clickable { onRepoClick(repo.fullName, repo.id) }
                                    )
                                }
                            }

                            if (searching) {
                                gitHubSearchSection(
                                    state = gitHubSearch,
                                    controlStates = searchControlStates,
                                    onToggle = viewModel::toggleSearchStar,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }

                            // Show loading indicator at the bottom while loading more
                            if (loadState.append is LoadState.Loading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = FactoryOrange)
                                    }
                                }
                            }

                            // Show error at the bottom if append fails
                            if (loadState.append is LoadState.Error) {
                                item {
                                    val error = (loadState.append as LoadState.Error).error
                                    Text(
                                        text = "ERROR_LOADING_MORE: ${error.message}",
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    // Dialogs and Bottom Sheets
    removalNotice?.let { count -> RemovalNotice(count = count, onContinue = viewModel::confirmRemoval) }

    if (showCreateTagDialog) {
        CreateTagDialog(
            onDismiss = { showCreateTagDialog = false },
            onCreate = { name, color ->
                viewModel.createTag(name, color)
            }
        )
    }

    if (showTagAssignmentSheet && selectedRepositoryForTags != null) {
        val (repoId, repoName) = selectedRepositoryForTags!!
        val currentState = repositoryStates[repoId]

        TagAssignmentSheet(
            repositoryName = repoName,
            availableTags = allTags,
            assignedTags = currentState?.tags ?: emptyList(),
            onDismiss = { showTagAssignmentSheet = false },
            onTagToggle = { tag ->
                val isAssigned = currentState?.tags?.any { it.id == tag.id } ?: false
                if (isAssigned) {
                    viewModel.removeTagFromRepository(repoId, tag.id)
                } else {
                    viewModel.addTagToRepository(repoId, tag.id)
                }
                // Update local state immediately
                val updatedTags = if (isAssigned) {
                    currentState?.tags?.filter { it.id != tag.id } ?: emptyList()
                } else {
                    (currentState?.tags ?: emptyList()) + tag
                }
                repositoryStates[repoId] = (currentState ?: RepositoryState()).copy(tags = updatedTags)
            },
            onCreateNew = {
                showTagAssignmentSheet = false
                showCreateTagDialog = true
            }
        )
    }
}

/**
 * A Home row that unstars when swiped from end to start. The card slides over an orange-bordered
 * `UNSTAR` layer with the Material default settle. A refused unstar (offline, rate limited, busy)
 * puts the card back. The same action is offered to accessibility services without the gesture:
 * [content] gets it as a modifier for the card, the node they focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnstarSwipe(
    repo: GitHubRepository,
    onUnstar: suspend () -> Attempt,
    modifier: Modifier = Modifier,
    content: @Composable (unstarAction: Modifier) -> Unit
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val unstarAction = Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction("Unstar ${repo.fullName}") {
                scope.launch { onUnstar() }
                true
            }
        )
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                scope.launch { if (onUnstar() != Attempt.Sent) state.reset() }
            }
        },
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryContainer)
                    .border(1.dp, FactoryOrange, RectangleShape)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = FactoryOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "UNSTAR",
                    color = FactoryOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    ) {
        content(unstarAction)
    }
}

/** Shown on Home until the person unstars with a swipe or dismisses it: the swipe has no other cue. */
@Composable
private fun UnstarHint(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FactoryOrange.copy(alpha = 0.5f), RectangleShape)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.StarBorder,
            contentDescription = null,
            tint = FactoryOrange,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Swipe left to unstar. Undo shows for 5 seconds.",
            color = FactoryTextWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
            Text(
                text = "[ GOT_IT ]",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = FactoryCyan,
                modifier = Modifier.semantics { contentDescription = "Dismiss hint" }
            )
        }
    }
}

/** Per-phone memory of the one-time swipe hint; a convenience, so a failed read shows it again. */
private const val HINTS_PREFS = "home_hints"
private const val UNSTAR_HINT_SEEN = "unstar_hint_seen"

@Composable
private fun rememberUnstarHint(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(HINTS_PREFS, Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(runCatching { !prefs.getBoolean(UNSTAR_HINT_SEEN, false) }.getOrDefault(true)) }
    val dismiss = remember(prefs) {
        {
            if (show) {
                show = false
                runCatching { prefs.edit().putBoolean(UNSTAR_HINT_SEEN, true).apply() }
            }
        }
    }
    return show to dismiss
}
