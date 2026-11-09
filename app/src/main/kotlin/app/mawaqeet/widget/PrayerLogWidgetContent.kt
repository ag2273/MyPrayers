package app.mawaqeet.widget

import androidx.annotation.StringRes
import app.mawaqeet.R
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.LogState
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerDay
import app.mawaqeet.core.PrayerWeek

/**
 * What the Today with Prayer Log widget (4×3) shows: the display day's date line, then its five Prayers, each with
 * its time and its Prayer Log state, the next one highlighted, and Sunrise as a footer. Display only: the widget
 * never changes the log.
 *
 * Like every widget it follows the display day, which switches at midnight: after midnight and before Fajr it shows
 * the new date, whose Prayers are all not yet due, although the current Prayer Day is still the previous one.
 */
data class TodayLogContent(
    val date: WidgetDate,
    val rows: List<Row>,
    /** Sunrise's time, for the footer line. */
    val sunrise: String,
) {
    data class Row(
        val prayer: Prayer,
        @param:StringRes val nameRes: Int,
        val time: String,
        val state: LogState,
        val isNext: Boolean,
    )

    companion object {
        /** [log] is the Prayer Day with [day]'s date (`core.prayerDay(day.date)`). */
        fun of(day: DisplayDay, log: PrayerDay, formatter: DisplayFormatter): TodayLogContent {
            require(log.date == day.date) { "The log day ${log.date} is not the display day ${day.date}" }
            val nextToday = day.next.time.toLocalDate() == day.date
            return TodayLogContent(
                date = WidgetDate.of(day, formatter),
                rows = day.prayerTimes.map { prayerTime ->
                    Row(
                        prayer = prayerTime.prayer,
                        nameRes = prayerNameRes(prayerTime),
                        time = formatter.time(prayerTime.time),
                        state = log.stateOf(prayerTime.prayer),
                        isNext = nextToday && prayerTime == day.next,
                    )
                },
                sunrise = formatter.time(day.sunrise),
            )
        }
    }
}

/**
 * What the Week Prayer Log widget (4×3) shows: the core's week grid (the last 7 Prayer Days, oldest first in reading
 * direction, × 5 Prayers), today's column highlighted, and the week's total, the same numbers as the Prayer Log
 * screen's summary (days before first use don't count). Display only.
 */
data class WeekLogContent(
    /** One letter per Prayer Day, oldest first; the last is today. */
    val weekdays: List<String>,
    /** For each Prayer in day order, its seven cells, oldest first. */
    val rows: List<Row>,
    /** The week's total, e.g. `٣١/٣٥`. */
    val total: String,
) {
    enum class Cell { PRAYED, NOT_MARKED, NOT_YET_DUE, BEFORE_FIRST_USE }

    /** Rows are labelled Dhuhr every day: the grid spans a week, so Friday's Jumu'ah keeps Dhuhr's row. */
    data class Row(val prayer: Prayer, @param:StringRes val nameRes: Int, val cells: List<Cell>)

    companion object {
        fun of(week: PrayerWeek, formatter: DisplayFormatter): WeekLogContent = WeekLogContent(
            weekdays = week.days.map { formatter.narrowWeekday(it.date) },
            rows = Prayer.entries.map { prayer ->
                Row(prayer, prayerNameRes(prayer), week.days.map { day -> cellOf(week, day, prayer) })
            },
            total = week.summary.total.let { formatter.count(it.prayed, it.possible) },
        )

        private fun cellOf(week: PrayerWeek, day: PrayerDay, prayer: Prayer): Cell = when {
            week.isBeforeFirstUse(day) -> Cell.BEFORE_FIRST_USE
            else -> when (day.stateOf(prayer)) {
                LogState.PRAYED -> Cell.PRAYED
                LogState.NOT_MARKED -> Cell.NOT_MARKED
                LogState.NOT_YET_DUE -> Cell.NOT_YET_DUE
            }
        }
    }
}

/** A Prayer's own name, never Jumu'ah. */
@StringRes
fun prayerNameRes(prayer: Prayer): Int = when (prayer) {
    Prayer.FAJR -> R.string.prayer_fajr
    Prayer.DHUHR -> R.string.prayer_dhuhr
    Prayer.ASR -> R.string.prayer_asr
    Prayer.MAGHRIB -> R.string.prayer_maghrib
    Prayer.ISHA -> R.string.prayer_isha
}
