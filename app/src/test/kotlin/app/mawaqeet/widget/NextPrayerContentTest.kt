package app.mawaqeet.widget

import app.mawaqeet.R
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.HijriDate
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Plain JVM tests of what the Next Prayer widget shows, from the core's display day and the shared formatter. */
class NextPrayerContentTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val arabic = DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC)
    private val english = DisplayFormatter(Locale.ENGLISH, DigitStyle.WESTERN)

    private fun at(date: LocalDate, time: String) = ZonedDateTime.of(date, LocalTime.parse(time), riyadh)

    /**
     * Makkah, Umm al-Qura (published times): Fajr 05:09, Sunrise 06:27, Dhuhr 12:04, Asr 15:17, Maghrib 17:41,
     * Isha 19:11. The same clock times are used for any [date] here; only the weekday matters.
     */
    private fun makkahDay(date: LocalDate, next: PrayerTime): DisplayDay {
        val times = listOf(
            Prayer.FAJR to "05:09", Prayer.DHUHR to "12:04", Prayer.ASR to "15:17",
            Prayer.MAGHRIB to "17:41", Prayer.ISHA to "19:11",
        ).map { (prayer, time) -> PrayerTime(prayer, at(date, time)) }
        return DisplayDay(date, times, at(date, "06:27"), next, HijriDate.of(date))
    }

    private val sunday = LocalDate.parse("2026-11-08")

    @Test
    fun `before Dhuhr it shows Dhuhr, its time in the chosen digits and how long until it`() {
        val day = makkahDay(sunday, PrayerTime(Prayer.DHUHR, at(sunday, "12:04")))

        val content = NextPrayerContent.of(day, at(sunday, "10:30:15").toInstant(), arabic)

        assertEquals(R.string.prayer_dhuhr, content.nameRes)
        assertEquals("١٢:٠٤", content.time)
        assertEquals(Duration.parse("PT1H33M45S"), content.untilNext)
    }

    @Test
    fun `English with Western digits shows the time as 12-04`() {
        val day = makkahDay(sunday, PrayerTime(Prayer.DHUHR, at(sunday, "12:04")))

        assertEquals("12:04", NextPrayerContent.of(day, at(sunday, "10:30").toInstant(), english).time)
    }

    @Test
    fun `on a Friday the next Dhuhr is named Jumu'ah`() {
        val friday = LocalDate.parse("2026-11-13")
        val day = makkahDay(friday, PrayerTime(Prayer.DHUHR, at(friday, "12:04")))

        assertEquals(R.string.prayer_jumuah, NextPrayerContent.of(day, at(friday, "09:00").toInstant(), arabic).nameRes)
    }

    @Test
    fun `after Isha it counts down to tomorrow's Fajr`() {
        val tomorrowFajr = PrayerTime(Prayer.FAJR, at(sunday.plusDays(1), "05:09"))
        val day = makkahDay(sunday, tomorrowFajr)

        val content = NextPrayerContent.of(day, at(sunday, "20:00").toInstant(), english)

        assertEquals(R.string.prayer_fajr, content.nameRes)
        assertEquals("05:09", content.time)
        assertEquals(Duration.parse("PT9H9M"), content.untilNext)
    }

    @Test
    fun `a widget rendered just after the Prayer Time never counts below zero while the next refresh is on its way`() {
        val day = makkahDay(sunday, PrayerTime(Prayer.DHUHR, at(sunday, "12:04")))

        assertEquals(Duration.ZERO, NextPrayerContent.of(day, at(sunday, "12:04:02").toInstant(), arabic).untilNext)
    }
}
