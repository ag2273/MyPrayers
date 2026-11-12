package app.mawaqeet.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** One Prayer and the moment it is due, in the location's time zone. */
data class PrayerTime(
    val prayer: Prayer,
    val time: ZonedDateTime,
) {
    /** Dhuhr on a Friday is named and announced as Jumu'ah (الجمعة). */
    val isJumuah: Boolean
        get() = prayer == Prayer.DHUHR && time.dayOfWeek == DayOfWeek.FRIDAY
}

/**
 * What the home screen and widgets show: the calendar date at the location (switching at local midnight)
 * with its Prayer Times and Sunrise.
 */
data class DisplayDay(
    val date: LocalDate,
    /** The five Prayer Times of [date], in day order. */
    val prayerTimes: List<PrayerTime>,
    val sunrise: ZonedDateTime,
    /** The next Prayer whose time has not yet arrived. Never Sunrise. */
    val next: PrayerTime,
    /** [date] in the Hijri calendar, with the user's Hijri offset applied. */
    val hijriDate: HijriDate,
    /** Non-null when a high-latitude rule set [date]'s Fajr or Isha: Home shows a short note. */
    val highLatitude: HighLatitudeAdjustment? = null,
    /**
     * The day before [date]'s Isha, from that day's own Prayer Times: before Fajr the previous Stop. The core always
     * sets it; the default (today's Isha a day earlier) is for days built by hand in tests.
     */
    val previousIsha: PrayerTime = prayerTimes.last().let { it.copy(time = it.time.minusDays(1)) },
) {
    /** Whether a high-latitude rule was applied to [date]'s Prayer Times. */
    val highLatitudeRuleApplied: Boolean
        get() = highLatitude != null

    /** Whether [date] is a Friday, so its Dhuhr is Jumu'ah. */
    val isJumuah: Boolean
        get() = date.dayOfWeek == DayOfWeek.FRIDAY

    fun timeOf(prayer: Prayer): ZonedDateTime = prayerTimes.first { it.prayer == prayer }.time
}
