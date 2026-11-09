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
    fun `Today's Prayer Times shows six columns in Arabic RTL with nothing clipped at 250x110`() {
        val root = render(TodayTimesWidget(), 250, 110)
        val words = widgetTexts(root).map { it.text.toString() }

        for (name in listOf("الفجر", "الشروق", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertTrue(words.any { "هـ" in it })
        assertEquals(View.LAYOUT_DIRECTION_RTL, widgetTexts(root).first().layoutDirection)
        assertNothingClipped(root, allowEllipsis = setOf(app.mawaqeet.R.id.date_hijri))
    }

    @Test
    fun `Date strip shows the dates and the next Prayer line with nothing clipped at 250x40`() {
        val root = render(DateStripWidget(), 250, 40)
        val words = widgetTexts(root).map { it.text.toString() }

        assertTrue("no next line in $words", words.any { it.startsWith("القادمة: ") })
        assertTrue(words.any { "هـ" in it })
        assertNothingClipped(root, allowEllipsis = setOf(app.mawaqeet.R.id.date_hijri))
    }

    @Test
    fun `the Date strip keeps its full text size when the launcher gives it a usual 4x1 height`() {
        val density = context.resources.displayMetrics.scaledDensity
        fun nextLineSp(height: Int) = widgetTexts(render(DateStripWidget(), 300, height))
            .first { it.id == app.mawaqeet.R.id.next_line }.textSize / density

        assertEquals(DateStripMetrics.MAX_SP, nextLineSp(80), 0.01f)
        assertTrue(nextLineSp(40) >= 9.5f)
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `in English and dark mode both widgets are LTR with nothing clipped`() {
        val root = render(TodayTimesWidget(), 320, 140)

        assertTrue(widgetTexts(root).map { it.text }.toString(), widgetTexts(root).any { it.text.toString() == "Sunrise" })
        assertEquals(View.LAYOUT_DIRECTION_LTR, widgetTexts(root).first().layoutDirection)
        assertNothingClipped(root, allowEllipsis = setOf(app.mawaqeet.R.id.date_hijri))

        val strip = render(DateStripWidget(), 250, 40)
        assertTrue(widgetTexts(strip).any { it.text.startsWith("Next: ") })
        assertNothingClipped(strip, allowEllipsis = setOf(app.mawaqeet.R.id.date_hijri))
    }
}
