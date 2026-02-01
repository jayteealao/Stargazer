package uk.adedamola.stargazer.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import uk.adedamola.stargazer.R
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repoName: String?,
    repoId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(repoName) {
        if (repoName != null) {
            viewModel.loadRepository(repoName)
        }
    }

    Scaffold(
        containerColor = FactoryDarkGrey,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = repoName?.split("/")?.lastOrNull()?.uppercase() ?: "REPOSITORY",
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FactoryDarkGrey,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = FactoryOrange)
                        Text(
                            text = "LOADING_REPOSITORY_DATA...",
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }

            is DetailUiState.Success -> {
                val repo = state.repository
                val readme = state.readme
                val uriHandler = LocalUriHandler.current
                with(sharedTransitionScope) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "card-$repoId"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Owner Avatar and Name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = repo.owner.avatarUrl,
                                contentDescription = "Owner avatar",
                                modifier = Modifier
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "avatar-$repoId"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = repo.owner.login,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 20.sp,
                                    color = FactoryCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "owner-$repoId"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                )
                                Text(
                                    text = repo.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
                                    color = FactoryOrange,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "name-$repoId"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description
                        if (repo.description != null) {
                            Text(
                                text = repo.description,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        // Stats Row - ABOVE divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Stars with shared element
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "stars-$repoId"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            ) {
                                Text(
                                    text = "STARS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "★ ${repo.stargazersCount}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FactoryYellow
                                )
                            }
                            StatItem("FORKS", repo.forksCount.toString(), FactoryCyan)
                            StatItem("WATCHERS", repo.watchersCount.toString(), FactoryOrange)

                            // GitHub icon button (inline with stats)
                            IconButton(
                                onClick = { uriHandler.openUri(repo.htmlUrl) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White, CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_github),
                                    contentDescription = "Open on GitHub",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Divider
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                        Spacer(modifier = Modifier.height(16.dp))

                        // README Content
                        if (readme != null) {
                            ReadmeContent(markdown = readme)
                        } else {
                            Text(
                                text = "NO_README_AVAILABLE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ERROR: ${state.message}",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReadmeContent(markdown: String) {
    Markdown(
        content = markdown,
        imageTransformer = Coil3ImageTransformerImpl,
        components = markdownComponents(
            codeBlock = highlightedCodeBlock,
            codeFence = highlightedCodeFence
        ),
        colors = markdownColor(
            text = Color.White
        ),
        typography = markdownTypography(
            text = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color.White
            ),
            code = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = FactoryCyan
            )
        )
    )
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
