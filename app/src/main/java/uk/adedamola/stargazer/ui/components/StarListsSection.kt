package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.adedamola.stargazer.data.local.database.StarListEntity
import uk.adedamola.stargazer.data.repository.RefreshStatus
import uk.adedamola.stargazer.ui.theme.FactorySkeleton
import uk.adedamola.stargazer.ui.theme.FactoryMutedWhite
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryTextGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite
import uk.adedamola.stargazer.ui.theme.FactoryYellow

/**
 * The drawer's GitHub star lists (read-only): `ALL_LISTS`, then one radio row per list with its
 * item count. Cached lists always show, also offline; with none cached it shows loading while a
 * refresh runs, the error when the last one failed, and the empty state when GitHub has none.
 */
@Composable
fun StarListsSection(
    lists: List<StarListEntity>,
    selectedId: String?,
    status: RefreshStatus,
    onSelect: (String?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        when {
            lists.isNotEmpty() -> Column(Modifier.selectableGroup()) {
                StarListRow(name = "ALL_LISTS", count = null, selected = selectedId == null, onClick = { onSelect(null) })
                lists.forEach { list ->
                    StarListRow(
                        name = list.name.uppercase(),
                        count = list.itemCount,
                        selected = selectedId == list.id,
                        onClick = { onSelect(list.id) }
                    )
                }
            }
            status is RefreshStatus.Failed -> ListsMessage(
                title = "LISTS_UNAVAILABLE",
                body = "GitHub did not return your lists. Your stars still work.",
                icon = true,
                onRetry = onRetry
            )
            status is RefreshStatus.Succeeded -> ListsMessage(
                title = "NO_LISTS_YET",
                body = "Group stars into lists on github.com. They appear here after the next refresh.",
                icon = false,
                onRetry = null
            )
            else -> ListsLoading()
        }
    }
}

private val RowText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)

/** One list: radio, name (one line, ellipsis before the count) and the count, right aligned. */
@Composable
fun StarListRow(name: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag("star-list-row")
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = FactoryOrange,
                unselectedColor = FactoryMutedWhite
            ),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = RowText,
            color = if (selected) FactoryTextWhite else FactoryMutedWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.End
                ),
                color = FactoryTextGrey,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 32.dp)
            )
        }
    }
}

/** Three still skeleton rows: no shimmer in a dim room. */
@Composable
private fun ListsLoading() {
    val block = FactorySkeleton
    Column(
        Modifier
            .testTag("star-lists-loading")
            .semantics {
                contentDescription = "Loading star lists"
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        listOf(150.dp, 110.dp, 170.dp).forEach { width ->
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(20.dp).background(block, CircleShape))
                Spacer(Modifier.width(24.dp))
                Box(Modifier.width(width).height(14.dp).background(block))
            }
        }
    }
}

@Composable
private fun ListsMessage(title: String, body: String, icon: Boolean, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = FactoryYellow,
                    modifier = Modifier.padding(top = 1.dp).size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = FactoryTextWhite
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = FactoryTextGrey
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text(
                            text = "[ RETRY ]",
                            fontFamily = FontFamily.Monospace,
                            color = FactoryCyan,
                            modifier = Modifier.semantics { contentDescription = "Retry" }
                        )
                    }
                }
            }
        }
    }
}
