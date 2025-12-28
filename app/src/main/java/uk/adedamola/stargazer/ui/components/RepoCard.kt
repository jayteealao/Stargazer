package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.ui.theme.FactoryBorder
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactorySurfaceGrey
import uk.adedamola.stargazer.ui.theme.FactoryYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepoCard(
    repoName: String,
    repoDescription: String,
    language: String,
    stars: Int,
    owner: String,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    isPinned: Boolean = false,
    tags: List<Tag> = emptyList(),
    onFavoriteClick: () -> Unit = {},
    onPinClick: () -> Unit = {},
    onTagsClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, FactoryBorder)),
        colors = CardDefaults.cardColors(
            containerColor = FactorySurfaceGrey
        ),
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Owner / Repo + Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                }

                // Action Icons
                Row {
                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = if (isPinned) FactoryOrange else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                            tint = if (isFavorite) FactoryYellow else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = repoDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Tags
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        AssistChip(
                            onClick = onTagsClick,
                            label = {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(android.graphics.Color.parseColor(tag.color)).copy(alpha = 0.3f),
                                labelColor = Color(android.graphics.Color.parseColor(tag.color))
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = Color(android.graphics.Color.parseColor(tag.color))
                            )
                        )
                    }
                    // Add tag button
                    AssistChip(
                        onClick = onTagsClick,
                        label = {
                            Text(
                                text = "+ Tag",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.Transparent,
                            labelColor = Color.White.copy(alpha = 0.6f)
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = FactoryBorder
                        )
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = onTagsClick,
                    label = {
                        Text(
                            text = "+ Add Tags",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        borderColor = FactoryBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = FactoryBorder, thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language
                Row(verticalAlignment = Alignment.CenterVertically) {
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
