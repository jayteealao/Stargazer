package uk.adedamola.stargazer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import uk.adedamola.stargazer.ui.components.CreateTagDialog
import uk.adedamola.stargazer.ui.components.FilterBar
import uk.adedamola.stargazer.ui.components.RepoCard
import uk.adedamola.stargazer.ui.components.TagAssignmentSheet
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRepoClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val repositories = viewModel.repositories.collectAsLazyPagingItems()
    val sortOption by viewModel.sortOption.collectAsState()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val showPinnedOnly by viewModel.showPinnedOnly.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    // Track repository states locally (favorite, pinned, tags)
    val repositoryStates = remember { mutableStateMapOf<Int, RepositoryState>() }

    var isRefreshing by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var showTagAssignmentSheet by remember { mutableStateOf(false) }
    var selectedRepositoryForTags by remember { mutableStateOf<Pair<Int, String>?>(null) }

    Scaffold(
        containerColor = FactoryDarkGrey,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "STARGAZER_SYSTEM",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "v1.0.0 // ONLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
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
            // Filter Bar
            FilterBar(
                sortOption = sortOption,
                showFavoritesOnly = showFavoritesOnly,
                showPinnedOnly = showPinnedOnly,
                selectedTag = allTags.find { it.id == selectedTagId },
                availableTags = allTags,
                onSortChange = { viewModel.setSortOption(it) },
                onFavoritesToggle = { viewModel.toggleFavoritesFilter() },
                onPinnedToggle = { viewModel.togglePinnedFilter() },
                onTagSelect = { tag -> viewModel.filterByTag(tag?.id) },
                onClearFilters = { viewModel.clearAllFilters() }
            )

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
                                        repoName = repo.name,
                                        repoDescription = repo.description ?: "No description available",
                                        language = repo.language ?: "Unknown",
                                        stars = repo.stargazersCount,
                                        owner = repo.owner.login,
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
                                        modifier = Modifier.clickable { onRepoClick(repo.fullName) }
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
