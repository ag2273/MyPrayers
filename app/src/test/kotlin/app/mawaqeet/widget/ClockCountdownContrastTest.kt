package app.mawaqeet.widget

import android.content.Context
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.R
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.ui.appLocale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Every Clock Widget's countdown reads clearly on its own surface: WCAG contrast of at least 4.5:1 against the
 * widget's background, in every look (the app's or the wallpaper's colours, the Next Prayer look, Gold Clock), at 4×2
 * and 4×1, in light and dark. Solid backgrounds only: over a see-through one, light text relies on its shadow.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class ClockCountdownContrastTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-12T06:30:20Z"), ZoneId.systemDefault())

    @After
    fun reset() = runBlocking {
        settings.setWidgetColors(WidgetColors.APP)
        settings.setWidgetNextPrayerLook(false)
        settings.setWidgetTransparency(0)
    }

    private fun countdown(): String = runBlocking {
        val app = context as MawaqeetApplication
        val core = PrayerCore(settings.settings.first(), clock, app.prayerLog)
        val formatter = DisplayFormatter(appLocale(context, settings.appLanguage.first()), settings.digitStyle.first())
        ClockContent.of(core.displayDay(), ZonedDateTime.now(clock), formatter, true).countdown
    }

    /** WCAG contrast of two opaque colours. */
    private fun contrast(a: Int, b: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        fun luminance(c: Int) = 0.2126 * channel(Color.red(c)) + 0.7152 * channel(Color.green(c)) + 0.0722 * channel(Color.blue(c))
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertCountdownReadable(look: String) {
        val widgets: List<Triple<String, () -> GlanceAppWidget, Pair<Int, Int>>> = listOf(
            Triple("Big Clock 4×2", { BigClockWidget(clock) }, 250 to 110),
            Triple("Big Clock 4×1", { BigClockWidget(clock) }, 250 to 40),
            Triple("Clock Prayer 4×2", { ClockPrayerWidget(clock) }, 250 to 110),
            Triple("Gold Clock 4×2", { GoldClockWidget(clock) }, 250 to 110),
            Triple("Gold Clock 4×1", { GoldClockWidget(clock) }, 250 to 40),
        )
        val text = countdown()
        for ((name, widget, size) in widgets) {
            val root = renderWidget(context, widget(), size.first, size.second)
            val backgrounds = backgroundColours(root)
            assertTrue("$look $name: a background", backgrounds.isNotEmpty())
            val shown: TextView = widgetTexts(root).firstOrNull { it.text.toString() == text }
                ?: throw AssertionError("$look $name: countdown «$text» not in ${widgetTexts(root).map { it.text }}")
            // Over a gradient, against every stop of it.
            for (background in backgrounds) {
                val ratio = contrast(shown.currentTextColor, background or 0xFF000000.toInt())
                assertTrue(
                    "$look $name: countdown #%08X on #%08X is %.2f:1".format(shown.currentTextColor, background, ratio),
                    ratio >= 4.5,
                )
            }
        }
    }

    private fun assertEveryLook(theme: String) {
        for (colors in WidgetColors.entries) {
            for (nextPrayerLook in listOf(false, true)) {
                runBlocking {
                    settings.setWidgetColors(colors)
                    settings.setWidgetNextPrayerLook(nextPrayerLook)
                }
                assertCountdownReadable("$theme, $colors colours${if (nextPrayerLook) ", Next Prayer look" else ""}:")
            }
        }
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `in light every Clock Widget's countdown reads clearly in every look`() = assertEveryLook("light")

    @Test
    @Config(qualifiers = "en-night")
    fun `in dark every Clock Widget's countdown reads clearly in every look`() = assertEveryLook("dark")

    /**
     * On the regular surfaces (the app's or the wallpaper's colours, solid or see-through) the countdown is in the
     * widget's bright text, the clock face's colour, never the pale tinted accent that was hard to read on the dark
     * wallpaper surface. The deep look and Gold Clock keep their gold countdown.
     */
    private fun assertCountdownInTextColour(theme: String) {
        val text = countdown()
        for (colors in WidgetColors.entries) {
            for (transparency in listOf(0, 70)) {
                runBlocking {
                    settings.setWidgetColors(colors)
                    settings.setWidgetTransparency(transparency)
                }
                for ((name, widget, size) in listOf(
                    Triple("Big Clock 4×2", BigClockWidget(clock), 250 to 110),
                    Triple("Big Clock 4×1", BigClockWidget(clock), 250 to 40),
                    Triple("Clock Prayer 4×2", ClockPrayerWidget(clock), 250 to 110),
                )) {
                    val root = renderWidget(context, widget, size.first, size.second)
                    val shown = widgetTexts(root).first { it.text.toString() == text }
                    val face = widgetTexts(root).filterIsInstance<android.widget.TextClock>().first { it.visibility == android.view.View.VISIBLE }
                    assertTrue(
                        "$theme, $colors, $transparency%% transparent: $name countdown #%08X, clock #%08X".format(shown.currentTextColor, face.currentTextColor),
                        shown.currentTextColor == face.currentTextColor,
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `in light the countdown on the regular surfaces is in the bright text colour`() = assertCountdownInTextColour("light")

    @Test
    @Config(qualifiers = "en-night")
    fun `in dark the countdown on the regular surfaces is in the bright text colour`() = assertCountdownInTextColour("dark")
}
