package uk.adedamola.stargazer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uk.adedamola.stargazer.ui.components.RepoCard
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey

// Mock Data
data class MockRepo(
    val name: String,
    val description: String,
    val language: String,
    val stars: Int,
    val owner: String
)

val mockRepos = listOf(
    MockRepo("stargazer", "A distinct industrial style repo viewer.", "Kotlin", 128, "adedamola"),
    MockRepo("factory-ai", "Industrial automation scripts for deployment.", "Python", 4052, "tech-corp"),
    MockRepo("react-native-skia", "High performance 2D graphics.", "TypeScript", 8900, "shopify"),
    MockRepo("compose-multiplatform", "Declarative UI framework for Kotlin.", "Kotlin", 12000, "jetbrains"),
    MockRepo("linux", "Linux kernel source tree", "C", 150000, "torvalds")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRepoClick: (String) -> Unit
) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(mockRepos) { repo ->
                RepoCard(
                    repoName = repo.name,
                    repoDescription = repo.description,
                    language = repo.language,
                    stars = repo.stars,
                    owner = repo.owner,
                    modifier = Modifier.clickable { onRepoClick(repo.name) }
                )
            }
        }
    }
}
