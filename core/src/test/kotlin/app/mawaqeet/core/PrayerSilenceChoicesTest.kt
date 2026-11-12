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

/** Prayer Silence choices (#68): which Prayers, how long, Jumu'ah, and silent or vibrate, as the alert plan carries them. */
class PrayerSilenceChoicesTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun planAt(time: ZonedDateTime, silence: PrayerSilence): AlertPlan {
        val settings = PrayerSettings.Default.copy(alerts = AlertSettings(prayerSilence = silence))
        return PrayerCore(settings, Clock.fixed(time.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog()).alertPlan()
    }

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private val SilenceWindow.length: Duration get() = Duration.between(start, end)

    @Test
    fun `once Prayer Silence is on every Prayer is chosen, and only the chosen Prayers get Silence Windows`() {
        assertEquals(Prayer.entries.toSet(), PrayerSilence(enabled = true).prayers)

        val silence = PrayerSilence(enabled = true).with(Prayer.FAJR, false).with(Prayer.ASR, false)
        val plan = planAt(makkah("2026-11-08", "12:00"), silence)

        assertEquals(
            listOf(Prayer.DHUHR, Prayer.MAGHRIB, Prayer.ISHA, Prayer.DHUHR, Prayer.MAGHRIB, Prayer.ISHA),
            plan.silenceWindows.map { it.prayer },
        )
        // The alerts are untouched.
        assertEquals(10, plan.alerts.size)
    }

    @Test
    fun `Friday Dhuhr is Jumu'ah and lasts 45 minutes by default, every other window 20`() {
        // 2026-11-13 is a Friday; from Thursday evening the plan covers Friday and Saturday morning.
        val plan = planAt(makkah("2026-11-12", "20:00"), PrayerSilence(enabled = true))

        val jumuah = plan.silenceWindows.single { it.isJumuah }
        assertEquals(Prayer.DHUHR, jumuah.prayer)
        assertEquals(LocalDate.parse("2026-11-13"), jumuah.prayerDay)
        assertEquals(Duration.ofMinutes(45), jumuah.length)
        assertTrue(plan.silenceWindows.filterNot { it.isJumuah }.all { it.length == Duration.ofMinutes(20) })
    }

    @Test
    fun `custom durations apply to every window and to Jumu'ah`() {
        val silence = PrayerSilence(enabled = true, minutes = 30, jumuahMinutes = 60)
        val plan = planAt(makkah("2026-11-12", "20:00"), silence)

        assertEquals(Duration.ofMinutes(60), plan.silenceWindows.single { it.isJumuah }.length)
        val others = plan.silenceWindows.filterNot { it.isJumuah }
        assertTrue(others.isNotEmpty())
        assertTrue(others.all { it.length == Duration.ofMinutes(30) })
        // Thursday's Dhuhr would not be Jumu'ah; check a non-Friday Dhuhr too.
        val saturday = planAt(makkah("2026-11-14", "11:00"), silence).silenceWindows.first { it.prayer == Prayer.DHUHR }
        assertEquals(false, saturday.isJumuah)
        assertEquals(Duration.ofMinutes(30), saturday.length)
    }

    @Test
    fun `Jumu'ah off still leaves Friday's other Prayers silenced`() {
        val plan = planAt(makkah("2026-11-12", "20:00"), PrayerSilence(enabled = true).with(Prayer.DHUHR, false))

        assertTrue(plan.silenceWindows.none { it.prayer == Prayer.DHUHR })
        assertTrue(plan.silenceWindows.any { it.prayerDay == LocalDate.parse("2026-11-13") && it.prayer == Prayer.ASR })
    }

    @Test
    fun `the chosen mode is carried in every window, silent by default`() {
        assertEquals(SilenceMode.SILENT, PrayerSilence().mode)

        val plan = planAt(makkah("2026-11-12", "20:00"), PrayerSilence(enabled = true, mode = SilenceMode.VIBRATE))

        assertTrue(plan.silenceWindows.isNotEmpty())
        assertTrue(plan.silenceWindows.all { it.mode == SilenceMode.VIBRATE })
    }

    @Test
    fun `durations stay within their allowed ranges`() {
        assertEquals(20, PrayerSilence().minutes)
        assertEquals(45, PrayerSilence().jumuahMinutes)
        assertTrue(runCatching { PrayerSilence(minutes = PrayerSilence.MINUTES_RANGE.first - 1) }.isFailure)
        assertTrue(runCatching { PrayerSilence(jumuahMinutes = PrayerSilence.JUMUAH_MINUTES_RANGE.last + 1) }.isFailure)
    }

    @Test
    fun `each tap moves a duration by 5 minutes, and never past its range`() {
        val silence = PrayerSilence(enabled = true)

        assertEquals(25, silence.stepped(jumuah = false, steps = 1).minutes)
        assertEquals(45, silence.stepped(jumuah = false, steps = 1).jumuahMinutes)
        assertEquals(35, silence.stepped(jumuah = true, steps = -2).jumuahMinutes)
        assertEquals(20, silence.stepped(jumuah = true, steps = -2).minutes)
        // 5 to 60 minutes; Jumu'ah 15 to 120.
        assertEquals(5, PrayerSilence(minutes = 10).stepped(jumuah = false, steps = -3).minutes)
        assertEquals(60, PrayerSilence(minutes = 55).stepped(jumuah = false, steps = 4).minutes)
        assertEquals(15, PrayerSilence(jumuahMinutes = 20).stepped(jumuah = true, steps = -1).stepped(jumuah = true, steps = -1).jumuahMinutes)
        assertEquals(120, PrayerSilence(jumuahMinutes = 115).stepped(jumuah = true, steps = 2).jumuahMinutes)
    }
}
