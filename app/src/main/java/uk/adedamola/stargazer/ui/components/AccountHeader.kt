package uk.adedamola.stargazer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.adedamola.stargazer.data.auth.AccountSummary
import uk.adedamola.stargazer.ui.theme.FactoryTextGrey
import uk.adedamola.stargazer.ui.theme.FactoryTextWhite

/**
 * The linked GitHub account at the top of the drawer: avatar, name and @login, and a chevron to
 * the profile. With no name, the @login is the title and "GitHub account" sits below it. Long
 * values truncate on one line; the chevron always stays visible.
 */
@Composable
fun DrawerAccountHeader(
    account: AccountSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = account.name ?: "@${account.login}"
    val subtitle = if (account.name != null) "@${account.login}" else "GitHub account"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Open profile", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag("drawer-account-header")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountAvatar(account, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                color = FactoryTextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = FactoryTextGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = FactoryTextGrey,
            modifier = Modifier.size(20.dp)
        )
    }
}
