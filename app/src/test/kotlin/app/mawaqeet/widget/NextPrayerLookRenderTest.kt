package app.mawaqeet.widget

import android.content.Context
import android.widget.ImageView
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

/** Settings → Widgets → «كل الأدوات بتصميم الصلاة القادمة»: every widget on the deep surface, nothing clipped. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar")
class NextPrayerLookRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings

    private val widgets = listOf(
        Triple(TodayTimesWidget(), 250, 110),
        Triple(TodayTimesWidget(), 320, 180),
        Triple(DateStripWidget(), 250, 40),
        Triple(TodayLogWidget(), 250, 110),
        Triple(TodayLogWidget(), 320, 180),
        Triple(WeekLogWidget(), 250, 180),
        Triple(WeekLogWidget(), 320, 260),
        Triple(BigClockWidget(), 250, 110),
        Triple(BigClockWidget(), 320, 180),
        Triple(ClockPrayerWidget(), 250, 110),
        Triple(ClockPrayerWidget(), 320, 180),
    )

    @After
    fun reset() = runBlocking { settings.setWidgetNextPrayerLook(false) }

    private fun backgroundTint(root: android.view.View) = root.findViewById<ImageView>(R.id.widget_bg).imageTintList?.defaultColor

    @Test
    fun `on, every widget is deep with readable text, and off each is back to its own look`() {
        val deep = context.getColor(R.color.widget_deep_background)
        runBlocking { settings.setWidgetNextPrayerLook(true) }
        for ((widget, w, h) in widgets) {
            val name = "${widget.javaClass.simpleName} $w×$h"
            val root = renderWidget(context, widget, w, h)
            backgroundTint(root)?.let { assertEquals(name, deep, it) }
            assertTrue(name, widgetTexts(root).isNotEmpty())
            try { assertNothingClipped(root, allowEllipsis = isHijriDate) } catch (e: AssertionError) { throw AssertionError("$name: ${e.message}") }
            System.getenv("MAWAQEET_DUMP_WIDGETS")?.let { dumpPng(root, "$it/${widget.javaClass.simpleName}-$w-$h.png") }
        }
        runBlocking { settings.setWidgetNextPrayerLook(false) }
        for ((widget, w, h) in widgets) {
            backgroundTint(renderWidget(context, widget, w, h))?.let { assertTrue(widget.javaClass.simpleName, it != deep) }
        }
    }

    private fun dumpPng(root: android.view.View, path: String) {
        val bitmap = android.graphics.Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFF6A5A8C.toInt())
        root.draw(canvas)
        java.io.File(path).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
