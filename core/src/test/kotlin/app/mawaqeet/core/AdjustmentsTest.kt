package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdjustmentsTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun core(settings: PrayerSettings, at: String) =
        PrayerCore(settings, Clock.fixed(LocalDateTime.parse(at).atZone(riyadh).toInstant(), riyadh), InMemoryPrayerLog())

    @Test
    fun `each Prayer moves by its own adjustment and Sunrise never moves`() {
        // Makkah, Umm al-Qura, 2026-11-08: Fajr 05:09, Sunrise 06:27, Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11.
        val settings = PrayerSettings.Default.copy(
            adjustments = mapOf(Prayer.FAJR to 5, Prayer.ASR to -30, Prayer.ISHA to 30),
        )
        val day = core(settings, "2026-11-08T10:00").displayDay()

        assertEquals(LocalTime.of(5, 14), day.timeOf(Prayer.FAJR).toLocalTime())
        assertEquals(LocalTime.of(6, 27), day.sunrise.toLocalTime())
        assertEquals(LocalTime.of(12, 4), day.timeOf(Prayer.DHUHR).toLocalTime())
        assertEquals(LocalTime.of(14, 47), day.timeOf(Prayer.ASR).toLocalTime())
        assertEquals(LocalTime.of(17, 41), day.timeOf(Prayer.MAGHRIB).toLocalTime())
        assertEquals(LocalTime.of(19, 41), day.timeOf(Prayer.ISHA).toLocalTime())
    }

    @Test
    fun `adjustments reach the alert plan`() {
        val settings = PrayerSettings.Default.copy(adjustments = mapOf(Prayer.DHUHR to -10))
        val plan = core(settings, "2026-11-08T10:00").alertPlan()

        val dhuhr = plan.alerts.first { it.prayer == Prayer.DHUHR && it.kind == AlertKind.ADHAN_ALERT }
        assertEquals(LocalDateTime.parse("2026-11-08T11:54"), dhuhr.instant.atZone(riyadh).toLocalDateTime())
    }

    @Test
    fun `an adjusted Prayer becomes markable only at its adjusted time`() {
        val settings = PrayerSettings.Default.copy(adjustments = mapOf(Prayer.DHUHR to 10))
        // 12:10: past the calculated 12:04, before the adjusted 12:14.
        val day = core(settings, "2026-11-08T12:10").currentPrayerDay()

        assertEquals(LogState.NOT_YET_DUE, day.stateOf(Prayer.DHUHR))
    }

    @Test
    fun `adjustments beyond 30 minutes either way are rejected`() {
        assertFailsWith<IllegalArgumentException> { PrayerSettings.Default.copy(adjustments = mapOf(Prayer.FAJR to 31)) }
        assertFailsWith<IllegalArgumentException> { PrayerSettings.Default.copy(adjustments = mapOf(Prayer.ISHA to -31)) }
    }
}

class RamadanIshaTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun ishaAfterMaghrib(date: String, settings: PrayerSettings = PrayerSettings.Default): Duration {
        val day = PrayerCore(
            settings,
            Clock.fixed(LocalDate.parse(date).atTime(10, 0).atZone(riyadh).toInstant(), riyadh),
            InMemoryPrayerLog(),
        ).displayDay()
        return Duration.between(day.timeOf(Prayer.MAGHRIB), day.timeOf(Prayer.ISHA))
    }

    @Test
    fun `under Umm al-Qura Isha is 120 minutes after Maghrib during Ramadan and 90 otherwise`() {
        // Umm al-Qura calendar: 1 Ramadan 1447 is 2026-02-18, 30 Ramadan is 2026-03-19.
        assertEquals(Duration.ofMinutes(90), ishaAfterMaghrib("2026-02-17"))
        assertEquals(Duration.ofMinutes(120), ishaAfterMaghrib("2026-02-18"))
        assertEquals(Duration.ofMinutes(120), ishaAfterMaghrib("2026-03-19"))
        assertEquals(Duration.ofMinutes(90), ishaAfterMaghrib("2026-03-20"))
    }

    @Test
    fun `Ramadan follows the user's Hijri offset`() {
        val dayLater = PrayerSettings.Default.copy(hijriOffsetDays = -1)
        assertEquals(Duration.ofMinutes(90), ishaAfterMaghrib("2026-02-18", dayLater))
        assertEquals(Duration.ofMinutes(120), ishaAfterMaghrib("2026-03-20", dayLater))
    }

    @Test
    fun `other methods keep their Isha in Ramadan`() {
        val egyptian = PrayerSettings.Default.copy(method = CalculationMethod.EGYPTIAN)
        assertEquals(ishaAfterMaghrib("2026-02-17", egyptian).toMinutes(), ishaAfterMaghrib("2026-02-18", egyptian).toMinutes(), )
    }

    @Test
    fun `the Ramadan Isha adjustment adds to the user's own Isha adjustment`() {
        val later = PrayerSettings.Default.copy(adjustments = mapOf(Prayer.ISHA to 5))
        assertEquals(Duration.ofMinutes(125), ishaAfterMaghrib("2026-03-01", later))
    }
}
