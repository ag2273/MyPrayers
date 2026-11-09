package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun planAt(time: ZonedDateTime, settings: PrayerSettings = PrayerSettings.Default): AlertPlan =
        PrayerCore(settings, Clock.fixed(time.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog()).alertPlan()

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun AlertPlan.localMinutes(zone: ZoneId) =
        alerts.map { it.instant.atZone(zone).truncatedTo(ChronoUnit.MINUTES).toLocalDateTime().toString() }

    @Test
    fun `before Dhuhr the plan holds the next 48 hours of Adhan Alerts, in order, without Sunrise`() {
        // Makkah, Umm al-Qura, 2026-11-08: Fajr 05:09, Sunrise 06:27, Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11.
        val plan = planAt(makkah("2026-11-08", "12:00"))

        assertEquals(10, plan.alerts.size)
        assertEquals(
            listOf(
                Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                Prayer.FAJR,
            ),
            plan.alerts.map { it.prayer },
        )
        assertEquals(
            listOf("2026-11-08T12:04", "2026-11-08T15:17", "2026-11-08T17:41", "2026-11-08T19:11"),
            plan.localMinutes(riyadh).take(4),
        )
        // The window ends 48 h from now: the Fajr of 10 November is in, its Dhuhr (~12:04) is not.
        assertEquals(LocalDate.parse("2026-11-10"), plan.alerts.last().instant.atZone(riyadh).toLocalDate())
        assertTrue(plan.alerts.none { it.instant.atZone(riyadh).toLocalTime().hour == 6 }, "no alert at Sunrise")
    }

    @Test
    fun `by default every Prayer has a Short Adhan Adhan Alert delivered as an exact alarm`() {
        val plan = planAt(makkah("2026-11-08", "12:00"))

        assertTrue(plan.alerts.all { it.kind == AlertKind.ADHAN_ALERT })
        assertTrue(plan.alerts.all { it.sound == AlertSound.ShortAdhan })
        assertTrue(plan.alerts.all { it.deliveryMode == DeliveryMode.EXACT })
    }

    @Test
    fun `after Isha the plan starts at tomorrow's Fajr and the next midnight is the coming local midnight`() {
        val plan = planAt(makkah("2026-11-08", "20:00"))

        assertEquals(10, plan.alerts.size)
        assertEquals(Prayer.FAJR, plan.alerts.first().prayer)
        assertEquals(LocalDate.parse("2026-11-09"), plan.alerts.first().prayerDay)
        assertEquals(LocalDate.parse("2026-11-10"), plan.alerts.last().prayerDay)
        assertEquals(Prayer.ISHA, plan.alerts.last().prayer)
        assertEquals(makkah("2026-11-09", "00:00").toInstant(), plan.nextLocalMidnight)
    }

    @Test
    fun `an alert due exactly now is not planned again, so a firing alarm moves on to the next one`() {
        val dhuhr = planAt(makkah("2026-11-08", "12:00")).alerts.first()

        val plan = planAt(dhuhr.instant.atZone(riyadh))

        assertEquals(Prayer.ASR, plan.alerts.first().prayer)
        assertTrue(plan.alerts.none { it.instant == dhuhr.instant })
    }

    @Test
    fun `Friday's Dhuhr alert is announced as Jumu'ah`() {
        // 2026-11-13 is a Friday.
        val plan = planAt(makkah("2026-11-12", "20:00"))

        val jumuah = plan.alerts.filter { it.isJumuah }
        assertEquals(1, jumuah.size)
        assertEquals(Prayer.DHUHR, jumuah.single().prayer)
        assertEquals(LocalDate.parse("2026-11-13"), jumuah.single().prayerDay)
    }

    @Test
    fun `across the end of summer time alerts keep following the sun, so their clock times move an hour earlier`() {
        // London leaves BST (UTC+1) for GMT at 01:00 UTC on Sunday 2026-10-25.
        val london = ZoneId.of("Europe/London")
        val settings = PrayerSettings.Default.copy(
            location = Location(51.5074, -0.1278, london),
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE,
        )
        val plan = planAt(ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, london), settings)

        assertEquals(10, plan.alerts.size)
        val saturday = plan.alerts.filter { it.prayerDay == LocalDate.parse("2026-10-24") }.associateBy { it.prayer }
        val sunday = plan.alerts.filter { it.prayerDay == LocalDate.parse("2026-10-25") }.associateBy { it.prayer }
        assertEquals(5, sunday.size)
        for ((prayer, alert) in saturday) {
            val next = sunday.getValue(prayer)
            // The sun moves a few minutes a day; the clocks move 60.
            val clockShift = Duration.between(next.instant.atZone(london).toLocalTime(), alert.instant.atZone(london).toLocalTime())
            assertTrue(clockShift.toMinutes() in 55..65, "$prayer clock time moved by $clockShift")
            val realGap = Duration.between(alert.instant, next.instant)
            assertTrue((realGap.toMinutes() - 24 * 60) in -5..5, "$prayer is $realGap after the previous day's")
        }
        // Midnight is local: 2026-10-25T00:00 BST.
        assertEquals(Instant.parse("2026-10-24T23:00:00Z"), plan.nextLocalMidnight)
    }
}
