package app.mawaqeet.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")

private fun displayDayAt(dateTime: String, hijriOffsetDays: Int = 0): DisplayDay = PrayerCore(
    PrayerSettings.Default.copy(hijriOffsetDays = hijriOffsetDays),
    Clock.fixed(LocalDateTime.parse(dateTime).atZone(RIYADH).toInstant(), ZoneOffset.UTC),
    InMemoryPrayerLog(),
).displayDay()

// Expected dates: the Umm al-Qura calendar (ummulqura.org.sa): 8 Nov 2026 = 28 Jumada al-Ula 1448,
// and 1 Ramadan 1448 = 8 Feb 2027.
class HijriDateTest {

    @Test
    fun `the display day carries its Umm al-Qura Hijri date`() {
        assertEquals(HijriDate(1448, 5, 28), displayDayAt("2026-11-08T10:00").hijriDate)
    }

    @Test
    fun `the Hijri offset shifts the Hijri date but not the Gregorian date`() {
        val later = displayDayAt("2026-11-08T10:00", hijriOffsetDays = 1)
        val earlier = displayDayAt("2026-11-08T10:00", hijriOffsetDays = -2)

        assertEquals(LocalDate.of(2026, 11, 8), later.date)
        assertEquals(HijriDate(1448, 5, 29), later.hijriDate)
        assertEquals(HijriDate(1448, 5, 26), earlier.hijriDate)
    }

    @Test
    fun `a +1 offset on the last day of Sha'ban shows 1 Ramadan`() {
        assertEquals(HijriDate(1448, 9, 1), displayDayAt("2027-02-07T10:00", hijriOffsetDays = 1).hijriDate)
        assertEquals(HijriDate(1448, 9, 1), displayDayAt("2027-02-08T10:00").hijriDate)
    }

    @Test
    fun `a Hijri offset beyond two days is rejected`() {
        assertFailsWith<IllegalArgumentException> { PrayerSettings.Default.copy(hijriOffsetDays = 3) }
        assertFailsWith<IllegalArgumentException> { PrayerSettings.Default.copy(hijriOffsetDays = -3) }
    }
}
