package app.mawaqeet.widget

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MainActivity
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Clock & Prayer «ساعة وصلاة» at 4×2, the split layout: on the app language's start side the clock, the Hijri date and
 * the Gregorian date; on the other side «الصلاة القادمة», the next Stop's name and time, «بعد» and the countdown, the bar
 * and «من» the previous Stop. Light surface, or the deep one with the star when the switch is on; one sentence for
 * TalkBack; a tap opens Today.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class ClockPrayerRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings

    /** One fixed instant for the widget and the expected values alike, so no minute turns between them. */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-12T06:30:20Z"), ZoneId.systemDefault())

    @After
    fun reset() = runBlocking {
        settings.setAppLanguage("")
        settings.setWidgetNextPrayerLook(false)
    }

    /** What the widget should show now, from the same core and content model. */
    private class Shown(val nextName: String, val nextTime: String, val previous: String, val countdown: String, val hijri: String, val gregorian: String)

    private fun shown(): Shown = runBlocking {
        val app = context as MawaqeetApplication
        val locale = appLocale(context, settings.appLanguage.first())
        val core = PrayerCore(settings.settings.first(), clock, app.prayerLog)
        val content = ClockContent.of(core.displayDay(), ZonedDateTime.now(clock), DisplayFormatter(locale, settings.digitStyle.first()), true)
        val strings = localizedContext(context, locale)
        Shown(
            nextName = strings.getString(content.next.nameRes),
            nextTime = content.next.time,
            previous = "${strings.getString(content.previous.nameRes)} ${content.previous.time}",
            countdown = content.countdown,
            hijri = content.hijriDate,
            gregorian = content.gregorianDate,
        )
    }

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

    private fun clock(root: View) = views(root).filterIsInstance<TextClock>().single { it.visibility == View.VISIBLE }

    private fun description(root: View): String = views(root).mapNotNull { it.contentDescription?.toString() }.single { "." in it }

    private fun render(w: Int = 250, h: Int = 110) = renderWidget(context, ClockPrayerWidget(clock), w, h)

    /** Every part of the split layout is there, and the clock's side is the app language's start. */
    private fun assertSplit(root: View, name: String, rtl: Boolean, label: String, before: String, from: String) {
        val s = shown()
        val clock = clock(root)
        val nextName = root.text(s.nextName)
        root.text(s.nextTime)
        root.text(label)
        root.text(before)
        root.text(s.countdown)
        root.text("$from ${s.previous}")
        val hijri = root.text(s.hijri)
        val gregorian = root.text(s.gregorian)
        assertTrue("$name: the Gregorian date is under the Hijri", gregorian.locationTop() > hijri.locationTop())
        assertTrue("$name: the dates are under the clock", hijri.locationTop() > clock.locationTop())
        if (rtl) {
            assertTrue("$name: the clock is on the right", clock.left() > nextName.left() + nextName.width)
        } else {
            assertTrue("$name: the clock is on the left", clock.left() + clock.width < nextName.left())
        }
        try { assertNothingClipped(root, allowEllipsis = isHijriDate) } catch (e: AssertionError) { throw AssertionError("$name: ${e.message}") }
    }

    private fun View.locationTop(): Int {
        var top = 0
        var v: View? = this
        while (v != null) {
            top += v.top
            v = v.parent as? View
        }
        return top
    }

    @Test
    fun `in Arabic on an English phone the clock and dates are on the right and the next Prayer on the left, nothing clipped`() {
        runBlocking { settings.setAppLanguage("ar") }
        for ((w, h) in listOf(250 to 110, 320 to 180)) {
            assertSplit(render(w, h), "$w×$h", rtl = true, label = "الصلاة القادمة", before = "بعد", from = "من")
        }
        val sentence = description(render())
        assertTrue(sentence, sentence.startsWith("الساعة "))
        assertTrue(sentence, shown().nextName in sentence && "هـ" in sentence)
    }

    @Test
    fun `in English the clock and dates are on the left and the next Prayer on the right, nothing clipped`() {
        for ((w, h) in listOf(250 to 110, 320 to 180)) {
            assertSplit(render(w, h), "$w×$h", rtl = false, label = "Next Prayer", before = "in", from = "from")
        }
        val clock = clock(render())
        assertTrue("«${clock.text}»", Regex("\\d{1,2}:\\d\\d").matches(clock.text))
        val sentence = description(render())
        assertTrue(sentence, "${shown().nextName} in " in sentence && " AH" in sentence)
    }

    @Test
    fun `it is on the light surface, and on the deep surface with the star when the Next Prayer look is on`() {
        fun tint(root: View) = backgroundColours(root)
        fun images(root: View) = views(root).count { it is ImageView && it.visibility == View.VISIBLE }
        val deep = artColours(context, R.drawable.widget_bg_deep)

        val light = render()
        assertEquals(artColours(context, R.drawable.widget_bg_sky), tint(light))

        runBlocking { settings.setWidgetNextPrayerLook(true) }
        val on = render()
        assertEquals(deep, tint(on))
        assertTrue("the star", images(on) > images(light))
        assertNothingClipped(on, allowEllipsis = isHijriDate)

        runBlocking { settings.setWidgetNextPrayerLook(false) }
        assertNotEquals(deep, tint(render()))
    }

    @Test
    fun `a tap anywhere opens Today`() {
        clickableViews(render()).single().performClick()
        assertEquals(MainActivity.Opens.TODAY, MainActivity.Opens.from(shadowOf(context as Application).nextStartedActivity))
    }
}
