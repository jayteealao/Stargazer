package uk.adedamola.stargazer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.paging.SyncPhase
import uk.adedamola.stargazer.ui.components.CreateTagDialog
import uk.adedamola.stargazer.ui.components.FilterDrawerContent
import uk.adedamola.stargazer.ui.components.RepoCard
import uk.adedamola.stargazer.ui.components.SearchBar
import uk.adedamola.stargazer.ui.components.TagAssignmentSheet
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRepoClick: (String, Int) -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
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

    // Track repository states locally (favorite, pinned, tags)
    val repositoryStates = remember { mutableStateMapOf<Int, RepositoryState>() }

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
                drawerContainerColor = FactoryDarkGrey
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
                    onSortChange = {
                        viewModel.setSortOption(it)
                        scope.launch { drawerState.close() }
                    },
                    onFavoritesToggle = {
                        viewModel.toggleFavoritesFilter()
                        scope.launch { drawerState.close() }
                    },
                    onPinnedToggle = {
                        viewModel.togglePinnedFilter()
                        scope.launch { drawerState.close() }
                    },
                    onTagSelect = { tag ->
                        viewModel.filterByTag(tag?.id)
                        scope.launch { drawerState.close() }
                    },
                    onLanguageSelect = { language ->
                        viewModel.filterByLanguage(language)
                        scope.launch { drawerState.close() }
                    },
                    onStarRangeChange = { min, max ->
                        viewModel.setStarRange(min, max)
                    },
                    onSavePreset = { name ->
                        viewModel.saveCurrentAsPreset(name)
                    },
                    onLoadPreset = { preset ->
                        viewModel.loadPreset(preset)
                        scope.launch { drawerState.close() }
                    },
                    onDeletePreset = { preset ->
                        viewModel.deletePreset(preset)
                    },
                    onClearFilters = {
                        viewModel.clearAllFilters()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = FactoryDarkGrey,
            topBar = {
                TopAppBar(
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

                // Repository List
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
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
                    loadState.refresh is LoadState.Loading && repositories.itemCount == 0 -> {
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
                    loadState.refresh is LoadState.Error && repositories.itemCount == 0 -> {
                        val error = (loadState.refresh as LoadState.Error).error
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "ERROR: ${error.message}",
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
                    loadState.refresh is LoadState.NotLoading && repositories.itemCount == 0 -> {
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
                            items(
                                count = repositories.itemCount,
                                key = repositories.itemKey { it.id }
                            ) { index ->
                                val repo = repositories[index]
                                if (repo != null) {
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
                                        modifier = Modifier
                                            .animateItem()
                                            .clickable { onRepoClick(repo.fullName, repo.id) }
                                    )
                                }
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
