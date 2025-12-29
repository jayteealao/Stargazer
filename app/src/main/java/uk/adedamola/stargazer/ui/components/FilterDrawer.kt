package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.repository.SortOption
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryYellow

@Composable
fun FilterDrawerContent(
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
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(FactoryDarkGrey)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "STARGAZER_SYSTEM",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "v1.0.0 // ONLINE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = FactoryOrange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Sort Section
        SectionHeader(text = "SORT_BY")
        SortOption.entries.forEach { option ->
            SortOptionRow(
                option = option,
                isSelected = sortOption == option,
                onClick = { onSortChange(option) }
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Filters Section
        SectionHeader(text = "FILTERS")
        FilterToggleRow(
            label = "FAVORITES_ONLY",
            isChecked = showFavoritesOnly,
            onToggle = onFavoritesToggle,
            icon = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (showFavoritesOnly) FactoryYellow else Color.White.copy(alpha = 0.6f)
                )
            }
        )
        FilterToggleRow(
            label = "PINNED_ONLY",
            isChecked = showPinnedOnly,
            onToggle = onPinnedToggle,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = if (showPinnedOnly) FactoryOrange else Color.White.copy(alpha = 0.6f)
                )
            }
        )

        // Tags Section
        if (availableTags.isNotEmpty()) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            SectionHeader(text = "TAGS")

            TagOptionRow(
                label = "ALL_TAGS",
                color = Color.White,
                isSelected = selectedTag == null,
                onClick = { onTagSelect(null) }
            )

            availableTags.forEach { tag ->
                val tagColor = try {
                    Color(android.graphics.Color.parseColor(tag.color))
                } catch (e: Exception) {
                    FactoryCyan
                }
                TagOptionRow(
                    label = tag.name.uppercase(),
                    color = tagColor,
                    isSelected = selectedTag?.id == tag.id,
                    onClick = { onTagSelect(tag) }
                )
            }
        }

        // Clear Filters Button
        val hasActiveFilters = showFavoritesOnly || showPinnedOnly || selectedTag != null
        if (hasActiveFilters) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onClearFilters,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "[ CLEAR_FILTERS ]",
                    fontFamily = FontFamily.Monospace,
                    color = FactoryOrange
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = FactoryCyan,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SortOptionRow(
    option: SortOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = FactoryOrange,
                unselectedColor = Color.White.copy(alpha = 0.6f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = option.name.replace("_", " "),
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun FilterToggleRow(
    label: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            color = if (isChecked) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = FactoryOrange,
                checkedTrackColor = FactoryOrange.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun TagOptionRow(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = color,
                unselectedColor = color.copy(alpha = 0.6f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) color else color.copy(alpha = 0.6f)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = color
            )
        }
    }
}
