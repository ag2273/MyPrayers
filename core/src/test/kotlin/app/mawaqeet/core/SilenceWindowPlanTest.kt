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
import kotlin.test.assertTrue

/** Prayer Silence (#61): the Silence Windows `PrayerCore.alertPlan()` plans next to the alerts. */
class SilenceWindowPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun planAt(time: ZonedDateTime, settings: PrayerSettings): AlertPlan =
        PrayerCore(settings, Clock.fixed(time.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog()).alertPlan()

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun silenceOn(alerts: AlertSettings = AlertSettings(), location: Location = PrayerSettings.Default.location) =
        PrayerSettings.Default.copy(alerts = alerts.copy(prayerSilence = PrayerSilence(enabled = true)), location = location)

    private fun SilenceWindow.local(zone: ZoneId) =
        start.atZone(zone).truncatedTo(ChronoUnit.MINUTES).toLocalDateTime().toString() to
            end.atZone(zone).truncatedTo(ChronoUnit.MINUTES).toLocalDateTime().toString()

    @Test
    fun `with Prayer Silence on, every Prayer in the 48 hours has a Silence Window from its Prayer Time to 20 minutes later`() {
        // Makkah, Umm al-Qura, 2026-11-08: Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11; Fajr 05:09.
        val plan = planAt(makkah("2026-11-08", "12:00"), silenceOn())

        assertEquals(10, plan.silenceWindows.size)
        assertEquals(
            listOf(
                Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                Prayer.FAJR,
            ),
            plan.silenceWindows.map { it.prayer },
        )
        assertEquals(
            listOf(
                "2026-11-08T12:04" to "2026-11-08T12:24",
                "2026-11-08T15:17" to "2026-11-08T15:37",
                "2026-11-08T17:41" to "2026-11-08T18:01",
                "2026-11-08T19:11" to "2026-11-08T19:31",
            ),
            plan.silenceWindows.take(4).map { it.local(riyadh) },
        )
        // Each starts at its Prayer's Adhan Alert, i.e. the Prayer Time itself.
        assertEquals(plan.alerts.map { it.prayerTime }, plan.silenceWindows.map { it.start })
        assertTrue(plan.silenceWindows.all { Duration.between(it.start, it.end) == Duration.ofMinutes(20) })
        assertTrue(plan.silenceWindows.all { it.mode == SilenceMode.SILENT })
        assertEquals(LocalDate.parse("2026-11-08"), plan.silenceWindows.first().prayerDay)
    }

    @Test
    fun `Prayer Silence is off by default, so the plan holds no Silence Window`() {
        assertEquals(false, AlertSettings().prayerSilence.enabled)
        val plan = planAt(makkah("2026-11-08", "12:00"), PrayerSettings.Default)

        assertTrue(plan.silenceWindows.isEmpty())
        assertEquals(10, plan.alerts.size)
    }

    @Test
    fun `Silence Windows are planned even with every app alert turned off`() {
        val plan = planAt(makkah("2026-11-08", "12:00"), silenceOn(AlertSettings(enabled = false)))

        assertTrue(plan.alerts.isEmpty())
        assertEquals(10, plan.silenceWindows.size)
    }

    @Test
    fun `a Prayer whose Adhan Alert is off still gets its Silence Window at the Prayer Time`() {
        val alerts = AlertSettings().with(Prayer.ASR, PrayerAlertSettings(adhanAlert = false))
        val plan = planAt(makkah("2026-11-08", "12:00"), silenceOn(alerts))

        assertEquals("2026-11-08T15:17" to "2026-11-08T15:37", plan.silenceWindows.first { it.prayer == Prayer.ASR }.local(riyadh))
    }

    @Test
    fun `a Silence Window in progress stays in the plan, so the alarm that restores the sound is kept`() {
        // Dhuhr 12:04 to 12:24: at 12:10 the window has started but not ended.
        val plan = planAt(makkah("2026-11-08", "12:10"), silenceOn())

        assertEquals(Prayer.DHUHR, plan.silenceWindows.first().prayer)
        assertEquals("2026-11-08T12:04" to "2026-11-08T12:24", plan.silenceWindows.first().local(riyadh))
        // The Adhan Alert has passed, so it is no longer planned.
        assertEquals(Prayer.ASR, plan.alerts.first().prayer)
    }

    @Test
    fun `a Silence Window that ends exactly now is not planned again`() {
        val dhuhr = planAt(makkah("2026-11-08", "12:00"), silenceOn()).silenceWindows.first()

        val plan = planAt(dhuhr.end.atZone(riyadh), silenceOn())

        assertEquals(Prayer.ASR, plan.silenceWindows.first().prayer)
    }

    @Test
    fun `after Isha the next Silence Window is tomorrow's Fajr, on its own Prayer Day`() {
        val plan = planAt(makkah("2026-11-08", "20:00"), silenceOn())

        val fajr = plan.silenceWindows.first()
        assertEquals(Prayer.FAJR, fajr.prayer)
        assertEquals(LocalDate.parse("2026-11-09"), fajr.prayerDay)
        assertEquals("2026-11-09T05:10" to "2026-11-09T05:30", fajr.local(riyadh))
    }

    @Test
    fun `Friday's Dhuhr window is marked as Jumu'ah`() {
        // 2026-11-13 is a Friday.
        val plan = planAt(makkah("2026-11-12", "20:00"), silenceOn())

        val jumuah = plan.silenceWindows.single { it.isJumuah }
        assertEquals(Prayer.DHUHR, jumuah.prayer)
        assertEquals(LocalDate.parse("2026-11-13"), jumuah.prayerDay)
    }

    @Test
    fun `across the end of summer time each window still lasts 20 real minutes and follows the sun`() {
        // London leaves BST (UTC+1) for GMT at 01:00 UTC on Sunday 2026-10-25.
        val london = ZoneId.of("Europe/London")
        val settings = silenceOn(location = Location(51.5074, -0.1278, london))
            .copy(method = CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val plan = planAt(ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, london), settings)

        assertEquals(plan.alerts.map { it.prayerTime }, plan.silenceWindows.map { it.start })
        assertTrue(plan.silenceWindows.all { Duration.between(it.start, it.end) == Duration.ofMinutes(20) })
        val saturday = plan.silenceWindows.filter { it.prayerDay == LocalDate.parse("2026-10-24") }.associateBy { it.prayer }
        val sunday = plan.silenceWindows.filter { it.prayerDay == LocalDate.parse("2026-10-25") }.associateBy { it.prayer }
        assertEquals(5, sunday.size)
        for ((prayer, window) in saturday) {
            val realGap = Duration.between(window.start, sunday.getValue(prayer).start)
            assertTrue((realGap.toMinutes() - 24 * 60) in -5..5, "$prayer is $realGap after the previous day's")
        }
    }

    @Test
    fun `after travel the windows follow the new location's Prayer Times`() {
        // The same instant, seen from Cairo (Egyptian method): Dhuhr 2026-11-08 at 11:39 Cairo time.
        val cairo = ZoneId.of("Africa/Cairo")
        val settings = silenceOn(location = Location(30.0444, 31.2357, cairo)).copy(method = CalculationMethod.EGYPTIAN)
        val now = ZonedDateTime.of(2026, 11, 8, 11, 0, 0, 0, cairo)
        val plan = planAt(now, settings)

        assertEquals(plan.alerts.map { it.prayerTime }, plan.silenceWindows.map { it.start })
        val dhuhr = plan.silenceWindows.first()
        assertEquals(Prayer.DHUHR, dhuhr.prayer)
        assertEquals(LocalDate.parse("2026-11-08"), dhuhr.start.atZone(cairo).toLocalDate())
        assertEquals(11, dhuhr.start.atZone(cairo).hour)
        assertEquals(Duration.ofMinutes(20), Duration.between(dhuhr.start, dhuhr.end))
    }
}
