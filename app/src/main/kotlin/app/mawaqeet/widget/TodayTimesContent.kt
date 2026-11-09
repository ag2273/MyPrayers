package app.mawaqeet.widget

import androidx.annotation.StringRes
import app.mawaqeet.R
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter

/**
 * What the Today's Prayer Times widget (4×2) shows, decided from the core's display day and the shared formatter
 * only: the date line and six columns in day order (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha). There is no
 * countdown. [TodayTimesWidget] just lays it out.
 */
data class TodayTimesContent(
    val date: WidgetDate,
    val columns: List<Column>,
) {
    data class Column(
        /** The Prayer's name (Jumu'ah for Friday's Dhuhr), or Sunrise. */
        @param:StringRes val nameRes: Int,
        /** Its time, in the user's digits and with no ص/م suffix (always `HH:mm`). */
        val time: String,
        /** Sunrise is shown, but muted and never highlighted. */
        val isSunrise: Boolean,
        /** The next Prayer, when it is on the displayed date. After Isha nothing is highlighted until midnight. */
        val isNext: Boolean,
    )

    companion object {
        fun of(day: DisplayDay, formatter: DisplayFormatter): TodayTimesContent {
            val nextToday = day.next.time.toLocalDate() == day.date
            val prayers = day.prayerTimes.map { prayerTime ->
                Column(
                    nameRes = prayerNameRes(prayerTime),
                    time = formatter.time(prayerTime.time),
                    isSunrise = false,
                    isNext = nextToday && prayerTime == day.next,
                )
            }
            val sunrise = Column(R.string.sunrise, formatter.time(day.sunrise), isSunrise = true, isNext = false)
            // Sunrise always falls between Fajr and Dhuhr.
            return TodayTimesContent(WidgetDate.of(day, formatter), listOf(prayers.first(), sunrise) + prayers.drop(1))
        }
    }
}

/**
 * What the Date strip widget (4×1) shows: the date line, then the next Prayer and its time on a second line. The
 * next Prayer is shown even when it is tomorrow's Fajr.
 */
data class DateStripContent(
    val date: WidgetDate,
    @param:StringRes val nextNameRes: Int,
    val nextTime: String,
) {
    companion object {
        fun of(day: DisplayDay, formatter: DisplayFormatter): DateStripContent = DateStripContent(
            date = WidgetDate.of(day, formatter),
            nextNameRes = prayerNameRes(day.next),
            nextTime = formatter.time(day.next.time),
        )
    }
}

/**
 * A widget's date line: the Gregorian date (weekday, day, month), then the Hijri date with our own month names and
 * the user's Hijri offset. When space is tight the Hijri date is the one that shortens.
 */
data class WidgetDate(val gregorian: String, val hijri: String) {
    companion object {
        fun of(day: DisplayDay, formatter: DisplayFormatter) = WidgetDate(
            gregorian = formatter.shortGregorianDate(day.date),
            hijri = formatter.hijriDate(day.hijriDate),
        )
    }
}
