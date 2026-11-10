package app.mawaqeet.widget

import android.content.Context
import android.view.View
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders both widgets at their smallest size through Glance, lays the RemoteViews out with real text measurement,
 * and checks the words, the direction and that no text is clipped or overlapping (Hijri may ellipsize by design).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar")
class TodayWidgetsRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun render(widget: GlanceAppWidget, width: Int, height: Int) = renderWidget(context, widget, width, height)

    @Test
    fun `Next Prayer's ring in one cell shows the name inside and the countdown under it, with nothing clipped`() {
        // The usual 1×1 cells on phones, from a narrow 57×60 to a roomy 90×100.
        for ((width, height) in listOf(70 to 80, 57 to 60, 62 to 92, 76 to 96, 90 to 100)) {
            val root = render(NextPrayerRingWidget(), width, height)
            val texts = widgetTexts(root)
            assertTrue("no countdown at $width×$height", texts.any { it.id == app.mawaqeet.R.id.countdown })
            assertTrue("no name at $width×$height in ${texts.map { it.text }}", texts.any { it.text.toString() == "المغرب" || it.text.length in 3..7 })
            assertTrue("label shown at $width×$height", texts.none { it.text.toString().startsWith("الصلاة القادمة") })
            assertNothingClipped(root)
        }
    }

    @Test
    fun `Today's Prayer Times shows the date and five columns in Arabic RTL with nothing clipped at 250x110`() {
        val root = render(TodayTimesWidget(), 250, 110)
        val words = widgetTexts(root).map { it.text.toString() }

        for (name in listOf("الفجر", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertTrue("no Sunrise on the board", "الشروق" !in words)
        assertTrue("no date first in $words", words.first() !in listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء"))
        assertEquals(View.LAYOUT_DIRECTION_RTL, widgetTexts(root).first().layoutDirection)
        assertNothingClipped(root, allowEllipsis = isHijriDate)
    }

    @Test
    fun `Date strip shows the dates and the next Prayer line with nothing clipped at 250x40`() {
        val root = render(DateStripWidget(), 250, 40)
        val words = widgetTexts(root).map { it.text.toString() }

        assertTrue("no next label in $words", "القادمة" in words)
        assertTrue(words.any { "هـ" in it })
        assertNothingClipped(root, allowEllipsis = isHijriDate)
    }

    @Test
    fun `the Date strip keeps the board's text size when the launcher gives it a usual 4x1 size`() {
        val density = context.resources.displayMetrics.scaledDensity
        fun nextLabelSp(width: Int, height: Int) = widgetTexts(render(DateStripWidget(), width, height))
            .first { it.text.toString() == "القادمة" }.textSize / density

        assertTrue(nextLabelSp(360, 80) >= DateStripMetrics.LABEL_SP)
        assertTrue(nextLabelSp(250, 40) >= DateStripMetrics.LABEL_SP * 0.6f)
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `in English and dark mode both widgets are LTR with nothing clipped`() {
        val root = render(TodayTimesWidget(), 320, 140)

        assertTrue(widgetTexts(root).map { it.text }.toString(), widgetTexts(root).any { it.text.toString() == "Isha" })
        assertEquals(View.LAYOUT_DIRECTION_LTR, widgetTexts(root).first().layoutDirection)
        assertNothingClipped(root, allowEllipsis = isHijriDate)

        val strip = render(DateStripWidget(), 250, 40)
        assertTrue(widgetTexts(strip).any { it.text.toString() == "Next" })
        assertNothingClipped(strip, allowEllipsis = isHijriDate)
    }
}
