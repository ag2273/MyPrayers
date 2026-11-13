package app.mawaqeet.widget

import androidx.annotation.StringRes
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant

/**
 * What the Today's Prayer Times widget shows, decided from the core's display day and the shared formatter only: the
 * date and the five Prayers' columns in day order (no Sunrise), the ones already passed quieter, and where the day
 * stands for the 4×2's day path ([dayPath]). There is no countdown. [TodayTimesWidget] just lays it out.
 */
data class TodayTimesContent(
    val date: WidgetDate,
    val columns: List<Column>,
    /** The next Prayer's index among the five, 5 after Isha, and how far it is from the previous one. */
    val nextIndex: Int = columns.indexOfFirst { it.isNext }.let { if (it < 0) columns.size else it },
    val fraction: Float = 0f,
) {
    data class Column(
        /** The Prayer's name (Jumu'ah for Friday's Dhuhr). */
        @param:StringRes val nameRes: Int,
        /** Its time, in the user's digits and with no ص/م suffix (always `HH:mm`). */
        val time: String,
        /** The next Prayer, when it is on the displayed date. After Isha nothing is highlighted until Fajr, when the next Prayer Day begins. */
        val isNext: Boolean,
        /** Its time has passed today. */
        val isPast: Boolean = false,
    )

    companion object {
        fun of(day: DisplayDay, formatter: DisplayFormatter, now: Instant = Instant.now()): TodayTimesContent {
            val nextToday = day.next.time.toLocalDate() == day.date
            val nextIndex = if (nextToday) day.prayerTimes.indexOf(day.next) else day.prayerTimes.size
            val prayers = day.prayerTimes.mapIndexed { i, prayerTime ->
                Column(
                    nameRes = prayerNameRes(prayerTime),
                    time = formatter.time(prayerTime.time),
                    isNext = nextToday && prayerTime == day.next,
                    isPast = i < nextIndex,
                )
            }
            val fraction = if (nextIndex in 1 until day.prayerTimes.size) {
                val from = day.prayerTimes[nextIndex - 1].time.toInstant()
                val to = day.prayerTimes[nextIndex].time.toInstant()
                (Duration.between(from, now).toMillis().toFloat() / Duration.between(from, to).toMillis().coerceAtLeast(1)).coerceIn(0f, 1f)
            } else {
                0f
            }
            return TodayTimesContent(WidgetDate.of(day, formatter), prayers, nextIndex, fraction)
        }
    }
}

/**
 * What the Date strip widget (4×1) shows, in three parts: the weekday over the day and month, the Hijri day and month
 * over its year, and the next Prayer with its time. The next Prayer is shown even when it is tomorrow's Fajr.
 */
data class DateStripContent(
    val date: WidgetDate,
    @param:StringRes val nextNameRes: Int,
    val nextTime: String,
    val weekday: String = "",
    val dayMonth: String = "",
    val hijriDayMonth: String = "",
    val hijriYear: String = "",
    val isFriday: Boolean = false,
) {
    companion object {
        fun of(day: DisplayDay, formatter: DisplayFormatter): DateStripContent = DateStripContent(
            date = WidgetDate.of(day, formatter),
            nextNameRes = prayerNameRes(day.next),
            nextTime = formatter.time(day.next.time),
            weekday = formatter.weekday(day.date),
            dayMonth = formatter.dayMonth(day.date),
            hijriDayMonth = formatter.hijriDayMonth(day.hijriDate),
            hijriYear = formatter.hijriYear(day.hijriDate),
            isFriday = day.date.dayOfWeek == DayOfWeek.FRIDAY,
        )
    }
}

/**
 * A widget's date line: the Gregorian date (weekday, day, month), then the Hijri date with our own month names and
 * the user's Hijri offset. When space is tight the Hijri date is the one that shortens. [hijriShort] is the Hijri day
 * and month without the year, for Today's Prayer Times header.
 */
data class WidgetDate(val gregorian: String, val hijri: String) {
    var hijriShort: String = hijri
        private set

    companion object {
        fun of(day: DisplayDay, formatter: DisplayFormatter) = WidgetDate(
            gregorian = formatter.shortGregorianDate(day.date),
            hijri = formatter.hijriDate(day.hijriDate),
        ).apply { hijriShort = formatter.hijriDayMonth(day.hijriDate) }
    }
}
