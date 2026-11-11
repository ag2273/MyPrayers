package app.mawaqeet.widget

import app.mawaqeet.R
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.HijriDate
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Plain JVM tests of what every Clock Widget shows: its two Stops, the bar, the countdown, the clock and the dates. */
class ClockContentTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val arabic = DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC)
    private val english = DisplayFormatter(Locale.ENGLISH, DigitStyle.WESTERN)

    private fun at(date: LocalDate, time: String) = ZonedDateTime.of(date, LocalTime.parse(time), riyadh)

    private val sunday = LocalDate.parse("2026-11-08")

    /**
     * Makkah, Umm al-Qura (published times): Fajr 05:09, Sunrise 06:27, Dhuhr 12:04, Asr 15:17, Maghrib 17:41,
     * Isha 19:11, with the next Prayer as the core decides it at [now]. The same clock times serve any [date].
     */
    private fun makkahDay(date: LocalDate, now: ZonedDateTime, hijriOffset: Int = 0): DisplayDay {
        val times = listOf(
            Prayer.FAJR to "05:09", Prayer.DHUHR to "12:04", Prayer.ASR to "15:17",
            Prayer.MAGHRIB to "17:41", Prayer.ISHA to "19:11",
        ).map { (prayer, time) -> PrayerTime(prayer, at(date, time)) }
        val next = times.firstOrNull { it.time.isAfter(now) } ?: PrayerTime(Prayer.FAJR, at(date.plusDays(1), "05:09"))
        return DisplayDay(date, times, at(date, "06:27"), next, HijriDate.of(date, hijriOffset))
    }

    private fun content(now: ZonedDateTime, formatter: DisplayFormatter = english, twentyFourHour: Boolean = true, hijriOffset: Int = 0) =
        ClockContent.of(makkahDay(now.toLocalDate(), now, hijriOffset), now, formatter, twentyFourHour)

    @Test
    fun `before Fajr the interval runs from yesterday's Isha to Fajr`() {
        val c = content(at(sunday, "03:00"))

        assertEquals(R.string.prayer_isha, c.previous.nameRes)
        assertEquals("19:11", c.previous.time)
        assertEquals(R.string.prayer_fajr, c.next.nameRes)
        assertEquals("05:09", c.next.time)
        assertEquals("2h 9m", c.countdown)
    }

    @Test
    fun `between Fajr and Sunrise the next Stop is Sunrise, which is not a Prayer`() {
        val c = content(at(sunday, "06:00"))

        assertEquals(R.string.prayer_fajr, c.previous.nameRes)
        assertEquals(R.string.sunrise, c.next.nameRes)
        assertEquals("06:27", c.next.time)
        assertNull(c.next.prayer)
        assertEquals("27m", c.countdown)
    }

    @Test
    fun `after Sunrise the interval runs from Sunrise to Dhuhr`() {
        val c = content(at(sunday, "10:30:15"), arabic)

        assertEquals(R.string.sunrise, c.previous.nameRes)
        assertEquals("٠٦:٢٧", c.previous.time)
        assertEquals(R.string.prayer_dhuhr, c.next.nameRes)
        assertEquals(Prayer.DHUHR, c.next.prayer)
        assertEquals("١٢:٠٤", c.next.time)
        // 1:33:45 left reads as 1 h 34 m: the minute that has started counts, so it never shows 0 before the Stop.
        assertEquals("١ س ٣٤ د", c.countdown)
    }

    @Test
    fun `on a Friday the Dhuhr Stop is named Jumu'ah`() {
        val friday = LocalDate.parse("2026-11-13")

        assertEquals(R.string.prayer_jumuah, content(at(friday, "09:00")).next.nameRes)
        assertEquals(R.string.prayer_jumuah, content(at(friday, "13:00")).previous.nameRes)
    }

    @Test
    fun `after Isha the interval runs to tomorrow's Fajr`() {
        val c = content(at(sunday, "20:00"))

        assertEquals(R.string.prayer_isha, c.previous.nameRes)
        assertEquals(R.string.prayer_fajr, c.next.nameRes)
        assertEquals("05:09", c.next.time)
        assertEquals("9h 9m", c.countdown)
    }

    @Test
    fun `the bar is the share of the interval gone, from 0 at the previous Stop to nearly 1 just before the next`() {
        // Dhuhr 12:04 to Asr 15:17 is 193 minutes.
        assertEquals(0f, content(at(sunday, "12:04")).elapsed, 0.001f)
        assertEquals(0.5f, content(at(sunday, "13:40:30")).elapsed, 0.001f)
        assertEquals(192f / 193f, content(at(sunday, "15:16")).elapsed, 0.001f)
    }

    @Test
    fun `at a Stop's very minute the Clock has moved on to the next interval`() {
        val c = content(at(sunday, "12:04"))

        assertEquals(R.string.prayer_dhuhr, c.previous.nameRes)
        assertEquals(R.string.prayer_asr, c.next.nameRes)
        assertEquals("3h 13m", c.countdown)
    }

    @Test
    fun `a minute or less before the Stop the countdown reads one minute, and on the hour it drops the minutes`() {
        assertEquals("1m", content(at(sunday, "12:03:01")).countdown)
        assertEquals("1m", content(at(sunday, "12:03")).countdown)
        assertEquals("1h", content(at(sunday, "11:04")).countdown)
        assertEquals(Duration.ofMinutes(60), content(at(sunday, "11:04")).untilNext)
    }

    @Test
    fun `the Clock re-renders at the next whole minute, or at the next Stop if that comes first`() {
        val now = at(sunday, "10:30:15")
        assertEquals(at(sunday, "10:31").toInstant(), content(now).nextChange(now.toInstant()))

        // A Stop that isn't on a whole minute (Sunrise at 06:27:20 here) is not left a minute late.
        val day = makkahDay(sunday, at(sunday, "06:26:30")).copy(sunrise = at(sunday, "06:27:20"))
        val early = ClockContent.of(day, at(sunday, "06:27:05"), english, true)
        assertEquals(at(sunday, "06:27:20").toInstant(), early.nextChange(at(sunday, "06:27:05").toInstant()))
    }

    @Test
    fun `the clock follows the phone's 12 or 24-hour setting, with a marker only in 12-hour time`() {
        val afternoon = content(at(sunday, "15:05"), arabic, twentyFourHour = false)
        assertEquals("٣:٠٥", afternoon.clockTime)
        assertEquals(Meridiem.PM, afternoon.meridiem)

        assertEquals(Meridiem.AM, content(at(sunday, "00:30"), twentyFourHour = false).meridiem)
        assertEquals(Meridiem.PM, content(at(sunday, "12:00"), twentyFourHour = false).meridiem)

        val day = content(at(sunday, "15:05"), english, twentyFourHour = true)
        assertEquals("15:05", day.clockTime)
        assertNull(day.meridiem)
    }

    @Test
    fun `the date line is the Hijri date with the user's offset, then the Gregorian date`() {
        // 8 November 2026 is 28 Jumada al-Ula 1448 (Umm al-Qura); an offset of +1 makes it the 29th.
        assertEquals("٢٩ جمادى الأولى ١٤٤٨ هـ · الأحد ٨ نوفمبر", content(at(sunday, "10:00"), arabic, hijriOffset = 1).dateLine)
        assertEquals("28 Jumada al-Ula 1448 AH · Sun 8 Nov", content(at(sunday, "10:00"), english).dateLine)
    }

    @Test
    fun `with Western digits in Arabic every number is Western`() {
        val c = content(at(sunday, "10:30"), DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.WESTERN), twentyFourHour = false)

        assertEquals("10:30", c.clockTime)
        assertEquals("12:04", c.next.time)
        assertEquals("1 س 34 د", c.countdown)
    }
}
