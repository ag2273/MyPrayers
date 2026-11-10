package app.mawaqeet.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.R
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.ui.localizedContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Measures the widest words and times with the system font (native graphics, at font scale 1 so 1sp = 1dp) at the
 * smallest size each new widget can be: nothing in the 4×2 columns or the 4×1 lines may be clipped there.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class WidgetTextFitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val languages = listOf(Locale.forLanguageTag("ar"), Locale.ENGLISH)
    private val names = TodayTimesMetrics.NAMES

    private fun width(text: String, sp: Float, bold: Boolean = false): Float = Paint().apply {
        textSize = sp
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }.measureText(text)

    private fun widestTime(formatter: DisplayFormatter) = (0..23).flatMap { h -> listOf(0, 8, 59).map { m -> h to m } }
        .map { (h, m) -> formatter.time(ZonedDateTime.of(2026, 1, 1, h, m, 0, 0, ZoneId.of("UTC"))) }

    @Test
    fun `the system font measures Arabic for real`() {
        // A guard for the other tests: zero or one fixed width per glyph would make them pass trivially.
        assertTrue(width("المغرب", 11f) > 10f)
        assertTrue(width("العشاء", 11f) != width("ii", 11f))
    }

    @Test
    fun `at the smallest 4x2 width every column name and time fits at a readable size`() {
        val room = TodayTimesMetrics.cellTextWidthDp(TodayTimesMetrics.MIN_WIDTH_DP.toFloat())
        for (locale in languages) {
            val strings = localizedContext(context, locale)
            val allNames = TodayTimesMetrics.NAMES.map { strings.getString(it) }
            val nameSp = TodayTimesMetrics.fitSp(allNames, room, TodayTimesMetrics.NAME_SP, bold = false)
            assertTrue("$locale names shrink to ${nameSp}sp", nameSp >= 9f)
            for (name in allNames) assertTrue("$name doesn't fit at ${nameSp}sp", width(name, nameSp) <= room)

            for (digits in DigitStyle.entries) {
                val times = widestTime(DisplayFormatter(locale, digits))
                val timeSp = TodayTimesMetrics.fitSp(times, room, TodayTimesMetrics.TIME_SP, bold = true)
                assertTrue("$locale $digits times shrink to ${timeSp}sp", timeSp >= 11f)
                for (time in times) assertTrue("$time doesn't fit at ${timeSp}sp", width(time, timeSp, bold = true) <= room)
            }
        }
    }

    @Test
    fun `Arabic names keep their full size at the smallest 4x2 width`() {
        val room = TodayTimesMetrics.cellTextWidthDp(TodayTimesMetrics.MIN_WIDTH_DP.toFloat())
        val strings = localizedContext(context, Locale.forLanguageTag("ar"))
        val names = TodayTimesMetrics.NAMES.map { strings.getString(it) }

        assertEquals(TodayTimesMetrics.NAME_SP, TodayTimesMetrics.fitSp(names, room, TodayTimesMetrics.NAME_SP, bold = false))
    }

    @Test
    fun `a large font scale shrinks the names instead of clipping them`() {
        val room = TodayTimesMetrics.cellTextWidthDp(330f)
        val names = TodayTimesMetrics.NAMES.map { localizedContext(context, Locale.ENGLISH).getString(it) }

        val sp = TodayTimesMetrics.fitSp(names, room, TodayTimesMetrics.NAME_SP, bold = false, fontScale = 1.3f)

        assertTrue(sp < TodayTimesMetrics.NAME_SP)
        assertTrue(names.all { width(it, sp * 1.3f) <= room })
    }

    @Test
    fun `the Date strip's three parts fit side by side at a readable size at the smallest 4x1 in Arabic and a usual 4x1 in English`() {
        for (locale in languages) {
            val strings = localizedContext(context, locale)
            for (digits in DigitStyle.entries) {
                val formatter = DisplayFormatter(locale, digits)
                // Every weekday and month, on the widest day numbers, with the widest Hijri month, Prayer and time.
                val dates = (1..12).flatMap { month -> (24..30).map { LocalDate.of(2026, month, if (month == 2) it - 2 else it) } }
                val hijri = (1..12).map { formatter.hijriDayMonth(app.mawaqeet.core.HijriDate(1448, it, 28)) }
                    .maxBy { width(it, DateStripMetrics.HIJRI_SP, bold = true) }
                val next = names.map { strings.getString(it) }.flatMap { name -> widestTime(formatter).map { "$name $it" } }
                    .maxBy { width(it, DateStripMetrics.NEXT_SP, bold = true) }
                for (date in dates) {
                    val text = object : DateStripText {
                        override val date = WidgetDate(formatter.shortGregorianDate(date), "")
                        override val nextLabel = strings.getString(R.string.widget_next_short)
                        override val next = next
                        override val parts = listOf(
                            formatter.weekday(date) to formatter.dayMonth(date),
                            hijri to formatter.hijriYear(app.mawaqeet.core.HijriDate(1448, 5, 28)),
                            next to nextLabel,
                        )
                    }
                    // English's longest Hijri months are wide: at the narrowest 4×1 only Arabic keeps the readable size.
                    val width = if (locale.language == "ar") DateStripMetrics.MIN_WIDTH_DP else USUAL_4X1_WIDTH_DP
                    val scale = DateStripMetrics.scale(text, width, 56f)
                    assertTrue("${text.parts} at $width : scale $scale", scale >= 0.6f)
                }
            }
        }
    }

    private companion object {
        /** A 4×1 on most phones is about 300 to 360dp wide. */
        const val USUAL_4X1_WIDTH_DP = 300f
    }
}
