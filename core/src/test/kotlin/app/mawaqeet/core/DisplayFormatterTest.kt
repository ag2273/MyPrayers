package app.mawaqeet.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

private val ARABIC: Locale = Locale.forLanguageTag("ar")
private val ENGLISH: Locale = Locale.forLanguageTag("en")
private val FAJR = ZonedDateTime.of(2026, 11, 8, 5, 9, 41, 0, ZoneId.of("Asia/Riyadh"))
private val SUNDAY: LocalDate = LocalDate.of(2026, 11, 8)

// Expected strings are written by hand from the Umm al-Qura month names and the digit tables (U+0660…U+0669).
class DisplayFormatterTest {

    @Test
    fun `an Arabic Hijri date uses our Arabic month names, in Arabic-Indic or Western digits`() {
        val date = HijriDate(1448, 5, 28)

        assertEquals("٢٨ جمادى الأولى ١٤٤٨ هـ", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).hijriDate(date))
        assertEquals("28 جمادى الأولى 1448 هـ", DisplayFormatter(ARABIC, DigitStyle.WESTERN).hijriDate(date))
    }

    @Test
    fun `an English Hijri date uses our English month names, never a platform era like BC`() {
        assertEquals("28 Jumada al-Ula 1448 AH", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).hijriDate(HijriDate(1448, 5, 28)))
        assertEquals("١ Ramadan ١٤٤٨ AH", DisplayFormatter(ENGLISH, DigitStyle.ARABIC_INDIC).hijriDate(HijriDate(1448, 9, 1)))
    }

    @Test
    fun `every Hijri month has its own Arabic and English name`() {
        val arabic = (1..12).map { DisplayFormatter(ARABIC, DigitStyle.WESTERN).hijriDate(HijriDate(1448, it, 1)) }
        val english = (1..12).map { DisplayFormatter(ENGLISH, DigitStyle.WESTERN).hijriDate(HijriDate(1448, it, 1)) }

        assertEquals("1 محرم 1448 هـ", arabic.first())
        assertEquals("1 ذو الحجة 1448 هـ", arabic.last())
        assertEquals("1 Muharram 1448 AH", english.first())
        assertEquals("1 Dhu al-Hijjah 1448 AH", english.last())
        assertEquals(12, arabic.toSet().size)
        assertEquals(12, english.toSet().size)
    }

    @Test
    fun `a Prayer Time is hours and minutes in the chosen digits`() {
        assertEquals("٠٥:٠٩", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).time(FAJR))
        assertEquals("05:09", DisplayFormatter(ARABIC, DigitStyle.WESTERN).time(FAJR))
        assertEquals("05:09", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).time(FAJR))
        assertEquals("٠٥:٠٩", DisplayFormatter(ENGLISH, DigitStyle.ARABIC_INDIC).time(FAJR))
    }

    @Test
    fun `Automatic digits follow the locale's numbering system`() {
        val arab = Locale.forLanguageTag("ar-u-nu-arab")
        val latn = Locale.forLanguageTag("ar-u-nu-latn")

        assertEquals("٠٥:٠٩", DisplayFormatter(arab, DigitStyle.AUTOMATIC).time(FAJR))
        assertEquals("05:09", DisplayFormatter(latn, DigitStyle.AUTOMATIC).time(FAJR))
        assertEquals("05:09", DisplayFormatter(ENGLISH, DigitStyle.AUTOMATIC).time(FAJR))
        assertEquals("05:09", DisplayFormatter(arab, DigitStyle.WESTERN).time(FAJR))
    }

    @Test
    fun `a countdown is hours, minutes and seconds in the chosen digits, never negative`() {
        val remaining = Duration.ofHours(1).plusMinutes(5).plusSeconds(9)

        assertEquals("١:٠٥:٠٩", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).countdown(remaining))
        assertEquals("1:05:09", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).countdown(remaining))
        assertEquals("0:00:00", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).countdown(Duration.ofSeconds(-3)))
    }

    @Test
    fun `a Gregorian date is weekday, day, month and year in the app language and chosen digits`() {
        assertEquals("الأحد، ٨ نوفمبر ٢٠٢٦", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).gregorianDate(SUNDAY))
        assertEquals("الأحد، 8 نوفمبر 2026", DisplayFormatter(ARABIC, DigitStyle.WESTERN).gregorianDate(SUNDAY))
        assertEquals("Sunday, 8 November 2026", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).gregorianDate(SUNDAY))
    }

    @Test
    fun `a Prayer Log day label is a short Gregorian date in the app language and chosen digits`() {
        assertEquals("الأحد ٨ نوفمبر", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).shortGregorianDate(SUNDAY))
        assertEquals("الأحد 8 نوفمبر", DisplayFormatter(ARABIC, DigitStyle.WESTERN).shortGregorianDate(SUNDAY))
        assertEquals("Sun 8 Nov", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).shortGregorianDate(SUNDAY))
    }

    @Test
    fun `a narrow weekday is one letter in either language`() {
        val week = (0L..6L).map { SUNDAY.plusDays(it) }

        assertEquals(listOf("ح", "ن", "ث", "ر", "خ", "ج", "س"), week.map { DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).narrowWeekday(it) })
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), week.map { DisplayFormatter(ENGLISH, DigitStyle.WESTERN).narrowWeekday(it) })
    }

    @Test
    fun `a Prayer Log count reads prayed over possible in the chosen digits`() {
        assertEquals("\u2066٥/٧\u2069", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).count(5, 7))
        assertEquals("\u206631/35\u2069", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).count(31, 35))
    }

    @Test
    fun `a number uses the chosen digits`() {
        assertEquals("+٢", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).signedNumber(2))
        assertEquals("-1", DisplayFormatter(ARABIC, DigitStyle.WESTERN).signedNumber(-1))
        assertEquals("٠", DisplayFormatter(ARABIC, DigitStyle.ARABIC_INDIC).signedNumber(0))
        assertEquals("31", DisplayFormatter(ENGLISH, DigitStyle.WESTERN).number(31))
    }
}
