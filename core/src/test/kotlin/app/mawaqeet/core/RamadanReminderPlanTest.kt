package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ramadan reminders (#91): «تذكير السحور» before Fajr and «تنبيه الإمساك» at Imsak, as `PrayerCore.alertPlan()` plans
 * them, and Maghrib's Adhan Alert marked as Iftar. Ramadan 1448 (Umm al-Qura) runs from Monday 2027-02-08 (1 Ramadan)
 * to Monday 2027-03-08 (29 Ramadan); 1 Shawwal is 2027-03-09.
 */
class RamadanReminderPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun core(at: ZonedDateTime, settings: PrayerSettings) =
        PrayerCore(settings, Clock.fixed(at.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())

    private fun ramadanOn(
        suhoor: SuhoorReminder = SuhoorReminder(enabled = true),
        imsakAlert: Boolean = true,
        imsakMinutes: Int = RamadanSettings.DEFAULT_IMSAK_MINUTES,
        alerts: AlertSettings = AlertSettings(),
        hijriOffsetDays: Int = 0,
        location: Location = PrayerSettings.Default.location,
    ) = PrayerSettings.Default.copy(
        alerts = alerts.copy(ramadan = RamadanSettings(imsakMinutes = imsakMinutes, suhoorReminder = suhoor, imsakAlert = imsakAlert)),
        hijriOffsetDays = hijriOffsetDays,
        location = location,
    )

    private fun PrayerCore.fajr(date: String) = prayerDay(LocalDate.parse(date)).prayerTimes.single { it.prayer == Prayer.FAJR }.time.toInstant()

    private fun AlertPlan.of(kind: WorshipReminderKind) = worshipReminders.filter { it.kind == kind }

    @Test
    fun `both Ramadan reminders are off by default, the suhoor one an hour before Fajr`() {
        assertEquals(SuhoorReminder(enabled = false, minutes = 60), AlertSettings().ramadan.suhoorReminder)
        assertEquals(false, AlertSettings().ramadan.imsakAlert)
        assertEquals(listOf(30, 45, 60, 90, 120), SuhoorReminder.MINUTES)
        kotlin.test.assertFailsWith<IllegalArgumentException> { SuhoorReminder(minutes = 50) }

        assertTrue(core(makkah("2027-02-21", "20:00"), PrayerSettings.Default).alertPlan().worshipReminders.isEmpty())
    }

    @Test
    fun `on Fasting Days the suhoor reminder comes an hour before Fajr and the Imsak alert at Imsak`() {
        val core = core(makkah("2027-02-21", "20:00"), ramadanOn())

        val plan = core.alertPlan()

        val fasting = listOf("2027-02-22", "2027-02-23")
        assertEquals(fasting.map { core.fajr(it).minus(Duration.ofMinutes(60)) }, plan.of(WorshipReminderKind.SUHOOR).map { it.instant })
        assertEquals(fasting.map { core.fajr(it).minus(Duration.ofMinutes(10)) }, plan.of(WorshipReminderKind.IMSAK).map { it.instant })
        assertEquals(fasting.map(LocalDate::parse), plan.of(WorshipReminderKind.SUHOOR).map { it.prayerDay })
        assertEquals(plan.worshipReminders.sortedBy { it.instant }, plan.worshipReminders)
    }

    @Test
    fun `the suhoor reminder keeps its chosen minutes and the Imsak alert follows the Imsak minutes`() {
        val core = core(
            makkah("2027-02-21", "20:00"),
            ramadanOn(suhoor = SuhoorReminder(enabled = true, minutes = 120), imsakMinutes = 25),
        )

        val plan = core.alertPlan()

        assertEquals(core.fajr("2027-02-22").minus(Duration.ofMinutes(120)), plan.of(WorshipReminderKind.SUHOOR).first().instant)
        assertEquals(core.fajr("2027-02-22").minus(Duration.ofMinutes(25)), plan.of(WorshipReminderKind.IMSAK).first().instant)
    }

    @Test
    fun `each switch plans only its own reminder`() {
        val at = makkah("2027-02-21", "20:00")

        val suhoorOnly = core(at, ramadanOn(imsakAlert = false)).alertPlan()
        val imsakOnly = core(at, ramadanOn(suhoor = SuhoorReminder(enabled = false))).alertPlan()

        assertEquals(setOf(WorshipReminderKind.SUHOOR), suhoorOnly.worshipReminders.map { it.kind }.toSet())
        assertEquals(setOf(WorshipReminderKind.IMSAK), imsakOnly.worshipReminders.map { it.kind }.toSet())
    }

    @Test
    fun `none outside Ramadan, from the eve of 1 Ramadan, and none on Eid morning`() {
        // The window from 2027-02-06 20:00 covers the mornings of 7 February (Sha'ban) and 8 February (1 Ramadan).
        val first = core(makkah("2027-02-06", "20:00"), ramadanOn()).alertPlan()
        assertEquals(listOf(LocalDate.parse("2027-02-08")), first.of(WorshipReminderKind.SUHOOR).map { it.prayerDay })
        assertEquals(listOf(LocalDate.parse("2027-02-08")), first.of(WorshipReminderKind.IMSAK).map { it.prayerDay })

        // From 2027-03-07 20:00: the morning of 29 Ramadan (8 March) and of Eid (9 March).
        val last = core(makkah("2027-03-07", "20:00"), ramadanOn()).alertPlan()
        assertEquals(listOf(LocalDate.parse("2027-03-08")), last.of(WorshipReminderKind.SUHOOR).map { it.prayerDay })
        assertEquals(listOf(LocalDate.parse("2027-03-08")), last.of(WorshipReminderKind.IMSAK).map { it.prayerDay })

        assertTrue(core(makkah("2026-11-12", "20:00"), ramadanOn()).alertPlan().worshipReminders.isEmpty())
    }

    @Test
    fun `the Hijri offset moves the first Fasting Day`() {
        val at = makkah("2027-02-06", "20:00")

        // +1: 1 Ramadan is a day earlier, on 7 February. −1: a day later, on 9 February (outside this window).
        val earlier = core(at, ramadanOn(hijriOffsetDays = 1)).alertPlan()
        val later = core(at, ramadanOn(hijriOffsetDays = -1)).alertPlan()

        assertEquals(listOf("2027-02-07", "2027-02-08").map(LocalDate::parse), earlier.of(WorshipReminderKind.SUHOOR).map { it.prayerDay })
        assertEquals(emptyList(), later.of(WorshipReminderKind.SUHOOR))
    }

    @Test
    fun `with every app alert turned off no Ramadan reminder is planned`() {
        val plan = core(makkah("2027-02-21", "20:00"), ramadanOn(alerts = AlertSettings(enabled = false))).alertPlan()

        assertTrue(plan.worshipReminders.isEmpty())
    }

    @Test
    fun `a reminder already past is not planned again`() {
        val core = core(makkah("2027-02-22", "04:30"), ramadanOn())

        val plan = core.alertPlan()

        // Fajr is about 05:30: the suhoor reminder (about 04:30) has gone, Imsak (about 05:20) has not.
        assertEquals(listOf("2027-02-23", "2027-02-24").map(LocalDate::parse), plan.of(WorshipReminderKind.SUHOOR).map { it.prayerDay })
        assertEquals(LocalDate.parse("2027-02-22"), plan.of(WorshipReminderKind.IMSAK).first().prayerDay)
    }

    @Test
    fun `with Prayer Silence on they are still planned, each outside or inside a window alike`() {
        // A Silence Window is quieted by the phone, not by the plan: the reminder is posted without sound (#76).
        val silence = PrayerSilence(enabled = true)
        val plan = core(makkah("2027-02-21", "20:00"), ramadanOn(alerts = AlertSettings(prayerSilence = silence))).alertPlan()

        assertEquals(2, plan.of(WorshipReminderKind.SUHOOR).size)
        assertEquals(2, plan.of(WorshipReminderKind.IMSAK).size)
        assertTrue(plan.silenceWindows.isNotEmpty())
    }

    @Test
    fun `after travel they follow the new location's Fajr`() {
        val cairo = ZoneId.of("Africa/Cairo")
        val settings = ramadanOn(location = Location(30.0444, 31.2357, cairo)).copy(method = CalculationMethod.EGYPTIAN)
        val core = core(ZonedDateTime.of(2027, 2, 21, 20, 0, 0, 0, cairo), settings)

        val plan = core.alertPlan()

        assertEquals(core.fajr("2027-02-22").minus(Duration.ofMinutes(60)), plan.of(WorshipReminderKind.SUHOOR).first().instant)
        assertEquals(core.fajr("2027-02-22").minus(Duration.ofMinutes(10)), plan.of(WorshipReminderKind.IMSAK).first().instant)
    }

    @Test
    fun `on a Fasting Day Maghrib's Adhan Alert is Iftar, and on no other day or Prayer`() {
        val inRamadan = core(makkah("2027-03-07", "00:30"), PrayerSettings.Default).alertPlan()
        val iftar = inRamadan.alerts.filter { it.isIftar }
        // 7 and 8 March are Fasting Days; 9 March is Eid.
        assertEquals(listOf("2027-03-07", "2027-03-08").map(LocalDate::parse), iftar.map { it.prayerDay })
        assertTrue(iftar.all { it.prayer == Prayer.MAGHRIB && it.kind == AlertKind.ADHAN_ALERT })

        val withReminder = PrayerSettings.Default.copy(
            alerts = AlertSettings().with(Prayer.MAGHRIB, PrayerAlertSettings(reminder = true)),
        )
        val reminders = core(makkah("2027-03-07", "00:30"), withReminder).alertPlan().alerts.filter { it.kind == AlertKind.PRAYER_REMINDER }
        assertTrue(reminders.none { it.isIftar })

        assertTrue(core(makkah("2026-11-12", "00:30"), PrayerSettings.Default).alertPlan().alerts.none { it.isIftar })
    }

    @Test
    fun `on a Fasting Day the display alarm goes off at Imsak, so the Next Prayer widgets switch from suhoor to iftar`() {
        // Reminders off: nothing else fires at Imsak.
        val core = core(makkah("2027-02-22", "01:00"), ramadanOn(suhoor = SuhoorReminder(), imsakAlert = false))

        assertEquals(core.fajr("2027-02-22").minus(Duration.ofMinutes(10)), core.alertPlan().nextDisplayChange)
    }

    @Test
    fun `with the master switch off the display alarm still goes off at Imsak, then at Maghrib`() {
        val off = ramadanOn(alerts = AlertSettings(enabled = false))
        val beforeImsak = core(makkah("2027-02-22", "01:00"), off)
        val afternoon = core(makkah("2027-02-22", "16:00"), off)

        assertEquals(beforeImsak.fajr("2027-02-22").minus(Duration.ofMinutes(10)), beforeImsak.alertPlan().nextDisplayChange)
        assertEquals(
            afternoon.prayerDay(LocalDate.parse("2027-02-22")).prayerTimes.single { it.prayer == Prayer.MAGHRIB }.time.toInstant(),
            afternoon.alertPlan().nextDisplayChange,
        )
    }

    @Test
    fun `outside Ramadan nothing moves the display alarm to before Fajr`() {
        val core = core(makkah("2027-03-10", "01:00"), ramadanOn())

        assertEquals(makkah("2027-03-11", "00:00").toInstant(), core.alertPlan().nextDisplayChange)
    }
}
