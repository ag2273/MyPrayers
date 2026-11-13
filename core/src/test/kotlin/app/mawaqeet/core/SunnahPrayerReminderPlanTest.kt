package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Last Third of the Night and the Sunnah Prayer Reminders (#96), as `PrayerCore.displayDay()` and
 * `PrayerCore.alertPlan()` give them: Duha at Sunrise + its minutes (never after Dhuhr − 10), Qiyam at the start of the
 * Last Third of the Night, Maghrib + ⅔ × (next Fajr − Maghrib).
 */
class SunnahPrayerReminderPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun core(at: ZonedDateTime, settings: PrayerSettings = PrayerSettings.Default) =
        PrayerCore(settings, Clock.fixed(at.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog())

    private fun sunnahOn(
        duha: DuhaReminder = DuhaReminder(enabled = true),
        qiyam: QiyamReminder = QiyamReminder(enabled = true),
        alerts: AlertSettings = AlertSettings(),
        base: PrayerSettings = PrayerSettings.Default,
    ) = base.copy(alerts = alerts.copy(worshipReminders = WorshipReminders(sunnahPrayers = SunnahPrayerReminders(duha, qiyam))))

    private fun PrayerCore.time(date: String, prayer: Prayer): ZonedDateTime =
        prayerDay(LocalDate.parse(date)).prayerTimes.single { it.prayer == prayer }.time

    private fun PrayerCore.sunrise(date: String) = prayerDay(LocalDate.parse(date)).sunrise

    private fun AlertPlan.of(kind: WorshipReminderKind) = worshipReminders.filter { it.kind == kind }

    /** Two thirds of the night from [date]'s Maghrib to the next day's Fajr. */
    private fun PrayerCore.twoThirdsOfTheNight(date: String): Duration {
        val maghrib = time(date, Prayer.MAGHRIB)
        val nextFajr = time(LocalDate.parse(date).plusDays(1).toString(), Prayer.FAJR)
        return Duration.between(maghrib, nextFajr).multipliedBy(2).dividedBy(3)
    }

    // --- The Last Third of the Night on the display day ---

    @Test
    fun `after Isha the display day's Last Third starts two thirds of the way from Maghrib to the next Fajr`() {
        val core = core(makkah("2026-11-15", "21:00"))
        val day = core.displayDay()

        val maghrib = core.time("2026-11-15", Prayer.MAGHRIB)
        assertEquals(maghrib.plus(core.twoThirdsOfTheNight("2026-11-15")).toInstant(), day.lastThirdOfNight.toInstant())
        // A Makkah night in November is about 12h40m from Maghrib (~17:40) to Fajr (~05:05): the last third from ~01:30.
        val local = day.lastThirdOfNight.withZoneSameInstant(riyadh).toLocalTime()
        assertTrue(local.isAfter(LocalTime.of(1, 0)) && local.isBefore(LocalTime.of(2, 0)), "last third at $local")
    }

    @Test
    fun `from midnight until Fajr the Last Third is still the running Prayer Day's night`() {
        val evening = core(makkah("2026-11-15", "23:00")).displayDay().lastThirdOfNight
        val night = core(makkah("2026-11-16", "03:00")).displayDay().lastThirdOfNight

        assertEquals(evening, night)
    }

    @Test
    fun `the Last Third follows each season's own night, long in winter and short in summer`() {
        val london = Location(51.5074, -0.1278, ZoneId.of("Europe/London"))
        val settings = PrayerSettings.Default.copy(location = london, method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        fun lastThird(date: String) = core(ZonedDateTime.of(LocalDate.parse(date), LocalTime.NOON, london.zone), settings).let { core ->
            val day = core.displayDay()
            assertEquals(
                day.timeOf(Prayer.MAGHRIB).plus(core.twoThirdsOfTheNight(date)).toInstant(),
                day.lastThirdOfNight.toInstant(),
                date,
            )
            // Maghrib to the Last Third is two thirds of the night: half of that is the last third's length.
            Duration.between(day.timeOf(Prayer.MAGHRIB), day.lastThirdOfNight).dividedBy(2)
        }

        // London's night (Maghrib to Fajr) is over 14 hours in December and under 7 in June, so its last third is
        // over 4h40m long in winter and under 2h20m in summer.
        val winter = lastThird("2026-12-21")
        val summer = lastThird("2026-06-21")
        assertTrue(winter > Duration.ofMinutes(280), "winter's last third $winter")
        assertTrue(summer < Duration.ofMinutes(140), "summer's last third $summer")
    }

    @Test
    fun `at a high latitude the Last Third uses the adjusted Fajr`() {
        // At 60° N in June Fajr comes from the seventh-of-the-night rule.
        val oslo = Location(59.9139, 10.7522, ZoneId.of("Europe/Oslo"))
        val settings = PrayerSettings.Default.copy(location = oslo, method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val core = core(ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, oslo.zone), settings)
        val day = core.displayDay()

        assertTrue(day.highLatitudeRuleApplied)
        val maghrib = day.timeOf(Prayer.MAGHRIB)
        assertEquals(maghrib.plus(core.twoThirdsOfTheNight("2026-06-21")).toInstant(), day.lastThirdOfNight.toInstant())
        assertTrue(day.lastThirdOfNight.isAfter(day.timeOf(Prayer.ISHA)))
    }

    // --- Duha ---

    @Test
    fun `both Sunnah Prayer Reminders are off by default, Duha at an hour after Sunrise`() {
        assertEquals(SunnahPrayerReminders(DuhaReminder(enabled = false, minutes = 60), QiyamReminder(enabled = false)), WorshipReminders().sunnahPrayers)

        val plan = core(makkah("2026-11-15", "00:30")).alertPlan()
        assertEquals(emptyList(), plan.of(WorshipReminderKind.DUHA) + plan.of(WorshipReminderKind.QIYAM))
    }

    @Test
    fun `the Duha reminder comes its minutes after Sunrise, once each Prayer Day`() {
        val core = core(makkah("2026-11-15", "05:30"), sunnahOn(qiyam = QiyamReminder()))
        val plan = core.alertPlan()

        val duha = plan.of(WorshipReminderKind.DUHA)
        assertEquals(listOf("2026-11-15", "2026-11-16"), duha.map { it.prayerDay.toString() })
        assertEquals(core.sunrise("2026-11-15").plusHours(1).toInstant(), duha.first().instant)
        assertEquals(emptyList(), plan.of(WorshipReminderKind.QIYAM))

        for (minutes in DuhaReminder.MINUTES) {
            val reminder = core(makkah("2026-11-15", "05:30"), sunnahOn(DuhaReminder(enabled = true, minutes = minutes))).alertPlan()
                .of(WorshipReminderKind.DUHA).first()
            assertEquals(Duration.ofMinutes(minutes.toLong()), Duration.between(core.sunrise("2026-11-15").toInstant(), reminder.instant), "$minutes")
        }
    }

    @Test
    fun `only 20, 30, 45, 60 or 90 minutes after Sunrise is allowed`() {
        assertEquals(listOf(20, 30, 45, 60, 90), DuhaReminder.MINUTES)
        assertFailsWith<IllegalArgumentException> { DuhaReminder(minutes = 15) }
        assertFailsWith<IllegalArgumentException> { DuhaReminder(minutes = 120) }
    }

    @Test
    fun `where Dhuhr comes soon after Sunrise the Duha reminder comes 10 minutes before Dhuhr, never later`() {
        // Rovaniemi (66.5° N) a week before the winter solstice: Sunrise near 11:30, Dhuhr near 12:20.
        val rovaniemi = Location(66.5039, 25.7294, ZoneId.of("Europe/Helsinki"))
        val base = PrayerSettings.Default.copy(location = rovaniemi, method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val at = ZonedDateTime.of(2026, 12, 14, 8, 0, 0, 0, rovaniemi.zone)
        val core = core(at, sunnahOn(DuhaReminder(enabled = true, minutes = 90), base = base))
        val sunrise = core.sunrise("2026-12-14").toInstant()
        val latest = core.time("2026-12-14", Prayer.DHUHR).minusMinutes(10).toInstant()
        assertTrue(sunrise.plus(Duration.ofMinutes(90)).isAfter(latest), "the case must need the clamp")

        val duha = core.alertPlan().of(WorshipReminderKind.DUHA).first()

        assertEquals(LocalDate.parse("2026-12-14"), duha.prayerDay)
        assertEquals(latest, duha.instant)
    }

    @Test
    fun `across the start of summer time the Duha reminder keeps its real minutes after that day's Sunrise`() {
        // London enters BST at 01:00 UTC on Sunday 2026-03-29.
        val london = Location(51.5074, -0.1278, ZoneId.of("Europe/London"))
        val base = PrayerSettings.Default.copy(location = london, method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val core = core(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, london.zone), sunnahOn(base = base))

        val duha = core.alertPlan().of(WorshipReminderKind.DUHA)

        val sunday = duha.single { it.prayerDay == LocalDate.parse("2026-03-29") }
        assertEquals(Duration.ofHours(1), Duration.between(core.sunrise("2026-03-29").toInstant(), sunday.instant))
    }

    // --- Qiyam ---

    @Test
    fun `the Qiyam reminder comes at the start of the Last Third, on the Prayer Day of that night's Maghrib`() {
        val core = core(makkah("2026-11-15", "21:00"), sunnahOn(duha = DuhaReminder()))
        val plan = core.alertPlan()

        val qiyam = plan.of(WorshipReminderKind.QIYAM)
        assertEquals(LocalDate.parse("2026-11-15"), qiyam.first().prayerDay)
        assertEquals(core.displayDay().lastThirdOfNight.toInstant(), qiyam.first().instant)
        assertEquals(listOf("2026-11-15", "2026-11-16"), qiyam.map { it.prayerDay.toString() })
        assertEquals(emptyList(), plan.of(WorshipReminderKind.DUHA))
    }

    @Test
    fun `a Qiyam reminder already past is not planned again`() {
        val core = core(makkah("2026-11-16", "03:00"), sunnahOn(duha = DuhaReminder()))

        val qiyam = core.alertPlan().of(WorshipReminderKind.QIYAM)

        // The night of 15 November's last third began before 03:00.
        assertEquals(listOf("2026-11-16", "2026-11-17"), qiyam.map { it.prayerDay.toString() })
    }

    @Test
    fun `across the end of summer time the Qiyam reminder is two thirds of the real night`() {
        // London leaves BST at 01:00 UTC on Sunday 2026-10-25, in the night of Saturday the 24th.
        val london = Location(51.5074, -0.1278, ZoneId.of("Europe/London"))
        val base = PrayerSettings.Default.copy(location = london, method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val core = core(ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, london.zone), sunnahOn(duha = DuhaReminder(), base = base))

        val saturday = core.alertPlan().of(WorshipReminderKind.QIYAM).single { it.prayerDay == LocalDate.parse("2026-10-24") }

        assertEquals(core.time("2026-10-24", Prayer.MAGHRIB).plus(core.twoThirdsOfTheNight("2026-10-24")).toInstant(), saturday.instant)
        // On the clock the night looks an hour shorter than it is; two thirds of the clock's night would be 40 minutes early.
        val maghrib = core.time("2026-10-24", Prayer.MAGHRIB)
        val fajr = core.time("2026-10-25", Prayer.FAJR)
        val clockNight = Duration.between(maghrib.toLocalDateTime(), fajr.toLocalDateTime())
        assertEquals(Duration.ofHours(1), Duration.between(maghrib, fajr) - clockNight)
        assertEquals(Duration.ofMinutes(40), Duration.between(maghrib.toInstant().plus(clockNight.multipliedBy(2).dividedBy(3)), saturday.instant))
    }

    @Test
    fun `after travel both reminders follow the Prayer Times where the user is now`() {
        val cairo = Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo"))
        val base = PrayerSettings.Default.copy(location = cairo, method = CalculationMethod.EGYPTIAN)
        val core = core(ZonedDateTime.of(2026, 11, 15, 0, 30, 0, 0, cairo.zone), sunnahOn(base = base))
        val plan = core.alertPlan()

        assertEquals(core.sunrise("2026-11-15").plusHours(1).toInstant(), plan.of(WorshipReminderKind.DUHA).first().instant)
        assertEquals(
            core.time("2026-11-15", Prayer.MAGHRIB).plus(core.twoThirdsOfTheNight("2026-11-15")).toInstant(),
            plan.of(WorshipReminderKind.QIYAM).single { it.prayerDay == LocalDate.parse("2026-11-15") }.instant,
        )
    }

    @Test
    fun `nothing is planned with the master switch off`() {
        val plan = core(makkah("2026-11-15", "00:30"), sunnahOn(alerts = AlertSettings(enabled = false))).alertPlan()

        assertEquals(emptyList(), plan.worshipReminders)
    }

    @Test
    fun `with Prayer Silence on, the plan keeps the Silence Windows next to the reminders, which the phone keeps quiet`() {
        // Prayer Silence changes nothing in the plan: the Silence Windows are planned as before, and a reminder that
        // falls inside one is posted without sound by the reminder receiver (#76), as every Worship Reminder is.
        val alerts = AlertSettings(prayerSilence = PrayerSilence(enabled = true))
        val plan = core(makkah("2026-11-15", "00:30"), sunnahOn(alerts = alerts)).alertPlan()

        assertTrue(plan.silenceWindows.isNotEmpty())
        assertTrue(plan.of(WorshipReminderKind.DUHA).isNotEmpty())
        assertTrue(plan.of(WorshipReminderKind.QIYAM).isNotEmpty())
    }

    @Test
    fun `turning Duha off keeps its minutes for when it comes back`() {
        val off = DuhaReminder(enabled = true, minutes = 45).copy(enabled = false)

        assertEquals(45, off.minutes)
    }
}
