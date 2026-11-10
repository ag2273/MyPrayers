package app.mawaqeet.widget

import app.mawaqeet.R
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.HijriDate
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Plain JVM tests of what the Today's Prayer Times (4×2) and Date strip (4×1) widgets show. */
class TodayTimesContentTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val arabic = DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC)
    private val english = DisplayFormatter(Locale.ENGLISH, DigitStyle.WESTERN)

    private fun at(date: LocalDate, time: String) = ZonedDateTime.of(date, LocalTime.parse(time), riyadh)

    /** Makkah, Umm al-Qura: Fajr 05:09, Sunrise 06:27, Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11. */
    private fun makkahDay(date: LocalDate, next: (List<PrayerTime>) -> PrayerTime, hijriOffset: Int = 0): DisplayDay {
        val times = listOf(
            Prayer.FAJR to "05:09", Prayer.DHUHR to "12:04", Prayer.ASR to "15:17",
            Prayer.MAGHRIB to "17:41", Prayer.ISHA to "19:11",
        ).map { (prayer, time) -> PrayerTime(prayer, at(date, time)) }
        return DisplayDay(date, times, at(date, "06:27"), next(times), HijriDate.of(date, hijriOffset))
    }

    private val sunday = LocalDate.parse("2026-11-08")
    private fun nextIs(prayer: Prayer): (List<PrayerTime>) -> PrayerTime = { times -> times.first { it.prayer == prayer } }
    private val tomorrowFajr: (List<PrayerTime>) -> PrayerTime = { PrayerTime(Prayer.FAJR, at(sunday.plusDays(1), "05:09")) }

    @Test
    fun `the five Prayers in day order with no Sunrise, times in Arabic-Indic digits with no suffix`() {
        val content = TodayTimesContent.of(makkahDay(sunday, nextIs(Prayer.ASR)), arabic)

        assertEquals(
            listOf(R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha),
            content.columns.map { it.nameRes },
        )
        assertEquals(listOf("٠٥:٠٩", "١٢:٠٤", "١٥:١٧", "١٧:٤١", "١٩:١١"), content.columns.map { it.time })
    }

    @Test
    fun `English with Western digits`() {
        val content = TodayTimesContent.of(makkahDay(sunday, nextIs(Prayer.ASR)), english)

        assertEquals(listOf("05:09", "12:04", "15:17", "17:41", "19:11"), content.columns.map { it.time })
        assertEquals(WidgetDate("Sun 8 Nov", "28 Jumada al-Ula 1448 AH"), content.date)
    }

    @Test
    fun `only the next Prayer is highlighted, and the highlight moves with it`() {
        fun highlighted(next: Prayer) =
            TodayTimesContent.of(makkahDay(sunday, nextIs(next)), arabic).columns.filter { it.isNext }.map { it.nameRes }

        assertEquals(listOf(R.string.prayer_fajr), highlighted(Prayer.FAJR))
        assertEquals(listOf(R.string.prayer_dhuhr), highlighted(Prayer.DHUHR))
        assertEquals(listOf(R.string.prayer_isha), highlighted(Prayer.ISHA))
    }

    @Test
    fun `after Isha nothing is highlighted until the date changes at midnight`() {
        val content = TodayTimesContent.of(makkahDay(sunday, tomorrowFajr), arabic)

        assertTrue(content.columns.none { it.isNext })
    }

    @Test
    fun `on a Friday Dhuhr's column is Jumu'ah`() {
        val friday = LocalDate.parse("2026-11-13")
        val content = TodayTimesContent.of(makkahDay(friday, nextIs(Prayer.DHUHR)), arabic)

        assertEquals(R.string.prayer_jumuah, content.columns[1].nameRes)
        assertTrue(content.columns[1].isNext)
    }

    @Test
    fun `the date line is Gregorian then Hijri, with our month names and the user's offset`() {
        val content = TodayTimesContent.of(makkahDay(sunday, nextIs(Prayer.ASR), hijriOffset = 1), arabic)

        assertEquals(WidgetDate("الأحد ٨ نوفمبر", "٢٩ جمادى الأولى ١٤٤٨ هـ"), content.date)
    }

    @Test
    fun `the Date strip shows the date line and the next Prayer with its time`() {
        val content = DateStripContent.of(makkahDay(sunday, nextIs(Prayer.MAGHRIB)), arabic)

        assertEquals(WidgetDate("الأحد ٨ نوفمبر", "٢٨ جمادى الأولى ١٤٤٨ هـ"), content.date)
        assertEquals(R.string.prayer_maghrib, content.nextNameRes)
        assertEquals("١٧:٤١", content.nextTime)
    }

    @Test
    fun `after Isha the Date strip shows tomorrow's Fajr`() {
        val content = DateStripContent.of(makkahDay(sunday, tomorrowFajr), english)

        assertEquals(R.string.prayer_fajr, content.nextNameRes)
        assertEquals("05:09", content.nextTime)
        assertEquals("Sun 8 Nov", content.date.gregorian)
    }
}
