package uk.adedamola.stargazer.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repoName: String?,
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
                        text = "REPOSITORY_DETAILS",
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
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
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = repo.owner.login,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                color = FactoryCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = repo.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 24.sp,
                                color = FactoryOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Description
                    if (repo.description != null) {
                        Text(
                            text = "DESCRIPTION:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = repo.description,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("STARS", repo.stargazersCount.toString(), FactoryYellow)
                        StatItem("FORKS", repo.forksCount.toString(), FactoryCyan)
                        StatItem("WATCHERS", repo.watchersCount.toString(), FactoryOrange)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Language
                    if (repo.language != null) {
                        DetailItem("LANGUAGE", repo.language)
                    }

                    // Default Branch
                    DetailItem("DEFAULT_BRANCH", repo.defaultBranch)

                    // License
                    if (repo.license != null) {
                        DetailItem("LICENSE", repo.license.name)
                    }

                    // Open Issues
                    DetailItem("OPEN_ISSUES", repo.openIssuesCount.toString())

                    // Visibility
                    DetailItem("VISIBILITY", repo.visibility.uppercase())

                    // Created At
                    DetailItem("CREATED", repo.createdAt)

                    // Updated At
                    DetailItem("LAST_UPDATED", repo.updatedAt)

                    // Homepage
                    if (!repo.homepage.isNullOrBlank()) {
                        DetailItem("HOMEPAGE", repo.homepage)
                    }

                    // Topics
                    if (repo.topics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "TOPICS:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = repo.topics.joinToString(", "),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = FactoryCyan,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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

@Composable
fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "$label:",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
