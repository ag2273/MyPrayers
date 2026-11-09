package app.mawaqeet.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** How digits look on screens, notifications and widgets. Stored by name, so entries are never renamed. */
enum class DigitStyle {
    /** The locale's own numbering system, including its `-u-nu-` extension (Android 14 regional preferences). */
    AUTOMATIC,

    /** ٠١٢٣٤٥٦٧٨٩ */
    ARABIC_INDIC,

    /** 0123456789 */
    WESTERN,
}

/**
 * The one formatter for every time, date, countdown and number the user sees: screens, notifications and widgets
 * all use it, so the language and digit setting look the same everywhere.
 *
 * [locale] is the app's current locale: Arabic when its language is `ar`, English otherwise. Hijri dates are written
 * from their numbers with our own month names; the platform's Islamic-calendar formatter is never used. Display only:
 * never use the output for storage or machine strings.
 */
class DisplayFormatter(locale: Locale, digits: DigitStyle) {
    private val arabic = locale.language == "ar"

    /** The locale with the digit choice applied as a numbering-system extension. */
    private val formattingLocale: Locale = when (digits) {
        DigitStyle.AUTOMATIC -> locale
        DigitStyle.ARABIC_INDIC -> Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "arab").build()
        DigitStyle.WESTERN -> Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
    }

    // java.time ignores locale digits unless it is given the locale's decimal style explicitly.
    private val decimalStyle = DecimalStyle.of(formattingLocale)
    private val zero = decimalStyle.zeroDigit

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withDecimalStyle(decimalStyle)
    private val gregorianFormatter = DateTimeFormatter
        .ofPattern(if (arabic) "EEEE، d MMMM y" else "EEEE, d MMMM y", locale)
        .withDecimalStyle(decimalStyle)

    /** A Prayer Time or other wall-clock time: `05:09` / `٠٥:٠٩`. */
    fun time(time: ZonedDateTime): String = timeFormatter.format(time)

    /** Time left until a Prayer, always with seconds: `1:05:09`. Negative durations read as zero. */
    fun countdown(remaining: Duration): String {
        val seconds = remaining.seconds.coerceAtLeast(0)
        return digits("%d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds % 3600 / 60, seconds % 60))
    }

    /** Weekday, day, month and year: `الأحد، ٨ نوفمبر ٢٠٢٦` / `Sunday, 8 November 2026`. */
    fun gregorianDate(date: LocalDate): String = gregorianFormatter.format(date)

    /** Day, our month name, year and era: `٢٨ جمادى الأولى ١٤٤٨ هـ` / `28 Jumada al-Ula 1448 AH`. */
    fun hijriDate(date: HijriDate): String {
        val month = (if (arabic) ARABIC_HIJRI_MONTHS else ENGLISH_HIJRI_MONTHS)[date.month - 1]
        val era = if (arabic) "هـ" else "AH"
        return "${number(date.day)} $month ${number(date.year)} $era"
    }

    fun number(value: Int): String = digits(value.toString())

    /** A number with its sign, such as a Hijri offset: `+2`, `-1`, `0`. */
    fun signedNumber(value: Int): String = if (value > 0) "+${number(value)}" else number(value)

    private fun digits(western: String): String =
        if (zero == '0') western else String(CharArray(western.length) { i ->
            val c = western[i]
            if (c in '0'..'9') zero + (c - '0') else c
        })

    private companion object {
        val ARABIC_HIJRI_MONTHS = listOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
        )
        val ENGLISH_HIJRI_MONTHS = listOf(
            "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Akhir", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah",
        )
    }
}
