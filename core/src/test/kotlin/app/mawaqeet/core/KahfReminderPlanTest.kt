package app.mawaqeet.core

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Worship Reminders (#64): the Surat Al-Kahf reminder `PrayerCore.alertPlan()` plans on Friday at Sunrise + 1 hour. */
class KahfReminderPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun core(time: ZonedDateTime, settings: PrayerSettings) =
        PrayerCore(settings, Clock.fixed(time.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun kahfOn(alerts: AlertSettings = AlertSettings(), location: Location = PrayerSettings.Default.location) =
        PrayerSettings.Default.copy(
            alerts = alerts.copy(worshipReminders = WorshipReminders(kahf = KahfReminder(enabled = true))),
            location = location,
        )

    private fun AlertPlan.kahf() = worshipReminders.filter { it.kind == WorshipReminderKind.KAHF }

    @Test
    fun `the Kahf reminder is off by default`() {
        assertEquals(KahfReminder(enabled = false), AlertSettings().worshipReminders.kahf)

        // Thursday evening in Makkah: Friday 2026-11-13 is within the plan.
        val plan = core(makkah("2026-11-12", "20:00"), PrayerSettings.Default).alertPlan()

        assertTrue(plan.kahf().isEmpty())
    }

    @Test
    fun `once on it comes on Friday at Sunrise plus one hour`() {
        // Makkah, Umm al-Qura, Friday 2026-11-13: Sunrise 06:30.
        val core = core(makkah("2026-11-12", "20:00"), kahfOn())
        val plan = core.alertPlan()

        val kahf = plan.kahf().single()
        val friday = LocalDate.parse("2026-11-13")
        assertEquals(friday, kahf.prayerDay)
        assertEquals(0, kahf.slot)
        assertEquals(Duration.ofHours(1), Duration.between(core.prayerDay(friday).sunrise.toInstant(), kahf.instant))
        assertEquals("07:30", kahf.instant.atZone(riyadh).truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString())
    }

    @Test
    fun `no Kahf reminder on any other day of the week`() {
        // Saturday 2026-11-07 through Thursday 2026-11-12: each plan covers that day and the next.
        for (date in listOf("2026-11-07", "2026-11-08", "2026-11-09", "2026-11-10", "2026-11-11")) {
            val plan = core(makkah(date, "00:30"), kahfOn()).alertPlan()

            assertTrue(plan.kahf().isEmpty(), "$date")
        }
        val fridayMorning = core(makkah("2026-11-13", "00:30"), kahfOn()).alertPlan()
        assertEquals(listOf(DayOfWeek.FRIDAY), fridayMorning.kahf().map { it.prayerDay.dayOfWeek })
    }

    @Test
    fun `once past on Friday it is not planned again`() {
        val plan = core(makkah("2026-11-13", "08:00"), kahfOn()).alertPlan()

        assertTrue(plan.kahf().isEmpty())
    }

    @Test
    fun `with every app alert turned off no Kahf reminder is planned`() {
        val plan = core(makkah("2026-11-12", "20:00"), kahfOn(alerts = AlertSettings(enabled = false))).alertPlan()

        assertTrue(plan.kahf().isEmpty())
    }

    @Test
    fun `after travel it follows the new location's Friday Sunrise`() {
        val cairo = ZoneId.of("Africa/Cairo")
        val settings = kahfOn(location = Location(30.0444, 31.2357, cairo)).copy(method = CalculationMethod.EGYPTIAN)
        val core = core(ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, cairo), settings)

        val kahf = core.alertPlan().kahf().single()

        val friday = LocalDate.parse("2026-11-13")
        assertEquals(Duration.ofHours(1), Duration.between(core.prayerDay(friday).sunrise.toInstant(), kahf.instant))
        assertEquals(7, kahf.instant.atZone(cairo).hour)
    }

    @Test
    fun `across a change of clocks it keeps a real hour after that Friday's Sunrise`() {
        // New York leaves daylight time (UTC-4) for standard time (UTC-5) at 02:00 on Sunday 2026-11-01; Friday
        // 2026-10-30 is before it and Friday 2026-11-06 after it.
        val newYork = ZoneId.of("America/New_York")
        val settings = kahfOn(location = Location(40.7128, -74.0060, newYork)).copy(method = CalculationMethod.NORTH_AMERICA)
        for (friday in listOf("2026-10-30", "2026-11-06")) {
            val day = LocalDate.parse(friday)
            val core = core(ZonedDateTime.of(day.minusDays(1), LocalTime.of(20, 0), newYork), settings)

            val kahf = core.alertPlan().kahf().single()

            assertEquals(day, kahf.prayerDay)
            assertEquals(Duration.ofHours(1), Duration.between(core.prayerDay(day).sunrise.toInstant(), kahf.instant), friday)
        }
    }

    @Test
    fun `salawat and Kahf on together are both planned, in time order`() {
        val both = WorshipReminders(salawat = SalawatReminder(enabled = true), kahf = KahfReminder(enabled = true))
        val plan = core(makkah("2026-11-13", "00:30"), PrayerSettings.Default.copy(alerts = AlertSettings(worshipReminders = both))).alertPlan()

        assertEquals(1, plan.kahf().size)
        assertTrue(plan.worshipReminders.any { it.kind == WorshipReminderKind.SALAWAT })
        assertEquals(plan.worshipReminders.sortedBy { it.instant }, plan.worshipReminders)
    }
}
