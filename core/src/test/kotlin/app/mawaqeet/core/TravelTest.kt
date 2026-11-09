package app.mawaqeet.core

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

private val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")
private val MAKKAH = PrayerSettings.Default
private val CAIRO = PrayerSettings.Default.copy(
    location = Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo")),
)
private val JAKARTA = PrayerSettings.Default.copy(location = Location(-6.2088, 106.8456, ZoneId.of("Asia/Jakarta")))

/** An instant given as Makkah wall-clock time. */
private fun makkah(dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(RIYADH).toInstant()

private fun date(text: String): LocalDate = LocalDate.parse(text)

private fun core(settings: PrayerSettings, at: Instant, log: PrayerLogRepository) =
    PrayerCore(settings, Clock.fixed(at, ZoneOffset.UTC), log)

/** Travels from [from] to [to] at [at]: the app records the change on the old core, then saves the new settings. */
private fun travel(from: PrayerSettings, @Suppress("UNUSED_PARAMETER") to: PrayerSettings, at: Instant, log: PrayerLogRepository) {
    core(from, at, log).recordLocationChange()
}

class TravelTest {

    @Test
    fun `after travel west past Prayer Days keep their times marks and markability`() {
        val log = InMemoryPrayerLog()
        val inMakkah = core(MAKKAH, makkah("2026-11-08T21:00"), log)
        Prayer.entries.forEach { inMakkah.mark(date("2026-11-08"), it) }
        val laterInMakkah = core(MAKKAH, makkah("2026-11-11T12:00"), log)
        val before = laterInMakkah.prayerDay(date("2026-11-08"))

        travel(MAKKAH, CAIRO, makkah("2026-11-09T12:00"), log)
        val after = core(CAIRO, makkah("2026-11-11T12:00"), log).prayerDay(date("2026-11-08"))

        assertEquals(before, after)
    }

    @Test
    fun `a past Prayer Day's Fajr boundary stays where it was`() {
        // Makkah Fajr on 8 November 2026 is about 05:10 Makkah time (Umm al-Qura); Cairo's is about 04:50 Cairo
        // time, 05:50 Makkah time, so with Cairo's times a minute after Makkah's Fajr would still be 7 November.
        val log = InMemoryPrayerLog()
        val makkahFajr = core(MAKKAH, makkah("2026-11-08T12:00"), log).prayerDay(date("2026-11-08"))
            .prayerTimes.first().time.toInstant()

        travel(MAKKAH, CAIRO, makkah("2026-11-09T12:00"), log)
        val inCairo = core(CAIRO, makkah("2026-11-11T12:00"), log)

        assertEquals(date("2026-11-08"), inCairo.prayerDayAt(makkahFajr.plusSeconds(60)).date)
        assertEquals(date("2026-11-07"), inCairo.prayerDayAt(makkahFajr.minusSeconds(60)).date)
    }

    @Test
    fun `the Prayer Day in progress and later ones follow the new place`() {
        val log = InMemoryPrayerLog()
        travel(MAKKAH, CAIRO, makkah("2026-11-09T12:00"), log)
        val inCairo = core(CAIRO, makkah("2026-11-09T12:00"), log)
        val cairoOnly = core(CAIRO, makkah("2026-11-09T12:00"), InMemoryPrayerLog())

        assertEquals(date("2026-11-09"), inCairo.currentPrayerDay().date)
        for (day in listOf("2026-11-09", "2026-11-10", "2026-11-20")) {
            assertEquals(cairoOnly.prayerDay(date(day)).prayerTimes, inCairo.prayerDay(date(day)).prayerTimes)
        }
    }

    @Test
    fun `a Prayer marked before travel stays prayed even if its new time is later`() {
        val log = InMemoryPrayerLog()
        // Makkah Dhuhr is about 11:55; Cairo's is about 11:40 Cairo time, 12:40 Makkah time.
        core(MAKKAH, makkah("2026-11-09T12:30"), log).mark(date("2026-11-09"), Prayer.DHUHR)

        travel(MAKKAH, CAIRO, makkah("2026-11-09T12:30"), log)
        val day = core(CAIRO, makkah("2026-11-09T12:30"), log).currentPrayerDay()

        assertEquals(LogState.PRAYED, day.stateOf(Prayer.DHUHR))
    }

    @Test
    fun `travel back and forth across time zones keeps each past Prayer Day at its own place`() {
        val log = InMemoryPrayerLog()
        val makkahDay = core(MAKKAH, makkah("2026-11-20T12:00"), log).prayerDay(date("2026-11-08"))
        val jakartaDay = core(JAKARTA, makkah("2026-11-20T12:00"), log).prayerDay(date("2026-11-12"))

        travel(MAKKAH, JAKARTA, makkah("2026-11-10T12:00"), log)
        travel(JAKARTA, CAIRO, makkah("2026-11-15T12:00"), log)
        val inCairo = core(CAIRO, makkah("2026-11-20T12:00"), log)

        assertEquals(makkahDay, inCairo.prayerDay(date("2026-11-08")))
        assertEquals(jakartaDay, inCairo.prayerDay(date("2026-11-12")))
    }

    @Test
    fun `a second change on the same Prayer Day does not move the earlier days`() {
        val log = InMemoryPrayerLog()
        val makkahDay = core(MAKKAH, makkah("2026-11-20T12:00"), log).prayerDay(date("2026-11-08"))

        travel(MAKKAH, JAKARTA, makkah("2026-11-09T12:00"), log)
        travel(JAKARTA, CAIRO, makkah("2026-11-09T13:00"), log)

        assertEquals(makkahDay, core(CAIRO, makkah("2026-11-20T12:00"), log).prayerDay(date("2026-11-08")))
    }
}
