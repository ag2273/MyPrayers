package app.mawaqeet.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.R
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.ui.appLocale
import app.mawaqeet.ui.localizedContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The three Clock Widgets shrunk to 4×1: the clock, the countdown and the bar stay, the date line goes, the clock is on
 * the app language's start side, and nothing is clipped or pushed out of the widget, with normal and large fonts.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class ClockCompactRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings

    /** One fixed instant for the widget and the expected values alike, so no minute turns between them. */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-12T06:30:20Z"), ZoneId.systemDefault())
    private val ANDROID = "http://schemas.android.com/apk/res/android"

    private val variants: List<Pair<String, () -> GlanceAppWidget>> = listOf(
        "Gold Clock" to { GoldClockWidget(clock) },
        "Big Clock" to { BigClockWidget(clock) },
        "Clock & Prayer" to { ClockPrayerWidget(clock) },
    )

    /** 4×1 at the smallest the launcher may give, and a roomier 4×1. */
    private val sizes = listOf(250 to 50, 320 to 80)

    @After
    fun reset() = runBlocking {
        settings.setAppLanguage("")
        settings.setWidgetNextPrayerLook(false)
    }

    private class Shown(val countdown: String, val hijri: String, val gregorian: String)

    private fun shown(): Shown = runBlocking {
        val app = context as MawaqeetApplication
        val locale = appLocale(context, settings.appLanguage.first())
        val core = PrayerCore(settings.settings.first(), clock, app.prayerLog)
        val content = ClockContent.of(core.displayDay(), ZonedDateTime.now(clock), DisplayFormatter(locale, settings.digitStyle.first()), true)
        Shown(content.countdown, content.hijriDate, content.gregorianDate)
    }

    private fun views(view: View): List<View> = listOf(view) + ((view as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { views(g.getChildAt(it)) } } ?: emptyList())

    private fun View.screenLeft(): Int = generateSequence(this) { it.parent as? View }.sumOf { it.left }
    private fun View.screenTop(): Int = generateSequence(this) { it.parent as? View }.sumOf { it.top }

    /** Renders [widget] and checks it. */
    private fun assertCompact(widget: GlanceAppWidget, w: Int, h: Int, name: String, rtl: Boolean) {
        val root = renderWidget(context, widget, w, h)
        val s = shown()
        val texts = widgetTexts(root)
        val clock = views(root).filterIsInstance<TextClock>().single { it.visibility == View.VISIBLE }
        val countdown: TextView = texts.firstOrNull { it.text.toString() == s.countdown }
            ?: throw AssertionError("$name: countdown «${s.countdown}» not in ${texts.map { it.text }}")
        val density = context.resources.displayMetrics.density
        val bar = views(root).filterIsInstance<ImageView>().filter { it.visibility == View.VISIBLE }
            .firstOrNull { it.height in 1..(WidgetArt.LINE_HEIGHT_DP * density + 1).toInt() && it.width > 40 * density }
        assertTrue("$name: the bar", bar != null)
        assertFalse("$name: no date line in ${texts.map { it.text }}", texts.any { s.hijri in it.text || s.gregorian in it.text || isHijriDate(it.text.toString()) })
        if (rtl) {
            assertTrue("$name: the clock is on the right of the countdown", clock.screenLeft() > countdown.screenLeft() + countdown.width - 1)
        } else {
            assertTrue("$name: the clock is on the left of the countdown", clock.screenLeft() + clock.width < countdown.screenLeft() + 1)
        }
        try { assertNothingClipped(root) } catch (e: AssertionError) { throw AssertionError("$name: ${e.message}") }
        for (v in texts + bar!!) {
            val top = v.screenTop()
            val left = v.screenLeft()
            assertTrue("$name: «${(v as? TextView)?.text ?: "bar"}» is inside the widget ($left,$top ${v.width}×${v.height} in ${root.width}×${root.height})",
                top >= 0 && left >= 0 && top + v.height <= root.height && left + v.width <= root.width && v.height > 0)
        }
    }

    private fun assertAll(rtl: Boolean) {
        for ((name, widget) in variants) {
            for ((w, h) in sizes) assertCompact(widget(), w, h, "$name $w×$h", rtl)
        }
    }

    @Test
    fun `in Arabic on an English phone each Clock at 4x1 keeps the clock on the right, the countdown and the bar, and drops the dates`() {
        runBlocking { settings.setAppLanguage("ar") }
        assertAll(rtl = true)
    }

    @Test
    fun `in English each Clock at 4x1 keeps the clock on the left, the countdown and the bar, and drops the dates`() {
        assertAll(rtl = false)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `with large fonts in Arabic each Clock at 4x1 scales down rather than clips`() {
        runBlocking { settings.setAppLanguage("ar") }
        assertAll(rtl = true)
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `with the largest fonts in English each Clock at 4x1 scales down rather than clips`() {
        assertAll(rtl = false)
    }

    @Test
    fun `with the Next Prayer look on Big Clock and Clock & Prayer at 4x1 still fit`() {
        runBlocking {
            settings.setAppLanguage("ar")
            settings.setWidgetNextPrayerLook(true)
        }
        for ((name, widget) in variants.drop(1)) assertCompact(widget(), 250, 50, "$name look", rtl = true)
    }

    @Test
    fun `every Clock's widget info allows resizing down to 4x1`() {
        for (info in listOf(R.xml.gold_clock_widget_info, R.xml.big_clock_widget_info, R.xml.clock_prayer_widget_info)) {
            val parser = context.resources.getXml(info)
            var height: Float? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                    height = parser.getAttributeValue(ANDROID, "minResizeHeight").takeWhile { it.isDigit() || it == '.' }.toFloat()
                }
            }
            assertTrue("minResizeHeight $height", height != null && height <= 50f)
        }
    }
}
