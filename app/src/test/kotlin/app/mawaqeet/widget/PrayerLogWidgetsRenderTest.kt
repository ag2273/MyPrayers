package app.mawaqeet.widget

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MainActivity
import app.mawaqeet.R
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock

/**
 * Renders the two Prayer Log widgets over the app's real Prayer Log (Room) at their smallest 4×3 size: the words, the
 * marks, nothing clipped, one tap target each, and where that tap goes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar")
class PrayerLogWidgetsRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app get() = context as MawaqeetApplication

    private fun <T> io(block: () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    private fun core() = runBlocking { PrayerCore(app.settings.settings.first(), Clock.systemUTC(), app.prayerLog) }

    private fun images(view: View): List<ImageView> = when (view) {
        is ImageView -> listOf(view)
        is android.view.ViewGroup -> (0 until view.childCount).flatMap { images(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** The intent the widget's one tap target starts. */
    private fun tapOpens(root: View): MainActivity.Opens? {
        val targets = clickableViews(root)
        assertEquals("one tap target, never one that changes the Prayer Log", 1, targets.size)
        targets.single().performClick()
        return MainActivity.Opens.from(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun `Today with Prayer Log shows the five Prayers, Sunrise and the date in Arabic, with nothing clipped`() {
        val root = renderWidget(context, TodayLogWidget(), 250, 180)
        val words = widgetTexts(root).map { it.text.toString() }

        for (name in listOf("الفجر", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertTrue(words.any { it.startsWith("الشروق ") })
        assertTrue(words.any { "هـ" in it })
        assertEquals(View.LAYOUT_DIRECTION_RTL, widgetTexts(root).first().layoutDirection)
        assertNothingClipped(root, allowEllipsis = setOf(R.id.date_hijri))
    }

    @Test
    fun `a mark made in the app shows as prayed on the next render`() {
        val core = core()
        val today = io { core.prayerDay(core.displayDay().date) }
        val due = Prayer.entries.firstOrNull { today.isMarkable(it) } ?: return // before Fajr: nothing can be marked yet
        fun labels() = images(renderWidget(context, TodayLogWidget(), 250, 180)).mapNotNull { it.contentDescription?.toString() }

        assertEquals(0, labels().count { it == "صُلّيت" })
        io { core.mark(today.date, due) }
        assertEquals(1, labels().count { it == "صُلّيت" })
        io { core.unmark(today.date, due) }
        assertEquals(0, labels().count { it == "صُلّيت" })
    }

    @Test
    fun `Today with Prayer Log opens today's Prayer Times`() {
        assertEquals(MainActivity.Opens.TODAY, tapOpens(renderWidget(context, TodayLogWidget(), 250, 180)))
    }

    @Test
    fun `Week Prayer Log shows seven day letters, five rows and the total, with nothing clipped, and opens the Prayer Log`() {
        val root = renderWidget(context, WeekLogWidget(), 250, 180)
        val words = widgetTexts(root).map { it.text.toString() }

        assertTrue("سجل الأسبوع" in words)
        assertTrue("no total in $words", words.any { "/" in it })
        for (name in listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertEquals(7, words.count { it.length == 1 })
        assertNothingClipped(root)
        assertEquals(MainActivity.Opens.PRAYER_LOG, tapOpens(root))
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `in English and dark mode both are LTR with nothing clipped`() {
        for (widget in listOf(TodayLogWidget(), WeekLogWidget())) {
            val root = renderWidget(context, widget, 250, 180)
            assertEquals(View.LAYOUT_DIRECTION_LTR, widgetTexts(root).first().layoutDirection)
            assertNothingClipped(root, allowEllipsis = setOf(R.id.date_hijri))
        }
        assertTrue(widgetTexts(renderWidget(context, WeekLogWidget(), 250, 180)).any { it.text.toString() == "Week Prayer Log" })
    }
}
