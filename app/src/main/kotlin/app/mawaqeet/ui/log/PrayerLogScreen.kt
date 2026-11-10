package app.mawaqeet.ui.log

import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.TabPage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.LogState
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerDay
import app.mawaqeet.core.PrayerWeek
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.settings.nameRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Prayer Log (#25): a week of Prayer Days × Prayers, newest day first, and the week's calm summary (counts only:
 * no streaks, badges or scores). The current week opens first; older weeks are browsable and read-only. Tapping a
 * Prayer of an editable day marks or unmarks it through the core, under the same rules as Home.
 */
@Composable
fun PrayerLogScreen(core: PrayerCore, logChanges: StateFlow<Int>) {
    // How many weeks back from the current one; saved so rotation keeps the week.
    var weeksBack by rememberSaveable { mutableStateOf(0) }
    val changes by logChanges.collectAsState()
    val week by produceState<PrayerWeek?>(null, core, changes, weeksBack) {
        value = withContext(Dispatchers.IO) {
            val current = core.currentPrayerDay().date
            core.week(current.minusDays(7L * weeksBack))
        }
    }
    val scope = rememberCoroutineScope()
    val format = LocalDisplayFormatter.current
    TabPage(stringResource(R.string.prayer_log)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WeekButton(R.string.log_previous_week, mirrored = false, enabled = true) { weeksBack += 1 }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (weeksBack == 0) R.string.log_this_week else R.string.log_earlier_week),
                    style = MaterialTheme.typography.titleMedium,
                )
                week?.let {
                    Text(
                        "${format.shortGregorianDate(it.days.first().date)} – ${format.shortGregorianDate(it.days.last().date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WeekButton(R.string.log_next_week, mirrored = true, enabled = weeksBack > 0) { weeksBack -= 1 }
        }
        val shown = week ?: return@TabPage
        Summary(shown)
        if (shown.days.none { it.isEditable }) {
            Text(
                stringResource(R.string.log_week_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card {
            Header()
            shown.days.asReversed().forEachIndexed { index, day ->
                if (index > 0) RowDivider()
                DayRow(day, beforeFirstUse = shown.isBeforeFirstUse(day), today = weeksBack == 0 && index == 0) { prayer ->
                    if (day.isMarkable(prayer)) {
                        scope.launch(Dispatchers.IO) {
                            if (day.stateOf(prayer) == LogState.PRAYED) core.unmark(day.date, prayer) else core.mark(day.date, prayer)
                        }
                    }
                }
            }
        }
    }
}

/** An outlined round arrow button for the previous or next week; [label] is read by TalkBack. */
@Composable
private fun WeekButton(label: Int, mirrored: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        // ic_back points to the start (right in Arabic): the previous week; turned around, the next one.
        Icon(
            painterResource(R.drawable.ic_back),
            contentDescription = stringResource(label),
            modifier = Modifier.size(20.dp).graphicsLayer { if (mirrored) rotationZ = 180f },
        )
    }
}

/** Per-Prayer counts as bars and the total, e.g. «الفجر ٥/٧» … «المجموع ٣١/٣٥». */
@Composable
private fun Summary(week: PrayerWeek) {
    val format = LocalDisplayFormatter.current
    Card {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val total = week.summary.total
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.log_summary),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(stringResource(R.string.log_total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(format.count(total.prayed, total.possible), style = MaterialTheme.typography.titleLarge)
            }
            Prayer.entries.forEach { prayer ->
                val count = week.summary.of(prayer)
                SummaryBar(stringResource(prayer.nameRes), count.prayed, count.possible, format.count(count.prayed, count.possible))
            }
        }
    }
}

@Composable
private fun SummaryBar(label: String, prayed: Int, possible: Int, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, Modifier.widthIn(min = 56.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(8.dp).background(colors.surfaceContainerHigh, RoundedCornerShape(4.dp))) {
            if (possible > 0 && prayed > 0) {
                Box(Modifier.fillMaxWidth(prayed.toFloat() / possible).height(8.dp).background(colors.primary, RoundedCornerShape(4.dp)))
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** The Prayer names over the columns of marks. TalkBack skips it: each cell already says its Prayer. */
@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp).clearAndSetSemantics {}) {
        Prayer.entries.forEach { prayer ->
            Text(
                stringResource(prayer.nameRes),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One Prayer Day: its Gregorian and Hijri date, then a mark per Prayer under the Prayer names. */
@Composable
private fun DayRow(day: PrayerDay, beforeFirstUse: Boolean, today: Boolean, onToggle: (Prayer) -> Unit) {
    val format = LocalDisplayFormatter.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (today) colors.primaryContainer else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(format.shortGregorianDate(day.date), style = MaterialTheme.typography.titleSmall)
            Text(format.hijriDate(day.hijriDate), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        if (beforeFirstUse) {
            Text(
                stringResource(R.string.log_before_first_use),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Prayer.entries.forEach { prayer -> PrayerCell(day, prayer, onToggle, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PrayerCell(day: PrayerDay, prayer: Prayer, onToggle: (Prayer) -> Unit, modifier: Modifier) {
    val state = day.stateOf(prayer)
    val name = stringResource(prayer.nameRes)
    val stateText = stringResource(
        when {
            state == LogState.NOT_YET_DUE -> R.string.log_not_yet_due
            !day.isEditable -> if (state == LogState.PRAYED) R.string.log_read_only_prayed else R.string.log_read_only
            state == LogState.PRAYED -> R.string.log_prayed
            else -> R.string.log_not_marked
        },
    )
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox, enabled = day.isMarkable(prayer)) { onToggle(prayer) }
            .semantics(mergeDescendants = true) {
                contentDescription = name
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center,
    ) {
        Mark(state)
    }
}

/** The same marks as Home: prayed is a filled circle with a tick, not marked an outline, not yet due a faint one. */
@Composable
private fun Mark(state: LogState) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        LogState.PRAYED -> Box(Modifier.size(28.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
            // The cell's state already says "Prayed"; TalkBack shouldn't also read "check mark".
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
        }
        LogState.NOT_MARKED -> Box(Modifier.size(28.dp).border(2.dp, colors.outline, CircleShape))
        LogState.NOT_YET_DUE -> Box(Modifier.size(28.dp).border(1.5.dp, colors.outline.copy(alpha = 0.5f), CircleShape))
    }
}
