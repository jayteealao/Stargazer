package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange

/**
 * Search bar component for repository search with industrial design aesthetic.
 * Features:
 * - Instant updates as user types (connected to debounced ViewModel)
 * - Clear button when text is present
 * - Keyboard dismiss on search action
 * - Monospace font matching app theme
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "SEARCH_REPOSITORIES..."
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.4f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = FactoryCyan
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = FactoryOrange
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = FactoryDarkGrey,
            unfocusedContainerColor = FactoryDarkGrey,
            focusedBorderColor = FactoryCyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            cursorColor = FactoryOrange
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
            }
        )
    )
}
