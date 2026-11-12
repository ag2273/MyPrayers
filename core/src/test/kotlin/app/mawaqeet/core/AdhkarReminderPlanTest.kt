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

/** Worship Reminders (#75): the morning adhkar after Fajr and the evening adhkar after Asr, as `PrayerCore.alertPlan()` plans them. */
class AdhkarReminderPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun planAt(time: ZonedDateTime, settings: PrayerSettings): AlertPlan =
        PrayerCore(settings, Clock.fixed(time.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog()).alertPlan()

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun adhkarOn(
        morning: AdhkarReminder? = AdhkarReminder(enabled = true),
        evening: AdhkarReminder? = AdhkarReminder(enabled = true),
        alerts: AlertSettings = AlertSettings(),
        location: Location = PrayerSettings.Default.location,
    ) = PrayerSettings.Default.copy(
        alerts = alerts.copy(
            worshipReminders = WorshipReminders(
                morningAdhkar = morning ?: AdhkarReminder(),
                eveningAdhkar = evening ?: AdhkarReminder(),
            ),
        ),
        location = location,
    )

    private fun AlertPlan.times(kind: WorshipReminderKind, zone: ZoneId = riyadh) = worshipReminders
        .filter { it.kind == kind }
        .map { "${it.prayerDay} " + it.instant.atZone(zone).truncatedTo(ChronoUnit.MINUTES).toLocalTime() }

    @Test
    fun `both adhkar reminders are off by default, 30 minutes after their Prayer`() {
        assertEquals(AdhkarReminder(enabled = false, minutes = 30), AlertSettings().worshipReminders.morningAdhkar)
        assertEquals(AdhkarReminder(enabled = false, minutes = 30), AlertSettings().worshipReminders.eveningAdhkar)

        assertTrue(planAt(makkah("2026-11-08", "00:30"), PrayerSettings.Default).worshipReminders.isEmpty())
    }

    @Test
    fun `the morning reminder comes 30 minutes after Fajr and the evening one 30 minutes after Asr, each Prayer Day`() {
        // Makkah, Umm al-Qura: Fajr 05:09 on 2026-11-08 and 05:10 on the 9th, Asr 15:17 on both.
        val plan = planAt(makkah("2026-11-08", "00:30"), adhkarOn())

        assertEquals(listOf("2026-11-08 05:39", "2026-11-09 05:40"), plan.times(WorshipReminderKind.MORNING_ADHKAR))
        assertEquals(listOf("2026-11-08 15:47", "2026-11-09 15:47"), plan.times(WorshipReminderKind.EVENING_ADHKAR))
        assertEquals(plan.worshipReminders.sortedBy { it.instant }, plan.worshipReminders)
        assertTrue(plan.worshipReminders.all { it.slot == 0 })
    }

    @Test
    fun `each keeps its own minutes`() {
        val plan = planAt(
            makkah("2026-11-08", "00:30"),
            adhkarOn(morning = AdhkarReminder(enabled = true, minutes = 60), evening = AdhkarReminder(enabled = true, minutes = 15)),
        )

        assertEquals(listOf("2026-11-08 06:09", "2026-11-09 06:10"), plan.times(WorshipReminderKind.MORNING_ADHKAR))
        assertEquals(listOf("2026-11-08 15:32", "2026-11-09 15:32"), plan.times(WorshipReminderKind.EVENING_ADHKAR))
    }

    @Test
    fun `only the one switched on is planned, and a reminder already past is not planned again`() {
        val plan = planAt(makkah("2026-11-08", "10:00"), adhkarOn(evening = null))

        assertEquals(listOf("2026-11-09 05:40", "2026-11-10 05:40"), plan.times(WorshipReminderKind.MORNING_ADHKAR))
        assertEquals(emptyList(), plan.times(WorshipReminderKind.EVENING_ADHKAR))
    }

    @Test
    fun `with every app alert turned off no adhkar reminder is planned`() {
        val plan = planAt(makkah("2026-11-08", "00:30"), adhkarOn(alerts = AlertSettings(enabled = false)))

        assertTrue(plan.worshipReminders.isEmpty())
    }

    @Test
    fun `only 15, 30, 45 or 60 minutes after the Prayer is allowed`() {
        assertEquals(listOf(15, 30, 45, 60), AdhkarReminder.MINUTES)
        assertFailsWith<IllegalArgumentException> { AdhkarReminder(minutes = 20) }
        assertFailsWith<IllegalArgumentException> { AdhkarReminder(minutes = 0) }
    }

    @Test
    fun `across the end of summer time they keep their real distance from Fajr and Asr`() {
        // London leaves BST (UTC+1) for GMT at 01:00 UTC on Sunday 2026-10-25.
        val london = ZoneId.of("Europe/London")
        val settings = adhkarOn(location = Location(51.5074, -0.1278, london)).copy(method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val core = PrayerCore(settings, Clock.fixed(ZonedDateTime.of(2026, 10, 24, 0, 30, 0, 0, london).toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())
        val plan = core.alertPlan()

        for (date in listOf("2026-10-24", "2026-10-25")) {
            val day = core.prayerDay(LocalDate.parse(date))
            fun at(prayer: Prayer) = day.prayerTimes.single { it.prayer == prayer }.time.toInstant()
            val reminders = plan.worshipReminders.filter { it.prayerDay == day.date }
            assertEquals(Duration.ofMinutes(30), Duration.between(at(Prayer.FAJR), reminders.single { it.kind == WorshipReminderKind.MORNING_ADHKAR }.instant))
            assertEquals(Duration.ofMinutes(30), Duration.between(at(Prayer.ASR), reminders.single { it.kind == WorshipReminderKind.EVENING_ADHKAR }.instant))
        }
    }

    @Test
    fun `after travel they follow the new location's Fajr and Asr`() {
        val cairo = ZoneId.of("Africa/Cairo")
        val settings = adhkarOn(location = Location(30.0444, 31.2357, cairo)).copy(method = CalculationMethod.EGYPTIAN)
        val core = PrayerCore(settings, Clock.fixed(ZonedDateTime.of(2026, 11, 8, 0, 30, 0, 0, cairo).toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())
        val day = core.prayerDay(LocalDate.parse("2026-11-08"))

        val plan = core.alertPlan()

        val fajr = day.prayerTimes.single { it.prayer == Prayer.FAJR }.time.toInstant()
        val asr = day.prayerTimes.single { it.prayer == Prayer.ASR }.time.toInstant()
        val today = plan.worshipReminders.filter { it.prayerDay == day.date }
        assertEquals(listOf(fajr.plusSeconds(1800), asr.plusSeconds(1800)), today.map { it.instant })
    }

    @Test
    fun `the adhkar kinds name the set they open`() {
        assertEquals(AdhkarSetId.MORNING, WorshipReminderKind.MORNING_ADHKAR.adhkarSet)
        assertEquals(AdhkarSetId.EVENING, WorshipReminderKind.EVENING_ADHKAR.adhkarSet)
        assertEquals(null, WorshipReminderKind.SALAWAT.adhkarSet)
    }
}
