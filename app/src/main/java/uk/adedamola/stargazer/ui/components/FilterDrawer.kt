package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.adedamola.stargazer.data.local.database.Tag
import uk.adedamola.stargazer.data.repository.SortOption
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryYellow
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterDrawerContent(
    sortOption: SortOption,
    showFavoritesOnly: Boolean,
    showPinnedOnly: Boolean,
    selectedTag: Tag?,
    availableTags: List<Tag>,
    selectedLanguage: String?,
    availableLanguages: List<String>,
    minStars: Int?,
    maxStars: Int?,
    savedPresets: List<uk.adedamola.stargazer.data.local.database.SearchPreset>,
    matchingReposCount: Int?,
    onSortChange: (SortOption) -> Unit,
    onFavoritesToggle: () -> Unit,
    onPinnedToggle: () -> Unit,
    onTagSelect: (Tag?) -> Unit,
    onLanguageSelect: (String?) -> Unit,
    onStarRangeChange: (Int?, Int?) -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (uk.adedamola.stargazer.data.local.database.SearchPreset) -> Unit,
    onDeletePreset: (uk.adedamola.stargazer.data.local.database.SearchPreset) -> Unit,
    onClearFilters: () -> Unit,
    onApplyFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collapsible section states
    var expandedPresets by remember { mutableStateOf(false) }
    var expandedSort by remember { mutableStateOf(true) }
    var expandedFilters by remember { mutableStateOf(true) }
    var expandedTags by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(FactoryDarkGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp) // Space for sticky Apply button
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

        Spacer(modifier = Modifier.height(16.dp))

        // Active Filters Chips Section
        val hasActiveFilters = showFavoritesOnly || showPinnedOnly || selectedTag != null ||
                               selectedLanguage != null || (minStars != null && maxStars != null)

        if (hasActiveFilters) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "ACTIVE_FILTERS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = FactoryCyan
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedLanguage != null) {
                        AssistChip(
                            onClick = { onLanguageSelect(null) },
                            label = {
                                Text(
                                    text = selectedLanguage,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = FactoryCyan.copy(alpha = 0.2f),
                                labelColor = FactoryCyan,
                                trailingIconContentColor = FactoryCyan
                            )
                        )
                    }

                    if (minStars != null || maxStars != null) {
                        AssistChip(
                            onClick = { onStarRangeChange(null, null) },
                            label = {
                                Text(
                                    text = "⭐ ${minStars ?: 0}-${maxStars ?: "∞"}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = FactoryYellow.copy(alpha = 0.2f),
                                labelColor = FactoryYellow,
                                trailingIconContentColor = FactoryYellow
                            )
                        )
                    }

                    if (showFavoritesOnly) {
                        AssistChip(
                            onClick = onFavoritesToggle,
                            label = {
                                Text(
                                    text = "FAVORITES",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = FactoryYellow.copy(alpha = 0.2f),
                                labelColor = FactoryYellow,
                                trailingIconContentColor = FactoryYellow
                            )
                        )
                    }

                    if (showPinnedOnly) {
                        AssistChip(
                            onClick = onPinnedToggle,
                            label = {
                                Text(
                                    text = "PINNED",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = FactoryOrange.copy(alpha = 0.2f),
                                labelColor = FactoryOrange,
                                trailingIconContentColor = FactoryOrange
                            )
                        )
                    }

                    if (selectedTag != null) {
                        val tagColor = try {
                            Color(android.graphics.Color.parseColor(selectedTag.color))
                        } catch (e: Exception) {
                            FactoryCyan
                        }
                        AssistChip(
                            onClick = { onTagSelect(null) },
                            label = {
                                Text(
                                    text = selectedTag.name.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = tagColor.copy(alpha = 0.2f),
                                labelColor = tagColor,
                                trailingIconContentColor = tagColor
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Live Preview Count
        if (matchingReposCount != null && hasActiveFilters) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FactoryOrange.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MATCHES:",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "$matchingReposCount REPOS",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = FactoryOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Search Presets Section
        var showSavePresetDialog by remember { mutableStateOf(false) }

        SectionHeader(
            text = "🔍 PRESETS",
            isExpanded = expandedPresets,
            onToggle = { expandedPresets = !expandedPresets }
        )

        if (expandedPresets) {
            // Save current filters button
            TextButton(
                onClick = { showSavePresetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = FactoryCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SAVE_CURRENT",
                    fontFamily = FontFamily.Monospace,
                    color = FactoryCyan
                )
            }

            // Saved presets list
            if (savedPresets.isNotEmpty()) {
                savedPresets.forEach { preset ->
                    PresetRow(
                        preset = preset,
                        onLoad = { onLoadPreset(preset) },
                        onDelete = { onDeletePreset(preset) }
                    )
                }
            }
        }

        if (showSavePresetDialog) {
            SavePresetDialog(
                onDismiss = { showSavePresetDialog = false },
                onSave = { name ->
                    onSavePreset(name)
                    showSavePresetDialog = false
                }
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Sort Section
        SectionHeader(
            text = "📊 SORT_BY",
            isExpanded = expandedSort,
            onToggle = { expandedSort = !expandedSort }
        )

        if (expandedSort) {
            SortOption.entries.forEach { option ->
                SortOptionRow(
                    option = option,
                    isSelected = sortOption == option,
                    onClick = { onSortChange(option) }
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Filters Section
        SectionHeader(
            text = "🎯 FILTERS",
            isExpanded = expandedFilters,
            onToggle = { expandedFilters = !expandedFilters }
        )

        if (expandedFilters) {
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

            // Language Filter
            if (availableLanguages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "LANGUAGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LanguageFilterDropdown(
                    selectedLanguage = selectedLanguage,
                    availableLanguages = availableLanguages,
                    onLanguageSelect = onLanguageSelect
                )
            }

            // Star Range
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "STAR_RANGE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            StarRangeSlider(
                minStars = minStars,
                maxStars = maxStars,
                onStarRangeChange = onStarRangeChange
            )
        }

        // Tags Section
        if (availableTags.isNotEmpty()) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            SectionHeader(
                text = "🏷️ TAGS",
                isExpanded = expandedTags,
                onToggle = { expandedTags = !expandedTags }
            )

            if (expandedTags) {
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
        }

        }

        // Sticky Apply/Clear Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FactoryDarkGrey)
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Apply Filters Button
            Button(
                onClick = onApplyFilters,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FactoryOrange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APPLY_FILTERS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Clear All Button
            if (hasActiveFilters) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onClearFilters,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "[ CLEAR_ALL ]",
                        fontFamily = FontFamily.Monospace,
                        color = FactoryCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    isExpanded: Boolean = true,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onToggle != null) { onToggle?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = FactoryCyan
        )
        if (onToggle != null) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = FactoryCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
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
            text = when (option) {
                SortOption.STARRED -> "RECENTLY STARRED"
                else -> option.name.replace("_", " ")
            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageFilterDropdown(
    selectedLanguage: String?,
    availableLanguages: List<String>,
    onLanguageSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = selectedLanguage ?: "ALL_LANGUAGES",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                focusedBorderColor = FactoryCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = FactoryCyan
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(FactoryDarkGrey)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "ALL_LANGUAGES",
                        fontFamily = FontFamily.Monospace,
                        color = if (selectedLanguage == null) FactoryCyan else Color.White.copy(alpha = 0.8f)
                    )
                },
                onClick = {
                    onLanguageSelect(null)
                    expanded = false
                }
            )
            availableLanguages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            color = if (selectedLanguage == language) FactoryCyan else Color.White.copy(alpha = 0.8f)
                        )
                    },
                    onClick = {
                        onLanguageSelect(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StarRangeSlider(
    minStars: Int?,
    maxStars: Int?,
    onStarRangeChange: (Int?, Int?) -> Unit
) {
    var sliderRange by remember(minStars, maxStars) {
        mutableStateOf(
            (minStars?.toFloat() ?: 0f) to (maxStars?.toFloat() ?: 10000f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Display current range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${sliderRange.first.roundToInt()} STARS",
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${sliderRange.second.roundToInt()} STARS",
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        RangeSlider(
            value = sliderRange.first..sliderRange.second,
            onValueChange = { range ->
                sliderRange = range.start to range.endInclusive
            },
            onValueChangeFinished = {
                val min = sliderRange.first.roundToInt()
                val max = sliderRange.second.roundToInt()
                onStarRangeChange(
                    if (min > 0) min else null,
                    if (max < 10000) max else null
                )
            },
            valueRange = 0f..10000f,
            steps = 100,
            colors = SliderDefaults.colors(
                thumbColor = FactoryOrange,
                activeTrackColor = FactoryOrange,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )

        // Clear button
        if (minStars != null || maxStars != null) {
            TextButton(
                onClick = {
                    sliderRange = 0f to 10000f
                    onStarRangeChange(null, null)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "CLEAR",
                    fontFamily = FontFamily.Monospace,
                    color = FactoryCyan,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: uk.adedamola.stargazer.data.local.database.SearchPreset,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = preset.name.uppercase(),
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(
            onClick = onDelete
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete preset",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var presetName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "SAVE_PRESET",
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        },
        text = {
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = {
                    Text(
                        text = "PRESET_NAME",
                        fontFamily = FontFamily.Monospace
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                    focusedBorderColor = FactoryCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = FactoryCyan,
                    focusedLabelColor = FactoryCyan,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (presetName.isNotBlank()) {
                        onSave(presetName.trim())
                    }
                },
                enabled = presetName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FactoryOrange,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "SAVE",
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        },
        containerColor = FactoryDarkGrey,
        shape = RoundedCornerShape(8.dp)
    )
}
