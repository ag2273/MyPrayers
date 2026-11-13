package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sunnah Fast reminders (#95), as `PrayerCore.alertPlan()` plans them: Isha + 30 minutes of the Prayer Day before each
 * Sunnah Fast day. Dates are Umm al-Qura (see [HijriCalendarTest]): Monday 16 Nov 2026 is 6 Jumada al-Akhirah 1448.
 */
class SunnahFastReminderPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun core(at: ZonedDateTime, settings: PrayerSettings) =
        PrayerCore(settings, Clock.fixed(at.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())

    private fun fastsOn(
        vararg fasts: SunnahFast,
        alerts: AlertSettings = AlertSettings(),
        location: Location = PrayerSettings.Default.location,
        hijriOffsetDays: Int = 0,
    ) = PrayerSettings.Default.copy(
        alerts = alerts.copy(worshipReminders = WorshipReminders(fasts = FastReminders(fasts.toSet()))),
        location = location,
        hijriOffsetDays = hijriOffsetDays,
    )

    private val allOn = SunnahFast.entries.toTypedArray()

    /** Each reminder as (Prayer Day, the fast day's Hijri date, kinds). */
    private fun PrayerCore.nights() =
        alertPlan().fastReminders.map { Triple(it.prayerDay.toString(), it.fast?.hijri, it.fast?.kinds) }

    private fun PrayerCore.isha(date: String) =
        prayerDay(LocalDate.parse(date)).prayerTimes.single { it.prayer == Prayer.ISHA }.time.toInstant()

    private val AlertPlan.fastReminders get() = worshipReminders.filter { it.kind == WorshipReminderKind.SUNNAH_FAST }

    @Test
    fun `the night before a Monday, a reminder comes 30 minutes after Isha`() {
        val core = core(makkah("2026-11-15", "12:00"), fastsOn(SunnahFast.MONDAY_THURSDAY))

        val reminders = core.alertPlan().fastReminders

        val reminder = reminders.single()
        assertEquals(core.isha("2026-11-15").plus(Duration.ofMinutes(30)), reminder.instant)
        assertEquals(LocalDate.parse("2026-11-15"), reminder.prayerDay)
        assertEquals(SunnahFastDay(HijriDate(1448, 6, 6), setOf(SunnahFast.MONDAY_THURSDAY)), reminder.fast)
    }

    @Test
    fun `every Sunnah Fast reminder is off by default`() {
        assertEquals(emptySet(), AlertSettings().worshipReminders.fasts.on)

        assertEquals(emptyList(), core(makkah("2026-11-22", "12:00"), PrayerSettings.Default).alertPlan().fastReminders)
    }

    @Test
    fun `each switch reminds only of its own fast`() {
        // Monday 23 Nov 2026 is 13 Jumada al-Akhirah, Tuesday the 14th.
        val at = makkah("2026-11-22", "12:00")

        assertEquals(
            listOf(
                Triple("2026-11-22", HijriDate(1448, 6, 13), setOf(SunnahFast.WHITE_DAYS)),
                Triple("2026-11-23", HijriDate(1448, 6, 14), setOf(SunnahFast.WHITE_DAYS)),
            ),
            core(at, fastsOn(SunnahFast.WHITE_DAYS)).nights(),
        )
        assertEquals(
            listOf(Triple("2026-11-22", HijriDate(1448, 6, 13), setOf(SunnahFast.MONDAY_THURSDAY))),
            core(at, fastsOn(SunnahFast.MONDAY_THURSDAY)).nights(),
        )
        assertEquals(emptyList(), core(at, fastsOn(SunnahFast.ARAFAH, SunnahFast.ASHURA, SunnahFast.SIX_OF_SHAWWAL)).nights())
    }

    @Test
    fun `a white day on a Monday gives one reminder naming both`() {
        val core = core(makkah("2026-11-22", "12:00"), fastsOn(SunnahFast.MONDAY_THURSDAY, SunnahFast.WHITE_DAYS))

        assertEquals(
            listOf(
                Triple("2026-11-22", HijriDate(1448, 6, 13), setOf(SunnahFast.MONDAY_THURSDAY, SunnahFast.WHITE_DAYS)),
                Triple("2026-11-23", HijriDate(1448, 6, 14), setOf(SunnahFast.WHITE_DAYS)),
            ),
            core.nights(),
        )
    }

    @Test
    fun `Arafah is announced the night before 9 Dhu al-Hijjah`() {
        // Saturday 15 May 2027 is Arafah 1448.
        val core = core(makkah("2027-05-14", "12:00"), fastsOn(SunnahFast.ARAFAH))

        val reminder = core.alertPlan().fastReminders.single()

        assertEquals(core.isha("2027-05-14").plus(Duration.ofMinutes(30)), reminder.instant)
        assertEquals(SunnahFastDay(HijriDate(1448, 12, 9), setOf(SunnahFast.ARAFAH)), reminder.fast)
    }

    @Test
    fun `Tasu'a and Ashura are each announced the night before`() {
        // Wednesday 24 June 2026 is 9 Muharram 1448, Thursday the 10th.
        val core = core(makkah("2026-06-23", "12:00"), fastsOn(SunnahFast.ASHURA))

        assertEquals(
            listOf(
                Triple("2026-06-23", HijriDate(1448, 1, 9), setOf(SunnahFast.ASHURA)),
                Triple("2026-06-24", HijriDate(1448, 1, 10), setOf(SunnahFast.ASHURA)),
            ),
            core.nights(),
        )
    }

    @Test
    fun `the six of Shawwal are announced once, on the night of Eid al-Fitr, never the night before Eid`() {
        // 9 March 2027 is Eid al-Fitr 1448 (a Forbidden Fast Day), Wednesday the 10th is 2 Shawwal.
        assertEquals(
            listOf(Triple("2027-03-09", HijriDate(1448, 10, 2), setOf(SunnahFast.SIX_OF_SHAWWAL))),
            core(makkah("2027-03-08", "12:00"), fastsOn(*allOn)).nights(),
        )
        // The night of 2 Shawwal brings no second six-of-Shawwal reminder (Thursday 3 Shawwal still has its own).
        assertEquals(
            listOf(Triple("2027-03-10", HijriDate(1448, 10, 3), setOf(SunnahFast.MONDAY_THURSDAY))),
            core(makkah("2027-03-10", "06:00"), fastsOn(*allOn)).nights(),
        )
    }

    @Test
    fun `no reminder comes before Eid al-Adha or the days of Tashreeq, not even before 13 Dhu al-Hijjah`() {
        // Eid al-Adha 1448 is 16 May 2027, Tashreeq 17 (a Monday) to 19 May; Thursday the 20th is 14 Dhu al-Hijjah.
        assertEquals(emptyList(), core(makkah("2027-05-16", "12:00"), fastsOn(*allOn)).nights())
        assertEquals(
            listOf(Triple("2027-05-19", HijriDate(1448, 12, 14), setOf(SunnahFast.MONDAY_THURSDAY, SunnahFast.WHITE_DAYS))),
            core(makkah("2027-05-18", "12:00"), fastsOn(*allOn)).nights(),
        )
    }

    @Test
    fun `in Ramadan neither Mondays and Thursdays nor the white days are announced`() {
        // Monday 8 Feb 2027 is 1 Ramadan 1448; Monday the 15th the 8th; Saturday the 20th the 13th.
        assertEquals(emptyList(), core(makkah("2027-02-07", "12:00"), fastsOn(*allOn)).nights())
        assertEquals(emptyList(), core(makkah("2027-02-14", "12:00"), fastsOn(*allOn)).nights())
        assertEquals(emptyList(), core(makkah("2027-02-19", "12:00"), fastsOn(*allOn)).nights())
    }

    @Test
    fun `nothing is planned with the master switch off`() {
        val settings = fastsOn(*allOn, alerts = AlertSettings(enabled = false))

        assertEquals(emptyList(), core(makkah("2026-11-22", "12:00"), settings).alertPlan().fastReminders)
    }

    @Test
    fun `after travel the reminder follows Isha where the user is now`() {
        val cairo = Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo"))
        val at = ZonedDateTime.of(LocalDate.parse("2026-11-15"), LocalTime.NOON, cairo.zone)
        val inCairo = core(at, fastsOn(SunnahFast.MONDAY_THURSDAY, location = cairo))
        val inMakkah = core(at, fastsOn(SunnahFast.MONDAY_THURSDAY))

        val reminder = inCairo.alertPlan().fastReminders.single()

        assertEquals(inCairo.isha("2026-11-15").plus(Duration.ofMinutes(30)), reminder.instant)
        assertEquals(true, reminder.instant.isAfter(inMakkah.alertPlan().fastReminders.single().instant))
    }

    @Test
    fun `the Hijri offset moves the fast and its reminder`() {
        // With +1, Arafah is Friday 14 May 2027, announced on Thursday night.
        val core = core(makkah("2027-05-13", "12:00"), fastsOn(SunnahFast.ARAFAH, hijriOffsetDays = 1))

        assertEquals(listOf(Triple("2027-05-13", HijriDate(1448, 12, 9), setOf(SunnahFast.ARAFAH))), core.nights())
    }
}
