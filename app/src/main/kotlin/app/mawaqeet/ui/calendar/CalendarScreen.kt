package app.mawaqeet.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.HijriCalendar
import app.mawaqeet.core.Occasion
import app.mawaqeet.core.SunnahFast
import app.mawaqeet.core.Upcoming
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.SectionTitle
import app.mawaqeet.ui.components.Segment
import app.mawaqeet.ui.components.SegmentedChoice
import app.mawaqeet.ui.components.SubPage
import java.time.LocalDate

/** The Calendar screen's tabs, «القادم» first, then the month grid «الشهر». */
enum class CalendarTab(@StringRes val label: Int) {
    UPCOMING(R.string.calendar_upcoming),
    MONTH(R.string.calendar_month),
}

/**
 * The Hijri Calendar screen (not a tab), opened from «التقويم» on the Tools Row and from the Hijri date on Today:
 * the coming Occasions and Sunnah Fasts of [calendar] from [today] (the calendar date, which moves on at midnight, not
 * the display day) or a Hijri month as a grid, and the note that the dates follow Umm al-Qura with the user's offset.
 */
@Composable
fun CalendarScreen(calendar: HijriCalendar, today: LocalDate, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(CalendarTab.UPCOMING) }
    SubPage(stringResource(R.string.tool_calendar), onBack) {
        if (CalendarTab.entries.size > 1) {
            SegmentedChoice(CalendarTab.entries.map { Segment(stringResource(it.label), it == tab) { tab = it } })
        } else {
            SectionTitle(stringResource(tab.label))
        }
        when (tab) {
            CalendarTab.UPCOMING -> UpcomingList(remember(calendar, today) { calendar.upcoming(today) })
            CalendarTab.MONTH -> MonthTab(calendar, today)
        }
        Text(
            stringResource(R.string.calendar_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun UpcomingList(entries: List<Upcoming>) {
    Card {
        entries.forEachIndexed { i, entry ->
            if (i > 0) RowDivider()
            UpcomingRow(entry)
        }
    }
}

@Composable
private fun UpcomingRow(entry: Upcoming) {
    val format = LocalDisplayFormatter.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(entry.label), style = MaterialTheme.typography.titleMedium)
            Text(
                format.hijriDate(entry.first.hijri),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                format.gregorianDate(entry.first.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            daysAway(entry.daysAway),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** «اليوم», «غدًا», or «بعد ٤ أيام». */
@Composable
private fun daysAway(days: Int): String = when (days) {
    0 -> stringResource(R.string.calendar_today)
    1 -> stringResource(R.string.calendar_tomorrow)
    else -> pluralStringResource(R.plurals.calendar_in_days, days, LocalDisplayFormatter.current.number(days))
}

private val Upcoming.label: Int
    get() = when (this) {
        is Upcoming.OccasionDays -> occasion.label
        is Upcoming.FastDays -> fast.label
    }

/** The Occasion's name on the calendar. */
@get:StringRes
val Occasion.label: Int
    get() = when (this) {
        Occasion.HIJRI_NEW_YEAR -> R.string.occasion_hijri_new_year
        Occasion.TASUA -> R.string.occasion_tasua
        Occasion.ASHURA -> R.string.occasion_ashura
        Occasion.MAWLID -> R.string.occasion_mawlid
        Occasion.ISRA_AND_MIRAJ -> R.string.occasion_isra_and_miraj
        Occasion.RAMADAN_START -> R.string.occasion_ramadan_start
        Occasion.LAST_TEN_NIGHTS -> R.string.occasion_last_ten_nights
        Occasion.EID_AL_FITR -> R.string.occasion_eid_al_fitr
        Occasion.FIRST_TEN_OF_DHU_AL_HIJJAH -> R.string.occasion_first_ten_of_dhu_al_hijjah
        Occasion.ARAFAH -> R.string.occasion_arafah
        Occasion.EID_AL_ADHA -> R.string.occasion_eid_al_adha
        Occasion.TASHREEQ -> R.string.occasion_tashreeq
    }

/** The Sunnah Fast's name, as on its reminder switch. */
@get:StringRes
val SunnahFast.label: Int
    get() = when (this) {
        SunnahFast.MONDAY_THURSDAY -> R.string.fast_monday_thursday
        SunnahFast.WHITE_DAYS -> R.string.fast_white_days
        SunnahFast.ARAFAH -> R.string.fast_arafah
        SunnahFast.ASHURA -> R.string.fast_ashura
        SunnahFast.SIX_OF_SHAWWAL -> R.string.fast_six_of_shawwal
    }
