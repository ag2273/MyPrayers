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
 * What the Today with Prayer Log widget (4×2) shows: how many of the display day's Prayers are marked, then its five
 * Prayers, each with its time and its Prayer Log state, the next one highlighted. Display only: the widget never
 * changes the log.
 *
 * Like every widget it follows the display day, which switches at midnight: after midnight and before Fajr it shows
 * the new date, whose Prayers are all not yet due, although the current Prayer Day is still the previous one.
 */
data class TodayLogContent(
    val date: WidgetDate,
    val rows: List<Row>,
    /** The Prayer Day the rows are of, which a tap on a column marks. */
    val logDate: java.time.LocalDate = java.time.LocalDate.MIN,
) {
    /** How many of the five are marked prayed. */
    val prayedCount: Int get() = rows.count { it.state == LogState.PRAYED }

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
                logDate = log.date,
            )
        }
    }
}

/**
 * What the Week Prayer Log widget (4×3) shows: the core's week grid (the last 7 Prayer Days, oldest first in reading
 * direction, × 5 Prayers), and the week's total, the same numbers as the Prayer Log screen's summary (days before
 * first use don't count). Display only.
 */
data class WeekLogContent(
    /** For each Prayer in day order, its seven cells, oldest first. */
    val rows: List<Row>,
    /** The week's prayed total, in the user's digits, e.g. `٢٦`. */
    val prayed: String,
    /** Out of how many, e.g. `٣٢`. */
    val possible: String,
    /** The seven Prayer Days, oldest first; the last is today. */
    val days: List<java.time.LocalDate> = emptyList(),
    /** Prayed out of possible, 0–1, for the 4×3's thin bar. */
    val share: Float = 0f,
) {
    enum class Cell { PRAYED, NOT_MARKED, NOT_YET_DUE, BEFORE_FIRST_USE }

    /** Rows are labelled Dhuhr every day: the grid spans a week, so Friday's Jumu'ah keeps Dhuhr's row. */
    data class Row(
        val prayer: Prayer,
        @param:StringRes val nameRes: Int,
        val cells: List<Cell>,
        /** This Prayer's count over the week, as the summary counts it. */
        val prayed: Int = 0,
        val possible: Int = 0,
    )

    companion object {
        fun of(week: PrayerWeek, formatter: DisplayFormatter): WeekLogContent {
            val total = week.summary.total
            return WeekLogContent(
                rows = Prayer.entries.map { prayer ->
                    val count = week.summary.of(prayer)
                    Row(prayer, prayerNameRes(prayer), week.days.map { day -> cellOf(week, day, prayer) }, count.prayed, count.possible)
                },
                prayed = formatter.number(total.prayed),
                possible = formatter.number(total.possible),
                days = week.days.map { it.date },
                share = if (total.possible == 0) 0f else total.prayed.toFloat() / total.possible,
            )
        }

        private fun cellOf(week: PrayerWeek, day: PrayerDay, prayer: Prayer): Cell = when {
            // A day before first use still shows what was marked on it in the app.
            week.isBeforeFirstUse(day) -> if (day.stateOf(prayer) == LogState.PRAYED) Cell.PRAYED else Cell.BEFORE_FIRST_USE
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
