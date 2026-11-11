package app.mawaqeet.widget

import android.content.Context
import android.widget.ImageView
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings → Widgets reaches every Widget Variant: the background's opacity, light shadowed text, and size steps. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar")
class WidgetAppearanceRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings

    /** Every Widget Variant at its smallest size. */
    private val smallest: List<Triple<GlanceAppWidget, Int, Int>> = listOf(
        Triple(NextPrayerRingWidget(), 57, 60),
        Triple(NextPrayerWidget(), 110, 100),
        Triple(TodayTimesWidget(), 250, 110),
        Triple(DateStripWidget(), 250, 40),
        Triple(NextPrayerWidget(), 250, 110),
        Triple(TodayLogWidget(), 250, 110),
        Triple(WeekLogWidget(), 250, 180),
        Triple(BigClockWidget(), 250, 110),
        Triple(ClockPrayerWidget(), 250, 110),
    )

    @After
    fun reset() = runBlocking { settings.setWidgetTransparency(0) }

    private fun background(root: android.view.View) = root.findViewById<ImageView>(R.id.widget_bg)

    @Test
    fun `by default every widget has a solid background and plain text`() {
        for ((widget, w, h) in smallest) {
            val root = renderWidget(context, widget, w, h)
            assertEquals(widget.javaClass.simpleName, 255, background(root).imageAlpha)
            assertTrue(widget.javaClass.simpleName, widgetTexts(root).all { it.shadowRadius == 0f })
        }
    }

    @Test
    fun `at 70 percent every widget fades its background and shadows its text off the accent, with nothing clipped`() {
        runBlocking { settings.setWidgetTransparency(70) }
        for ((widget, w, h) in smallest) {
            val root = renderWidget(context, widget, w, h)
            val name = widget.javaClass.simpleName
            assertEquals(name, 77, background(root).imageAlpha)
            val texts = widgetTexts(root)
            assertTrue(name, texts.isNotEmpty())
            // Only the dark text on a highlighted (accent-filled) cell goes without a shadow: the next Prayer's name and time.
            val plain = texts.filter { it.shadowRadius == 0f }.map { it.text.toString() }
            assertTrue("$name: $plain", plain.size <= 2)
            try { assertNothingClipped(root, allowEllipsis = isHijriDate) } catch (e: AssertionError) { throw AssertionError("$name: ${e.message}") }
        }
    }

    @Test
    fun `given more room on Android 12 and later, text grows a step, with nothing clipped`() {
        fun nameSize(width: Int, height: Int) = widgetTexts(renderWidget(context, WeekLogWidget(), width, height))
            .first { it.text.toString() == "الفجر" }.textSize

        val small = nameSize(250, 180)
        val large = nameSize(420, 320)
        // A step up, at least: at its smallest the grid also shrinks a little to fit the height.
        assertTrue("${large / small}", large / small >= 1.15f)
        assertNothingClipped(renderWidget(context, WeekLogWidget(), 420, 320), allowEllipsis = isHijriDate)
    }

    @Test
    @Config(sdk = [30])
    fun `on Android 11 the widgets keep one layout at the default size`() {
        fun nameSize(width: Int, height: Int) = widgetTexts(renderWidget(context, WeekLogWidget(), width, height))
            .first { it.text.toString() == "الفجر" }.textSize

        assertEquals(nameSize(250, 180), nameSize(420, 320), 0.01f)
        // The Next Prayer label may shorten its place name.
        for ((widget, w, h) in smallest) assertNothingClipped(renderWidget(context, widget, w, h)) { isHijriDate(it) || " · " in it }
    }
}
