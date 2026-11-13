package app.mawaqeet.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.mawaqeet.ui.theme.LocalSkin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mawaqeet.R

/** The corner radius of cards (the redesign uses one radius everywhere). */
val CardShape = RoundedCornerShape(22.dp)

/**
 * A top-level tab's page (Today, Prayer Log, Settings): its title as a heading, then [content] in a scrolling column.
 * The bottom navigation bar is drawn around it by the activity.
 */
@Composable
fun TabPage(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() },
            )
        }
        content()
    }
}

/** A page opened from another (a Settings section, Fix alerts): a back arrow and the title, then [content]. */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    // Transparent: the living sky (MainActivity) shows behind every page.
    Scaffold(bottomBar = bottomBar, containerColor = Color.Transparent) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val skin = LocalSkin.current
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(skin.surface)
                        .border(1.dp, skin.line, RoundedCornerShape(14.dp)),
                ) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), modifier = Modifier.size(20.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** A small coloured heading above a card. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp).semantics { heading() },
    )
}

/** A translucent card on the sky (the design's `--surface` with a hairline), holding rows separated by [RowDivider]. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val skin = LocalSkin.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(skin.surface)
            .border(1.dp, skin.line, CardShape),
        content = content,
    )
}

/** The thin line between two rows of a card. */
@Composable
fun RowDivider() = HorizontalDivider(color = LocalSkin.current.line)

/** An icon on a soft teal tile, at the start of a navigation row. */
@Composable
fun IconTile(@DrawableRes icon: Int) {
    Box(
        Modifier.size(40.dp).background(LocalSkin.current.accentSoft, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** A row's title and the current value or explanation under it. */
@Composable
fun RowScope.RowText(title: String, summary: String?) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (!summary.isNullOrEmpty()) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A row that opens another page or a picker: optional icon tile, title, its current value, a forward chevron. */
@Composable
fun NavRow(title: String, summary: String?, @DrawableRes icon: Int? = null, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconTile(icon)
        RowText(title, summary)
        trailing()
        Icon(
            painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A row with an on/off switch; the whole row toggles it. */
@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(title, description)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** One option of a segmented control. */
data class Segment(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/** Two or three mutually exclusive options side by side on a grey track (Asr convention, language, widget colours). */
@Composable
fun SegmentedChoice(segments: List<Segment>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val skin = LocalSkin.current
    Row(
        modifier
            .fillMaxWidth()
            .background(skin.track.copy(alpha = skin.track.alpha * 0.5f), RoundedCornerShape(14.dp))
            .border(1.dp, skin.line, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { segment ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (segment.selected) colors.primary else Color.Transparent)
                    .selectable(selected = segment.selected, role = Role.RadioButton, onClick = segment.onSelect)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    segment.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (segment.selected) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}
