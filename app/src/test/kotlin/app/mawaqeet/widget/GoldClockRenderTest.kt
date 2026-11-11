package app.mawaqeet.widget

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.ZonedDateTime

/**
 * Gold Clock «ساعة ذهبية» at 4×2: Big Clock's layout and content on a fixed black surface, a white clock, a gold
 * countdown and bar, muted gold Stops and a light grey date line, whatever Settings → Widgets says.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class GoldClockRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings

    @After
    fun reset() = runBlocking {
        settings.setAppLanguage("")
        settings.setWidgetNextPrayerLook(false)
        settings.setWidgetTransparency(0)
        settings.setWidgetColors(WidgetColors.APP)
    }

    /** The two Stops and the countdown as the widget should show them now, from the same core and content model. */
    private fun shown(): Triple<String, String, String> = runBlocking {
        val app = context as MawaqeetApplication
        val locale = appLocale(context, settings.appLanguage.first())
        val core = PrayerCore(settings.settings.first(), Clock.systemUTC(), app.prayerLog)
        val content = ClockContent.of(core.displayDay(), ZonedDateTime.now(), DisplayFormatter(locale, settings.digitStyle.first()), true)
        val strings = localizedContext(context, locale)
        Triple("${strings.getString(content.previous.nameRes)} ${content.previous.time}", "${strings.getString(content.next.nameRes)} ${content.next.time}", content.countdown)
    }

    private fun stops(): Pair<String, String> = shown().let { it.first to it.second }

    private fun View.left(): Int {
        var left = 0
        var v: View? = this
        while (v != null) {
            left += v.left
            v = v.parent as? View
        }
        return left
    }

    private fun View.text(text: String): TextView = widgetTexts(this).firstOrNull { it.text.toString() == text }
        ?: throw AssertionError("«$text» not in ${widgetTexts(this).map { it.text }}")

    private fun views(view: View): List<View> = listOf(view) + ((view as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { views(g.getChildAt(it)) } } ?: emptyList())

    private fun background(root: View) = root.findViewById<ImageView>(R.id.widget_bg)

    private fun clock(root: View) = views(root).filterIsInstance<TextClock>().single()

    private fun render() = renderWidget(context, GoldClockWidget(), 250, 110)

    @Test
    fun `in Arabic on an English phone it reads right to left on black, nothing clipped`() {
        runBlocking { settings.setAppLanguage("ar") }
        val (previous, next) = stops()
        for ((w, h) in listOf(250 to 110, 320 to 180)) {
            val root = renderWidget(context, GoldClockWidget(), w, h)
            val name = "$w×$h"
            assertEquals(name, Color.BLACK, background(root).imageTintList?.defaultColor)
            assertTrue(name, root.text(previous).left() > root.text(next).left())
            assertEquals("$name clock", View.VISIBLE, clock(root).visibility)
            assertTrue("$name dates", widgetTexts(root).any { "هـ" in it.text })
            try { assertNothingClipped(root, allowEllipsis = isHijriDate) } catch (e: AssertionError) { throw AssertionError("$name: ${e.message}") }
        }
    }

    @Test
    fun `in English it reads left to right with English names on black, nothing clipped`() {
        val (previous, next) = stops()
        for ((w, h) in listOf(250 to 110, 320 to 180)) {
            val root = renderWidget(context, GoldClockWidget(), w, h)
            assertEquals("$w×$h", Color.BLACK, background(root).imageTintList?.defaultColor)
            assertTrue("$w×$h", root.text(previous).left() < root.text(next).left())
            assertTrue("«${clock(root).text}»", Regex("\\d{1,2}:\\d\\d").matches(clock(root).text))
            assertTrue("dates", widgetTexts(root).any { " AH · " in it.text })
            assertNothingClipped(root, allowEllipsis = isHijriDate)
        }
    }

    @Test
    fun `a white clock, a gold countdown, muted gold Stops and a light grey date line, all readable on black`() {
        val root = render()
        assertEquals(Color.WHITE, clock(root).currentTextColor)
        val (previous, next, countdown) = shown()
        val gold = Color.parseColor("#E0B860")
        val mutedGold = root.text(previous).currentTextColor
        assertEquals(gold, root.text(countdown).currentTextColor)
        assertEquals(mutedGold, root.text(next).currentTextColor)
        assertTrue("muted gold", Color.red(mutedGold) > Color.blue(mutedGold) + 40 && mutedGold != gold)
        val date = widgetTexts(root).single { isHijriDate(it.text.toString()) || " AH · " in it.text }.currentTextColor
        assertTrue("light grey", Color.red(date) == Color.green(date) && Color.green(date) == Color.blue(date) && Color.red(date) in 0xA0..0xF0)
        for (line in widgetTexts(root)) {
            val ratio = contrastOnBlack(line.currentTextColor)
            assertTrue("«${line.text}» ${"%.1f".format(ratio)}:1", ratio >= 4.5)
        }
    }

    @Test
    fun `it looks the same whatever the transparency, colours and the Next Prayer look`() {
        val plain = look(render())
        runBlocking { settings.setWidgetNextPrayerLook(true) }
        assertEquals("Next Prayer look on", plain, look(render()))
        runBlocking { settings.setWidgetNextPrayerLook(false) }
        for (percent in listOf(30, 70, 100)) {
            runBlocking { settings.setWidgetTransparency(percent) }
            assertEquals("$percent%", plain, look(render()))
        }
        runBlocking {
            settings.setWidgetTransparency(0)
            settings.setWidgetColors(WidgetColors.WALLPAPER)
        }
        assertEquals("wallpaper colours", plain, look(render()))
        assertEquals(255, background(render()).imageAlpha)
    }

    @Test
    fun `a tap anywhere opens Today`() {
        clickableViews(render()).single().performClick()
        val started = org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(app.mawaqeet.MainActivity.Opens.TODAY, app.mawaqeet.MainActivity.Opens.from(started))
    }

    /** What the user sees of the look: the surface and its opacity, the drawings (no star), and every line's colour. */
    private fun look(root: View): List<Any?> = listOf(
        background(root).imageTintList?.defaultColor,
        background(root).imageAlpha,
        views(root).count { it is ImageView && it.visibility == View.VISIBLE },
    ) + widgetTexts(root).map { it.currentTextColor to it.shadowRadius }

    /** WCAG contrast of an opaque colour against black. */
    private fun contrastOnBlack(color: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        val l = 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color))
        return (l + 0.05) / 0.05
    }
}
