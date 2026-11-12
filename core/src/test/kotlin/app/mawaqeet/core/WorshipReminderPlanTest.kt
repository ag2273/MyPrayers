package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Worship Reminders (#63): the salawat reminders `PrayerCore.alertPlan()` plans between Sunrise and Isha. */
class WorshipReminderPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun planAt(time: ZonedDateTime, settings: PrayerSettings): AlertPlan =
        PrayerCore(settings, Clock.fixed(time.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog()).alertPlan()

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun salawatOn(
        intervalHours: Int = SalawatReminder.DEFAULT_INTERVAL_HOURS,
        alerts: AlertSettings = AlertSettings(),
        location: Location = PrayerSettings.Default.location,
    ) = PrayerSettings.Default.copy(
        alerts = alerts.copy(worshipReminders = WorshipReminders(salawat = SalawatReminder(enabled = true, intervalHours = intervalHours))),
        location = location,
    )

    private fun AlertPlan.salawatTimes(zone: ZoneId, day: String) = worshipReminders
        .filter { it.kind == WorshipReminderKind.SALAWAT && it.prayerDay == LocalDate.parse(day) }
        .map { it.instant.atZone(zone).truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString() }

    @Test
    fun `salawat reminders are off by default, every two hours once on`() {
        assertEquals(SalawatReminder(enabled = false, intervalHours = 2), AlertSettings().worshipReminders.salawat)

        val plan = planAt(makkah("2026-11-08", "00:30"), PrayerSettings.Default)

        assertTrue(plan.worshipReminders.isEmpty())
    }

    @Test
    fun `every 2 hours from Sunrise plus two hours, stepping by two, the last before Isha`() {
        // Makkah, Umm al-Qura, 2026-11-08: Sunrise 06:27, Isha 19:11.
        val plan = planAt(makkah("2026-11-08", "00:30"), salawatOn(2))

        assertEquals(listOf("08:27", "10:27", "12:27", "14:27", "16:27", "18:27"), plan.salawatTimes(riyadh, "2026-11-08"))
    }

    @Test
    fun `every hour and every 3 hours step by their own interval, never after Isha`() {
        val hourly = planAt(makkah("2026-11-08", "00:30"), salawatOn(1))
        val threeHourly = planAt(makkah("2026-11-08", "00:30"), salawatOn(3))

        assertEquals((7..18).map { "%02d:27".format(it) }, hourly.salawatTimes(riyadh, "2026-11-08"))
        assertEquals(listOf("09:27", "12:27", "15:27", "18:27"), threeHourly.salawatTimes(riyadh, "2026-11-08"))
    }

    @Test
    fun `none before Sunrise or after Isha, across the 48 hours, in time order`() {
        val plan = planAt(makkah("2026-11-08", "00:30"), salawatOn(2))
        val core = PrayerCore(salawatOn(2), Clock.fixed(makkah("2026-11-08", "00:30").toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())

        assertEquals(plan.worshipReminders.sortedBy { it.instant }, plan.worshipReminders)
        for (reminder in plan.worshipReminders) {
            val day = core.prayerDay(reminder.prayerDay)
            assertTrue(reminder.instant.isAfter(day.sunrise.toInstant()), "$reminder is before Sunrise")
            val isha = day.prayerTimes.single { it.prayer == Prayer.ISHA }.time.toInstant()
            assertTrue(reminder.instant.isBefore(isha), "$reminder is not before Isha")
        }
        // 2026-11-08 and 2026-11-09 in full: six each.
        assertEquals(12, plan.worshipReminders.size)
    }

    @Test
    fun `a reminder already past is not planned again, and each has a distinct slot on its Prayer Day`() {
        val plan = planAt(makkah("2026-11-08", "12:30"), salawatOn(2))

        assertEquals(listOf("14:27", "16:27", "18:27"), plan.salawatTimes(riyadh, "2026-11-08"))
        val today = plan.worshipReminders.filter { it.prayerDay == LocalDate.parse("2026-11-08") }
        assertEquals(listOf(3, 4, 5), today.map { it.slot })
        val tomorrow = plan.worshipReminders.filter { it.prayerDay == LocalDate.parse("2026-11-09") }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), tomorrow.map { it.slot })
    }

    @Test
    fun `with every app alert turned off no worship reminder is planned`() {
        val plan = planAt(makkah("2026-11-08", "00:30"), salawatOn(2, alerts = AlertSettings(enabled = false)))

        assertTrue(plan.worshipReminders.isEmpty())
    }

    @Test
    fun `turning salawat off keeps the chosen interval for when it comes back`() {
        val off = WorshipReminders(salawat = SalawatReminder(enabled = false, intervalHours = 3))

        val plan = planAt(makkah("2026-11-08", "00:30"), PrayerSettings.Default.copy(alerts = AlertSettings(worshipReminders = off)))

        assertTrue(plan.worshipReminders.isEmpty())
        assertEquals(3, off.salawat.intervalHours)
    }

    @Test
    fun `only every 1, 2 or 3 hours is allowed`() {
        assertEquals(listOf(1, 2, 3), SalawatReminder.INTERVAL_HOURS)
        assertFailsWith<IllegalArgumentException> { SalawatReminder(intervalHours = 4) }
        assertFailsWith<IllegalArgumentException> { SalawatReminder(intervalHours = 0) }
    }

    @Test
    fun `across the end of summer time the reminders keep their real interval from the day's Sunrise`() {
        // London leaves BST (UTC+1) for GMT at 01:00 UTC on Sunday 2026-10-25.
        val london = ZoneId.of("Europe/London")
        val settings = salawatOn(2, location = Location(51.5074, -0.1278, london)).copy(method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val core = PrayerCore(settings, Clock.fixed(ZonedDateTime.of(2026, 10, 24, 0, 30, 0, 0, london).toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())
        val plan = core.alertPlan()

        for (date in listOf("2026-10-24", "2026-10-25")) {
            val reminders = plan.worshipReminders.filter { it.prayerDay == LocalDate.parse(date) }
            val sunrise = core.prayerDay(LocalDate.parse(date)).sunrise.toInstant()
            assertTrue(reminders.isNotEmpty())
            reminders.forEachIndexed { index, reminder ->
                assertEquals(Duration.ofHours(2L * (index + 1)), Duration.between(sunrise, reminder.instant), "$date #$index")
            }
        }
    }

    @Test
    fun `after travel the reminders follow the new location's Sunrise`() {
        // The same morning, seen from Cairo (Egyptian method): Sunrise 2026-11-08 a little after 06:00 Cairo time.
        val cairo = ZoneId.of("Africa/Cairo")
        val settings = salawatOn(2, location = Location(30.0444, 31.2357, cairo)).copy(method = CalculationMethod.EGYPTIAN)
        val now = ZonedDateTime.of(2026, 11, 8, 0, 30, 0, 0, cairo)
        val core = PrayerCore(settings, Clock.fixed(now.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())

        val first = core.alertPlan().worshipReminders.first()
        assertEquals(Duration.ofHours(2), Duration.between(core.prayerDay(LocalDate.parse("2026-11-08")).sunrise.toInstant(), first.instant))
        assertEquals(8, first.instant.atZone(cairo).hour)
    }
}
