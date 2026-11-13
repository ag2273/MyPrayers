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
import app.mawaqeet.core.DigitStyle
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
 * Big Clock «ساعة كبيرة» at 4×2, rendered through Glance and laid out with real text measurement: the clock, the two
 * Stops either side of the countdown in the app language's order, the bar and the date line, nothing clipped; the
 * light surface, or the deep one with the switch on; one sentence for TalkBack; a tap opens Today.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class BigClockRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings

    /** One fixed instant for the widget and the expected values alike, so no minute turns between them. */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-12T06:30:20Z"), ZoneId.systemDefault())

    @After
    fun reset() = runBlocking {
        settings.setAppLanguage("")
        settings.setWidgetNextPrayerLook(false)
    }

    /** The two Stops as the widget should name them now, from the same core and content model. */
    private fun stops(): Pair<String, String> = runBlocking {
        val app = context as MawaqeetApplication
        val locale = appLocale(context, settings.appLanguage.first())
        val core = PrayerCore(settings.settings.first(), clock, app.prayerLog)
        val content = ClockContent.of(core.displayDay(), ZonedDateTime.now(clock), DisplayFormatter(locale, settings.digitStyle.first()), true)
        val strings = localizedContext(context, locale)
        "${strings.getString(content.previous.nameRes)} ${content.previous.time}" to "${strings.getString(content.next.nameRes)} ${content.next.time}"
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

    private fun description(root: View): String = views(root).mapNotNull { it.contentDescription?.toString() }.single { "." in it }

    private fun tapOpens(root: View): MainActivity.Opens? {
        clickableViews(root).single().performClick()
        return MainActivity.Opens.from(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun `in Arabic on an English phone it reads right to left, the previous Stop on the right and the next on the left, nothing clipped`() {
        runBlocking { settings.setAppLanguage("ar") }
        val (previous, next) = stops()
        for ((w, h) in listOf(250 to 110, 320 to 180)) {
            val root = renderWidget(context, BigClockWidget(clock), w, h)
            val name = "$w×$h"
            assertTrue(name, root.text(previous).left() > root.text(next).left())
            assertTrue("$name clock", views(root).any { it is TextClock && it.visibility == View.VISIBLE })
            assertTrue("$name dates", widgetTexts(root).any { "هـ" in it.text })
            try { assertNothingClipped(root, allowEllipsis = isHijriDate) } catch (e: AssertionError) { throw AssertionError("$name: ${e.message}") }
        }
        val sentence = description(renderWidget(context, BigClockWidget(clock), 250, 110))
        assertTrue(sentence, sentence.startsWith("الساعة "))
        assertTrue(sentence, next.substringBefore(' ') in sentence && "هـ" in sentence)
    }

    @Test
    fun `in English it reads left to right with English names, nothing clipped`() {
        val (previous, next) = stops()
        for ((w, h) in listOf(250 to 110, 320 to 180)) {
            val root = renderWidget(context, BigClockWidget(clock), w, h)
            assertTrue("$w×$h", root.text(previous).left() < root.text(next).left())
            val clock = views(root).filterIsInstance<TextClock>().single()
            assertTrue("«${clock.text}»", Regex("\\d{1,2}:\\d\\d").matches(clock.text))
            assertTrue("dates", widgetTexts(root).any { " AH · " in it.text })
            assertNothingClipped(root, allowEllipsis = isHijriDate)
        }
        val sentence = description(renderWidget(context, BigClockWidget(clock), 250, 110))
        assertTrue(sentence, "${next.substringBefore(' ')} in " in sentence && " AH" in sentence)
    }

    @Test
    fun `it is on the light surface, and on the deep surface with the star when the Next Prayer look is on`() {
        fun tint(root: View) = backgroundColours(root)
        fun images(root: View) = views(root).count { it is ImageView && it.visibility == View.VISIBLE }
        val deep = artColours(context, R.drawable.widget_bg_deep)

        val light = renderWidget(context, BigClockWidget(clock), 250, 110)
        assertEquals(artColours(context, R.drawable.widget_bg_sky), tint(light))

        runBlocking { settings.setWidgetNextPrayerLook(true) }
        val on = renderWidget(context, BigClockWidget(clock), 250, 110)
        assertEquals(deep, tint(on))
        assertTrue("the star", images(on) > images(light))
        assertNothingClipped(on, allowEllipsis = isHijriDate)

        runBlocking { settings.setWidgetNextPrayerLook(false) }
        assertNotEquals(deep, tint(renderWidget(context, BigClockWidget(clock), 250, 110)))
    }

    @Test
    fun `a tap anywhere opens Today`() {
        assertEquals(MainActivity.Opens.TODAY, tapOpens(renderWidget(context, BigClockWidget(clock), 250, 110)))
    }

    @Test
    fun `with Arabic-Indic digits on an English phone the clock shows the user's digits`() {
        runBlocking {
            settings.setAppLanguage("ar")
            settings.setDigitStyle(DigitStyle.ARABIC_INDIC)
        }
        try {
            val clock = views(renderWidget(context, BigClockWidget(clock), 250, 110)).filterIsInstance<TextClock>().single()
            assertTrue("«${clock.text}»", clock.text.any { it in '٠'..'٩' } && clock.text.none { it in '0'..'9' })
        } finally {
            runBlocking { settings.setDigitStyle(DigitStyle.AUTOMATIC) }
        }
    }
}
