package app.mawaqeet.core

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * A date in the Umm al-Qura Hijri calendar, as plain numbers ([month] 1 = Muharram … 12 = Dhu al-Hijjah).
 * Show it with [DisplayFormatter.hijriDate], never with a platform Islamic-calendar formatter.
 */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
    companion object {
        /** The Umm al-Qura date of [date] shifted by [offsetDays] (the user's moon-sighting correction). */
        fun of(date: LocalDate, offsetDays: Int = 0): HijriDate {
            val hijrah = HijrahDate.from(date.plusDays(offsetDays.toLong()))
            return HijriDate(
                year = hijrah.get(ChronoField.YEAR),
                month = hijrah.get(ChronoField.MONTH_OF_YEAR),
                day = hijrah.get(ChronoField.DAY_OF_MONTH),
            )
        }
    }
}
