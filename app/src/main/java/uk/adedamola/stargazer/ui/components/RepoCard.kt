package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.adedamola.stargazer.ui.theme.FactoryBorder
import uk.adedamola.stargazer.ui.theme.FactorySurfaceGrey

@Composable
fun RepoCard(
    repoName: String,
    repoDescription: String,
    language: String,
    stars: Int,
    owner: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, FactoryBorder)), // Sharp mechanical border
        colors = CardDefaults.cardColors(
            containerColor = FactorySurfaceGrey
        ),
        shape = androidx.compose.ui.graphics.RectangleShape // No rounded corners for card
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Owner / Repo
            Text(
                text = "$owner /",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = repoName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = repoDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = FactoryBorder, thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Dot indicator
                    Spacer(
                        modifier = Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Stars
                Text(
                    text = "★ $stars",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
