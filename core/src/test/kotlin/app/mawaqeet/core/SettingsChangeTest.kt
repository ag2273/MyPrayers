package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")

private fun coreAt(
    dateTime: String,
    method: CalculationMethod,
    asr: AsrConvention = AsrConvention.STANDARD,
    log: PrayerLogRepository = InMemoryPrayerLog(),
) = PrayerCore(
    PrayerSettings.Default.copy(method = method, asrConvention = asr),
    Clock.fixed(LocalDateTime.parse(dateTime).atZone(RIYADH).toInstant(), ZoneOffset.UTC),
    log,
)

/** Changing the Calculation Method or Asr Convention changes the answers; it never touches the Prayer Log. */
class SettingsChangeTest {

    @Test
    fun `every Calculation Method gives five ordered Prayer Times in Makkah`() {
        CalculationMethod.entries.forEach { method ->
            val times = coreAt("2026-11-08T10:00", method).displayDay().prayerTimes.map { it.time }
            assertEquals(times.sorted(), times, "$method")
            assertEquals(5, times.size)
        }
    }

    @Test
    fun `the Egyptian method's deeper Fajr angle puts Fajr a few minutes before Umm al-Qura's`() {
        // Egyptian uses 19.5° below the horizon, Umm al-Qura 18.5°: one degree is roughly 4-5 minutes in Makkah.
        val egyptian = coreAt("2026-11-08T10:00", CalculationMethod.EGYPTIAN).displayDay().timeOf(Prayer.FAJR)
        val ummAlQura = coreAt("2026-11-08T10:00", CalculationMethod.UMM_AL_QURA).displayDay().timeOf(Prayer.FAJR)

        val earlier = Duration.between(egyptian, ummAlQura)
        assertTrue(earlier >= Duration.ofMinutes(3) && earlier <= Duration.ofMinutes(7), "$earlier")
    }

    @Test
    fun `the next Prayer follows the chosen Calculation Method`() {
        // Makkah, 8 November 2026: Umm al-Qura Isha is Maghrib (17:41) + 90 min = 19:11; the Egyptian 17.5° Isha
        // falls about 75 minutes after Maghrib, before 19:05.
        assertEquals(Prayer.ISHA, coreAt("2026-11-08T19:05", CalculationMethod.UMM_AL_QURA).displayDay().next.prayer)
        val egyptian = coreAt("2026-11-08T19:05", CalculationMethod.EGYPTIAN).displayDay().next
        assertEquals(Prayer.FAJR, egyptian.prayer)
        assertEquals(LocalDate.parse("2026-11-09"), egyptian.time.toLocalDate())
    }

    @Test
    fun `the Hanafi Asr Convention puts Asr later than Standard`() {
        // Hanafi Asr waits for a shadow of twice the object's length (plus its noon shadow), Standard for once.
        val standard = coreAt("2026-11-08T10:00", CalculationMethod.UMM_AL_QURA).displayDay().timeOf(Prayer.ASR)
        val hanafi = coreAt("2026-11-08T10:00", CalculationMethod.UMM_AL_QURA, AsrConvention.HANAFI)
            .displayDay().timeOf(Prayer.ASR)

        assertTrue(Duration.between(standard, hanafi) > Duration.ofMinutes(45))
    }

    @Test
    fun `a recalculation keeps existing marks when the Calculation Method changes`() {
        // Isha is marked at 19:05 under the Egyptian method; switching to Umm al-Qura moves Isha to 19:11.
        val log = InMemoryPrayerLog()
        val today = LocalDate.parse("2026-11-08")
        assertEquals(LogCommandResult.DONE, coreAt("2026-11-08T19:05", CalculationMethod.EGYPTIAN, log = log).mark(today, Prayer.ISHA))

        val day = coreAt("2026-11-08T20:00", CalculationMethod.UMM_AL_QURA, log = log).currentPrayerDay()

        assertEquals(today, day.date)
        assertEquals(LogState.PRAYED, day.stateOf(Prayer.ISHA))
    }
}
