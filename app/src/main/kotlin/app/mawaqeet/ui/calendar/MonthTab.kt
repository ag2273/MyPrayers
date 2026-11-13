package app.mawaqeet.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.CalendarDay
import app.mawaqeet.core.HijriCalendar
import app.mawaqeet.core.HijriMonth
import app.mawaqeet.core.MonthEntry
import app.mawaqeet.core.SunnahFast
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.SectionTitle
import app.mawaqeet.ui.log.WeekButton
import java.time.DayOfWeek
import java.time.LocalDate

/** A Saturday, the first column of the grid; the six days after it head the other columns. */
private val FIRST_SATURDAY: LocalDate = LocalDate.of(2026, 11, 14)

/**
 * The «الشهر» tab (#94): a Hijri month of [calendar] as a grid from Saturday, each cell the Hijri day over the
 * Gregorian day, with a dot on the Monday/Thursday fast, the white days tinted, Occasions highlighted and [today]
 * ringed; previous/next month buttons, a legend, and «في هذا الشهر». Opens on today's Hijri month.
 */
@Composable
internal fun MonthTab(calendar: HijriCalendar, today: LocalDate) {
    val format = LocalDisplayFormatter.current
    // The month as year × 12 + (month − 1), so it survives rotation as a plain Int.
    var shown by rememberSaveable(calendar, today) {
        mutableIntStateOf(HijriMonth.of(calendar.day(today).hijri).let { it.year * 12 + it.month - 1 })
    }
    val month = HijriMonth(shown / 12, shown % 12 + 1)
    val days = remember(calendar, month) { calendar.month(month) }
    val entries = remember(calendar, month) { calendar.inMonth(month) }

    Card {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WeekButton(R.string.calendar_previous_month, mirrored = false, enabled = true) { shown -= 1 }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        format.hijriMonth(month),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        format.monthSpan(days.first().date, days.last().date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                WeekButton(R.string.calendar_next_month, mirrored = true, enabled = true) { shown += 1 }
            }
            WeekdayHeads()
            weeks(days).forEach { week -> WeekRow(week, today) }
            // On the card, where the swatches show as they do on the cells.
            Legend()
        }
    }
    if (entries.isNotEmpty()) {
        SectionTitle(stringResource(R.string.calendar_in_this_month))
        Card {
            entries.forEachIndexed { i, entry ->
                if (i > 0) RowDivider()
                EntryRow(entry)
            }
        }
    }
}

/** The month's days in rows of seven from Saturday, with nulls before the 1st and after the last day. */
private fun weeks(days: List<CalendarDay>): List<List<CalendarDay?>> {
    val leading = (days.first().date.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
    val cells: List<CalendarDay?> = List(leading) { null } + days
    return cells.chunked(7).map { it + List(7 - it.size) { null } }
}

@Composable
private fun WeekdayHeads() {
    val format = LocalDisplayFormatter.current
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { i ->
            val day = FIRST_SATURDAY.plusDays(i.toLong())
            val name = format.weekday(day)
            Text(
                format.narrowWeekday(day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { contentDescription = name },
            )
        }
    }
}

@Composable
private fun WeekRow(week: List<CalendarDay?>, today: LocalDate) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        week.forEach { day ->
            Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                if (day != null) DayCell(day, today = day.date == today)
            }
        }
    }
}

private val CellShape = RoundedCornerShape(10.dp)

@Composable
private fun DayCell(day: CalendarDay, today: Boolean) {
    val format = LocalDisplayFormatter.current
    val colors = MaterialTheme.colorScheme
    val occasion = day.occasions.isNotEmpty()
    val white = SunnahFast.WHITE_DAYS in day.fasts
    val (background, content) = when {
        occasion -> colors.secondaryContainer to colors.onSecondaryContainer
        white -> colors.primaryContainer to colors.onPrimaryContainer
        else -> Color.Transparent to colors.onSurface
    }
    val separator = stringResource(R.string.calendar_label_separator)
    val label = buildList {
        add(format.hijriDate(day.hijri))
        add(format.gregorianDate(day.date))
        day.occasions.forEach { add(stringResource(it.label)) }
        day.fasts.sortedBy { it.ordinal }.forEach { add(stringResource(it.label)) }
        if (today) add(stringResource(R.string.calendar_today))
    }.joinToString(separator)
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = 52.dp)
            .background(background, CellShape)
            .then(if (today) Modifier.border(2.dp, colors.primary, CellShape) else Modifier)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            format.number(day.hijri.day),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (occasion || today) FontWeight.Bold else FontWeight.Normal,
            color = content,
        )
        Text(
            format.number(day.date.dayOfMonth),
            style = MaterialTheme.typography.labelSmall,
            color = if (occasion || white) content else colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        FastDot(visible = SunnahFast.MONDAY_THURSDAY in day.fasts)
    }
}

@Composable
private fun FastDot(visible: Boolean) {
    val color = if (visible) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(Modifier.size(5.dp).background(color, CircleShape))
}

/** What each mark means: the dot, the white days' tint, an Occasion's highlight and today's ring. */
@Composable
private fun Legend() {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(stringResource(R.string.fast_monday_thursday)) { FastDot(visible = true) }
        LegendItem(stringResource(R.string.fast_white_days)) { Swatch(Modifier.background(colors.primaryContainer, CellShape)) }
        LegendItem(stringResource(R.string.calendar_legend_occasion)) { Swatch(Modifier.background(colors.secondaryContainer, CellShape)) }
        LegendItem(stringResource(R.string.calendar_today)) { Swatch(Modifier.border(2.dp, colors.primary, CellShape)) }
    }
}

@Composable
private fun Swatch(modifier: Modifier) = Box(Modifier.size(14.dp).then(modifier))

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryRow(entry: MonthEntry) {
    val format = LocalDisplayFormatter.current
    val (first, last) = entry.first to entry.last
    val hijri = if (first == last) format.hijriDayMonth(first.hijri) else "${format.number(first.hijri.day)} – ${format.hijriDayMonth(last.hijri)}"
    val gregorian = if (first == last) format.dayMonth(first.date) else format.dayRange(first.date, last.date)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(
                when (entry) {
                    is MonthEntry.OccasionDays -> entry.occasion.label
                    is MonthEntry.FastDays -> entry.fast.label
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(hijri, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(gregorian, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
