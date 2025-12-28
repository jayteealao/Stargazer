package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import uk.adedamola.stargazer.ui.theme.FactoryBorder
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange

// Create Tag Dialog
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTagDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FF6600") }

    val predefinedColors = listOf(
        "#FF6600", // Orange
        "#00CCFF", // Cyan
        "#FFCC00", // Yellow
        "#FF3366", // Pink
        "#00FF88", // Green
        "#9966FF", // Purple
        "#FF6666", // Red
        "#66FFCC"  // Teal
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FactoryDarkGrey,
        title = {
            Text(
                text = "CREATE_NEW_TAG",
                fontFamily = FontFamily.Monospace,
                color = FactoryOrange
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = {
                        Text(
                            "TAG NAME",
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FactoryCyan,
                        focusedLabelColor = FactoryCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SELECT COLOR:",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    predefinedColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color(android.graphics.Color.parseColor(color)),
                                    CircleShape
                                )
                                .border(
                                    width = if (selectedColor == color) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tagName.isNotBlank()) {
                        onCreate(tagName, selectedColor)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FactoryOrange
                )
            ) {
                Text("CREATE", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", fontFamily = FontFamily.Monospace)
            }
        }
    )
}

// Tag Assignment Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagAssignmentSheet(
    repositoryName: String,
    availableTags: List<Tag>,
    assignedTags: List<Tag>,
    onDismiss: () -> Unit,
    onTagToggle: (Tag) -> Unit,
    onCreateNew: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FactoryDarkGrey
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "MANAGE TAGS",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleLarge,
                color = FactoryOrange
            )

            Text(
                text = repositoryName,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Create New Tag Button
            Button(
                onClick = onCreateNew,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FactoryCyan
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("CREATE NEW TAG", fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tag List
            if (availableTags.isEmpty()) {
                Text(
                    text = "NO TAGS AVAILABLE",
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                LazyColumn {
                    items(availableTags) { tag ->
                        val isAssigned = assignedTags.any { it.id == tag.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTagToggle(tag) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            Color(android.graphics.Color.parseColor(tag.color)),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = tag.name,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }

                            if (isAssigned) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Assigned",
                                    tint = FactoryCyan
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
