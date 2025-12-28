package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.repository.SortOption
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBar(
    sortOption: SortOption,
    showFavoritesOnly: Boolean,
    showPinnedOnly: Boolean,
    selectedTag: Tag?,
    availableTags: List<Tag>,
    onSortChange: (SortOption) -> Unit,
    onFavoritesToggle: () -> Unit,
    onPinnedToggle: () -> Unit,
    onTagSelect: (Tag?) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header Row: Sort + Clear Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Dropdown
            TextButton(onClick = { showSortMenu = true }) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Sort",
                    tint = FactoryCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SORT: ${sortOption.name}",
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.name.replace("_", " "),
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            showSortMenu = false
                        }
                    )
                }
            }

            // Clear Filters
            if (showFavoritesOnly || showPinnedOnly || selectedTag != null) {
                IconButton(onClick = onClearFilters) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear filters",
                        tint = FactoryOrange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Favorites Filter
            FilterChip(
                selected = showFavoritesOnly,
                onClick = onFavoritesToggle,
                label = {
                    Text(
                        text = "FAVORITES",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (showFavoritesOnly) FactoryYellow else Color.White.copy(alpha = 0.6f)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FactoryYellow.copy(alpha = 0.3f),
                    selectedLabelColor = FactoryYellow
                )
            )

            // Pinned Filter
            FilterChip(
                selected = showPinnedOnly,
                onClick = onPinnedToggle,
                label = {
                    Text(
                        text = "PINNED",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (showPinnedOnly) FactoryOrange else Color.White.copy(alpha = 0.6f)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FactoryOrange.copy(alpha = 0.3f),
                    selectedLabelColor = FactoryOrange
                )
            )

            // Tag Filter
            if (availableTags.isNotEmpty()) {
                FilterChip(
                    selected = selectedTag != null,
                    onClick = { showTagMenu = true },
                    label = {
                        Text(
                            text = selectedTag?.name ?: "TAGS",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = selectedTag?.let {
                            Color(android.graphics.Color.parseColor(it.color)).copy(alpha = 0.3f)
                        } ?: Color.Transparent,
                        selectedLabelColor = selectedTag?.let {
                            Color(android.graphics.Color.parseColor(it.color))
                        } ?: Color.White
                    )
                )

                DropdownMenu(
                    expanded = showTagMenu,
                    onDismissRequest = { showTagMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "All Tags",
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        onClick = {
                            onTagSelect(null)
                            showTagMenu = false
                        }
                    )
                    availableTags.forEach { tag ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = tag.name,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(android.graphics.Color.parseColor(tag.color))
                                )
                            },
                            onClick = {
                                onTagSelect(tag)
                                showTagMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
