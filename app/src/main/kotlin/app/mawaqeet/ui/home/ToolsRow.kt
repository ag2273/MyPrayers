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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.mawaqeet.R

/**
 * The Tools Row on Today, under the Next Prayer card: equal one-line buttons, icon beside label, 48dp tall. A tool
 * whose callback is null is hidden.
 */
@Composable
fun ToolsRow(
    onQibla: () -> Unit,
    onCalendar: (() -> Unit)? = null,
    onMosques: (() -> Unit)? = null,
) {
    EqualRowOrStack {
        ToolButton(R.string.tool_qibla, R.drawable.ic_compass, onQibla)
        onCalendar?.let { ToolButton(R.string.tool_calendar, R.drawable.ic_calendar, it) }
        onMosques?.let { ToolButton(R.string.tool_mosques, R.drawable.ic_mosque, it) }
    }
}

private val ToolGap = 8.dp

/**
 * The buttons side by side in equal widths and one height; when a label would not fit its share (a large system
 * font), one above the other at full width instead, so no label is cut off.
 */
@Composable
private fun EqualRowOrStack(content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = ToolGap.roundToPx()
        val width = constraints.maxWidth
        val share = (width - gap * (measurables.size - 1)) / measurables.size.coerceAtLeast(1)
        if (measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= share }) {
            val height = measurables.maxOf { it.minIntrinsicHeight(share) }
            val placeables = measurables.map { it.measure(Constraints.fixed(share, height)) }
            layout(width, placeables.maxOf { it.height }) {
                placeables.forEachIndexed { i, p -> p.placeRelative(i * (share + gap), 0) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            layout(width, placeables.sumOf { it.height } + gap * (placeables.size - 1)) {
                var y = 0
                placeables.forEach { p -> p.placeRelative(0, y); y += p.height + gap }
            }
        }
    }
}

/**
 * Items side by side across the full width with the Tools Row's gap: each keeps its own width and the room left over
 * is shared out equally, so the row starts and ends where the Tools Row does. Items that do not fit one line wrap to
 * the next, and each line spreads the same way.
 */
@Composable
internal fun SpreadRows(content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = ToolGap.roundToPx()
        val width = constraints.maxWidth
        val natural = measurables.map { minOf(it.maxIntrinsicWidth(Constraints.Infinity), width) }
        val lines = mutableListOf<MutableList<Int>>()
        var used = 0
        natural.forEachIndexed { i, w ->
            val line = lines.lastOrNull()
            if (line == null || used + gap + w > width) {
                lines += mutableListOf(i); used = w
            } else {
                line += i; used += gap + w
            }
        }
        val placed = lines.map { line ->
            val extra = (width - line.sumOf { natural[it] } - gap * (line.size - 1)) / line.size
            val height = line.maxOf { measurables[it].minIntrinsicHeight(natural[it] + extra) }
            line.map { measurables[it].measure(Constraints(natural[it] + extra, natural[it] + extra, height, height)) }
        }
        val total = placed.sumOf { row -> row.maxOf { it.height } } + gap * (placed.size - 1)
        layout(width, total) {
            var y = 0
            placed.forEach { row ->
                var x = 0
                row.forEach { p -> p.placeRelative(x, y); x += p.width + gap }
                y += row.maxOf { it.height } + gap
            }
        }
    }
}

private val ToolShape = RoundedCornerShape(16.dp)

@Composable
private fun ToolButton(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val skin = app.mawaqeet.ui.theme.LocalSkin.current
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(ToolShape)
            .background(skin.surface)
            .border(1.dp, skin.line, ToolShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
