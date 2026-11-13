package app.mawaqeet.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

private val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")

/** A fake clock frozen at a wall-clock time in Makkah. */
private fun makkahClockAt(dateTime: String): Clock =
    Clock.fixed(LocalDateTime.parse(dateTime).atZone(RIYADH).toInstant(), ZoneOffset.UTC)

private fun displayDayAt(dateTime: String): DisplayDay =
    PrayerCore(PrayerSettings.Default, makkahClockAt(dateTime), InMemoryPrayerLog()).displayDay()

class DisplayDayTest {

    @Test
    fun `the previous day's Isha comes from the previous day's own Prayer Times, across a clock change too`() {
        // London leaves BST (UTC+1) for GMT at 01:00 UTC on Sunday 2026-10-25.
        val london = ZoneId.of("Europe/London")
        val settings = PrayerSettings.Default.copy(
            location = Location(51.5074, -0.1278, london),
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE,
        )
        fun dayAt(dateTime: String) = PrayerCore(
            settings,
            Clock.fixed(LocalDateTime.parse(dateTime).atZone(london).toInstant(), ZoneOffset.UTC),
            InMemoryPrayerLog(),
        ).displayDay()

        val sunday = dayAt("2026-10-25T12:00")
        val saturday = dayAt("2026-10-24T12:00")

        assertEquals(saturday.timeOf(Prayer.ISHA).toInstant(), sunday.previousIsha.time.toInstant())
        assertEquals(Prayer.ISHA, sunday.previousIsha.prayer)
        // Saturday's Isha was in the evening on Saturday's (summer) clock: after 19:00 BST.
        assertEquals(LocalDate.of(2026, 10, 24), sunday.previousIsha.time.toLocalDate())
        kotlin.test.assertTrue(sunday.previousIsha.time.toLocalTime().isAfter(LocalTime.of(19, 0)))
        // Before Sunday's Fajr the display day is still Saturday, with Saturday's own Isha.
        assertEquals(saturday.prayerTimes, dayAt("2026-10-25T03:00").prayerTimes)
    }

    @Test
    fun `default location shows today's Makkah Prayer Times and Sunrise under Umm al-Qura`() {
        val day = displayDayAt("2026-11-08T10:00")

        assertEquals(LocalDate.of(2026, 11, 8), day.date)
        // Expected values: adhan-kotlin 0.0.7, Umm al-Qura, Standard Asr (notes/spec-digest); #16 adds official spot-checks.
        assertEquals(LocalTime.of(5, 9), day.timeOf(Prayer.FAJR).toLocalTime().withSecond(0))
        assertEquals(LocalTime.of(6, 27), day.sunrise.toLocalTime().withSecond(0))
        assertEquals(LocalTime.of(12, 4), day.timeOf(Prayer.DHUHR).toLocalTime().withSecond(0))
        assertEquals(LocalTime.of(15, 17), day.timeOf(Prayer.ASR).toLocalTime().withSecond(0))
        assertEquals(LocalTime.of(17, 41), day.timeOf(Prayer.MAGHRIB).toLocalTime().withSecond(0))
        assertEquals(LocalTime.of(19, 11), day.timeOf(Prayer.ISHA).toLocalTime().withSecond(0))
    }

    @Test
    fun `the next Prayer is the first one whose time has not yet arrived`() {
        val day = displayDayAt("2026-11-08T10:00")

        assertEquals(Prayer.DHUHR, day.next.prayer)
        assertEquals(day.timeOf(Prayer.DHUHR), day.next.time)
    }

    @Test
    fun `between Fajr and Sunrise the next Prayer is Dhuhr, never Sunrise`() {
        val day = displayDayAt("2026-11-08T06:00")

        assertEquals(Prayer.DHUHR, day.next.prayer)
    }

    @Test
    fun `after Isha the display day stays today and the next Prayer is tomorrow's Fajr`() {
        val day = displayDayAt("2026-11-08T22:00")

        assertEquals(LocalDate.of(2026, 11, 8), day.date)
        assertEquals(Prayer.FAJR, day.next.prayer)
        assertEquals(LocalDate.of(2026, 11, 9), day.next.time.toLocalDate())
    }

    @Test
    fun `from midnight until Fajr the display day is the Prayer Day still running, with its own date and times`() {
        // 00:17 on Friday 13 November 2026 in Makkah: Thursday's Prayer Day runs until Friday's Fajr.
        val day = displayDayAt("2026-11-13T00:17")
        val thursday = displayDayAt("2026-11-12T12:00")

        assertEquals(LocalDate.of(2026, 11, 12), day.date)
        assertEquals(thursday.prayerTimes, day.prayerTimes)
        assertEquals(thursday.hijriDate, day.hijriDate)
        assertEquals(false, day.isJumuah)
        // The Next Prayer is the coming Fajr, Friday's.
        assertEquals(Prayer.FAJR, day.next.prayer)
        assertEquals(displayDayAt("2026-11-13T12:00").timeOf(Prayer.FAJR), day.next.time)
    }

    @Test
    fun `at Fajr the display day moves to the new Prayer Day, together with the Prayer Log`() {
        val fajr = displayDayAt("2026-11-13T12:00").timeOf(Prayer.FAJR).toInstant()
        fun at(instant: java.time.Instant) =
            PrayerCore(PrayerSettings.Default, Clock.fixed(instant, ZoneOffset.UTC), InMemoryPrayerLog())

        assertEquals(LocalDate.of(2026, 11, 12), at(fajr.minusSeconds(1)).displayDay().date)
        assertEquals(LocalDate.of(2026, 11, 12), at(fajr.minusSeconds(1)).currentPrayerDay().date)
        assertEquals(LocalDate.of(2026, 11, 13), at(fajr).displayDay().date)
        assertEquals(LocalDate.of(2026, 11, 13), at(fajr).currentPrayerDay().date)
    }

    @Test
    fun `the day is the location's, not the device clock's`() {
        // 03:30 UTC on 9 November is 06:30 on 9 November in Makkah (after Fajr), still 8 November in UTC.
        val utcClock = Clock.fixed(java.time.Instant.parse("2026-11-09T03:30:00Z"), ZoneOffset.UTC)

        val day = PrayerCore(PrayerSettings.Default, utcClock, InMemoryPrayerLog()).displayDay()

        assertEquals(LocalDate.of(2026, 11, 9), day.date)
    }

    @Test
    fun `at the exact Prayer Time that Prayer has arrived and the next one is after it`() {
        val asr = displayDayAt("2026-11-08T10:00").timeOf(Prayer.ASR)

        val day = PrayerCore(PrayerSettings.Default, Clock.fixed(asr.toInstant(), ZoneOffset.UTC), InMemoryPrayerLog()).displayDay()

        assertEquals(Prayer.MAGHRIB, day.next.prayer)
    }

    @Test
    fun `on a Friday Dhuhr is Jumu'ah`() {
        // 13 November 2026 is a Friday.
        assertEquals(true, displayDayAt("2026-11-13T10:00").isJumuah)
        assertEquals(false, displayDayAt("2026-11-12T10:00").isJumuah)
        assertEquals(false, displayDayAt("2026-11-14T10:00").isJumuah)
    }

    @Test
    fun `on a Friday morning the next Prayer is Jumu'ah`() {
        val next = displayDayAt("2026-11-13T10:00").next

        assertEquals(Prayer.DHUHR, next.prayer)
        assertEquals(true, next.isJumuah)
    }

    @Test
    fun `Jumu'ah follows the display day, so it starts at Fajr on Friday, not at midnight`() {
        assertEquals(false, displayDayAt("2026-11-12T23:59").isJumuah)
        assertEquals(false, displayDayAt("2026-11-13T00:17").isJumuah)
        assertEquals(true, displayDayAt("2026-11-13T06:00").isJumuah)
    }
}
