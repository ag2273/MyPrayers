package app.mawaqeet.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mawaqeet.R

/**
 * The Tools Row on Today, under the Next Prayer card: equal one-line buttons, icon beside label, 48dp tall. A tool
 * whose callback is null is hidden (the Calendar and Mosques tools until their screens exist).
 */
@Composable
fun ToolsRow(
    onQibla: () -> Unit,
    onCalendar: (() -> Unit)? = null,
    onMosques: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolButton(R.string.tool_qibla, R.drawable.ic_qibla, onQibla)
        onCalendar?.let { ToolButton(R.string.tool_calendar, R.drawable.ic_today, it) }
        onMosques?.let { ToolButton(R.string.tool_mosques, R.drawable.ic_pin, it) }
    }
}

private val ToolShape = RoundedCornerShape(16.dp)

@Composable
private fun androidx.compose.foundation.layout.RowScope.ToolButton(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(ToolShape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, ToolShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
