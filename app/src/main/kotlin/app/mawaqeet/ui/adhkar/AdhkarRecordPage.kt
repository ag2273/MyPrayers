package app.mawaqeet.ui.adhkar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarMark
import app.mawaqeet.core.AdhkarRecordDay
import app.mawaqeet.core.AdhkarSlot
import app.mawaqeet.core.AdhkarWeek
import app.mawaqeet.core.LogState
import app.mawaqeet.core.Prayer
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.SubPage
import app.mawaqeet.ui.log.Mark
import app.mawaqeet.ui.log.WeekButton
import app.mawaqeet.ui.settings.nameRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** The column weights: the morning, the evening, then the five after-prayer marks together. */
private const val DAILY_WEIGHT = 1f
private const val AFTER_PRAYER_WEIGHT = 2.6f

/**
 * The Adhkar Record (#74): a week of Prayer Days, newest first, each with a mark for the morning, the evening and
 * after each of the five Prayers, like the Prayer Log's week. Browsable week by week; read-only. Only marks: no
 * streaks, badges or scores.
 */
@Composable
internal fun AdhkarRecordPage(adhkar: Adhkar, changes: StateFlow<Int>, onBack: () -> Unit) {
    var weeksBack by rememberSaveable { mutableStateOf(0) }
    val version by changes.collectAsState()
    val week by produceState<AdhkarWeek?>(null, adhkar, version, weeksBack) {
        value = withContext(Dispatchers.IO) {
            if (weeksBack == 0) adhkar.week() else adhkar.week(adhkar.week().days.last().date.minusDays(7L * weeksBack))
        }
    }
    val format = LocalDisplayFormatter.current
    SubPage(stringResource(R.string.adhkar_record), onBack) {
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
        val shown = week ?: return@SubPage
        Card {
            Header()
            shown.days.asReversed().forEachIndexed { index, day ->
                if (index > 0) RowDivider()
                DayRow(day, today = weeksBack == 0 && index == 0)
            }
        }
    }
}

/** The set names over the columns of marks. TalkBack skips it: each mark already says its set. */
@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp).clearAndSetSemantics {}) {
        HeaderText(R.string.adhkar_record_morning, DAILY_WEIGHT)
        HeaderText(R.string.adhkar_record_evening, DAILY_WEIGHT)
        HeaderText(R.string.adhkar_record_after_prayers, AFTER_PRAYER_WEIGHT)
    }
}

@Composable
private fun RowScope.HeaderText(text: Int, weight: Float) {
    Text(
        stringResource(text),
        Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** One Prayer Day: its Gregorian and Hijri date, then the morning and evening marks and five small after-prayer ones. */
@Composable
private fun DayRow(day: AdhkarRecordDay, today: Boolean) {
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Cell(day, AdhkarSlot.MORNING, 28.dp, Modifier.weight(DAILY_WEIGHT))
            Cell(day, AdhkarSlot.EVENING, 28.dp, Modifier.weight(DAILY_WEIGHT))
            Row(Modifier.weight(AFTER_PRAYER_WEIGHT)) {
                Prayer.entries.forEach { prayer -> Cell(day, AdhkarSlot.afterPrayer(prayer), 18.dp, Modifier.weight(1f)) }
            }
        }
    }
}

/** One mark, read by TalkBack as the set's name («أذكار بعد صلاة الفجر») and whether it was completed. */
@Composable
private fun Cell(day: AdhkarRecordDay, slot: AdhkarSlot, size: Dp, modifier: Modifier) {
    val mark = day.of(slot)
    val name = slot.prayer?.let { stringResource(R.string.adhkar_after_prayer_name, stringResource(it.nameRes)) }
        ?: stringResource(slot.set.nameRes)
    val state = stringResource(
        when (mark) {
            AdhkarMark.COMPLETED -> R.string.adhkar_record_completed
            AdhkarMark.NOT_COMPLETED -> R.string.adhkar_record_not_completed
            AdhkarMark.NOT_YET_DUE -> R.string.adhkar_record_not_yet_due
            AdhkarMark.BEFORE_FIRST_USE -> R.string.adhkar_record_before_first_use
        },
    )
    Box(
        modifier.heightIn(min = 40.dp).semantics(mergeDescendants = true) {
            contentDescription = name
            stateDescription = state
        },
        contentAlignment = Alignment.Center,
    ) {
        when (mark) {
            AdhkarMark.COMPLETED -> Mark(LogState.PRAYED, size)
            AdhkarMark.NOT_COMPLETED -> Mark(LogState.NOT_MARKED, size)
            AdhkarMark.NOT_YET_DUE -> Mark(LogState.NOT_YET_DUE, size)
            // Before first use nothing could have been read: no mark at all, never a miss.
            AdhkarMark.BEFORE_FIRST_USE -> Box(Modifier.size(size))
        }
    }
}
