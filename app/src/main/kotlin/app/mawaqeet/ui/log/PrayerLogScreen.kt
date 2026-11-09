package app.mawaqeet.ui.log

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
fun PrayerLogScreen(core: PrayerCore, logChanges: StateFlow<Int>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
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
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.prayer_log),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { weeksBack += 1 }) { Text(stringResource(R.string.log_previous_week)) }
                Text(
                    stringResource(if (weeksBack == 0) R.string.log_this_week else R.string.log_earlier_week),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { weeksBack -= 1 }, enabled = weeksBack > 0) {
                    Text(stringResource(R.string.log_next_week))
                }
            }
            val shown = week ?: return@Column
            Summary(shown)
            if (shown.days.none { it.isEditable }) {
                Text(
                    stringResource(R.string.log_week_read_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shown.days.asReversed().forEach { day ->
                DayRow(day, beforeFirstUse = shown.isBeforeFirstUse(day)) { prayer ->
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

/** Per-Prayer counts and the total, e.g. «الفجر ٥/٧» … «المجموع ٣١/٣٥». */
@Composable
private fun Summary(week: PrayerWeek) {
    val format = LocalDisplayFormatter.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.log_summary),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Prayer.entries.forEach { prayer ->
            val count = week.summary.of(prayer)
            SummaryLine(stringResource(prayer.nameRes), format.count(count.prayed, count.possible))
        }
        val total = week.summary.total
        SummaryLine(stringResource(R.string.log_total), format.count(total.prayed, total.possible), bold = true)
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = weight)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = weight)
    }
}

/** One Prayer Day: its Gregorian then Hijri date, then a mark per Prayer with the Prayer's name under it. */
@Composable
private fun DayRow(day: PrayerDay, beforeFirstUse: Boolean, onToggle: (Prayer) -> Unit) {
    val format = LocalDisplayFormatter.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(format.shortGregorianDate(day.date), style = MaterialTheme.typography.titleSmall)
            Text(format.hijriDate(day.hijriDate), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (beforeFirstUse) {
                Text(stringResource(R.string.log_before_first_use), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Prayer.entries.forEach { prayer -> PrayerCell(day, prayer, onToggle) }
        }
    }
}

@Composable
private fun PrayerCell(day: PrayerDay, prayer: Prayer, onToggle: (Prayer) -> Unit) {
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
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Checkbox, enabled = day.isMarkable(prayer)) { onToggle(prayer) }
            .semantics(mergeDescendants = true) {
                contentDescription = name
                stateDescription = stateText
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mark(state)
        Text(name, style = MaterialTheme.typography.labelSmall)
    }
}

/** The same marks as Home: prayed is a filled circle with a tick, not marked an outline, not yet due a faint one. */
@Composable
private fun Mark(state: LogState) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        LogState.PRAYED -> Box(Modifier.size(28.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
            // The row's state already says "Prayed"; TalkBack shouldn't also read "check mark".
            Text("✓", color = colors.onPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
        }
        LogState.NOT_MARKED -> Box(Modifier.size(28.dp).border(2.dp, colors.primary, CircleShape).background(Color.Transparent))
        LogState.NOT_YET_DUE -> Box(Modifier.size(28.dp).border(1.dp, colors.outlineVariant, CircleShape))
    }
}
