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
 * Renders the two Prayer Log widgets over the app's real Prayer Log (Room) at their smallest size (4×2 and 4×3): the words, the
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
    fun `Today with Prayer Log shows its title, the count marked and the five Prayers in Arabic, with nothing clipped`() {
        val root = renderWidget(context, TodayLogWidget(), 250, 110)
        val words = widgetTexts(root).map { it.text.toString() }

        for (name in listOf("اليوم وسجل الصلاة", "الفجر", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertTrue("no count in $words", words.any { it.endsWith(" من ٥") })
        assertEquals(View.LAYOUT_DIRECTION_RTL, widgetTexts(root).first().layoutDirection)
        assertNothingClipped(root, allowEllipsis = isHijriDate)
    }

    @Test
    fun `a mark made in the app shows as prayed on the next render`() {
        val core = core()
        val today = io { core.prayerDay(core.displayDay().date) }
        val due = Prayer.entries.firstOrNull { today.isMarkable(it) } ?: return // before Fajr: nothing can be marked yet
        fun labels() = images(renderWidget(context, TodayLogWidget(), 250, 110)).mapNotNull { it.contentDescription?.toString() }

        assertEquals(0, labels().count { it == "صُلّيت" })
        io { core.mark(today.date, due) }
        assertEquals(1, labels().count { it == "صُلّيت" })
        io { core.unmark(today.date, due) }
        assertEquals(0, labels().count { it == "صُلّيت" })
    }

    @Test
    fun `Today with Prayer Log opens today's Prayer Times`() {
        assertEquals(MainActivity.Opens.TODAY, tapOpens(renderWidget(context, TodayLogWidget(), 250, 110)))
    }

    @Test
    fun `Week Prayer Log shows five rows and the total, with nothing clipped, and opens the Prayer Log`() {
        val root = renderWidget(context, WeekLogWidget(), 250, 180)
        val words = widgetTexts(root).map { it.text.toString() }

        assertTrue("سجل الأسبوع" in words)
        assertTrue("no total in $words", words.any { it.startsWith("المجموع ") })
        for (name in listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertNothingClipped(root)
        assertEquals(MainActivity.Opens.PRAYER_LOG, tapOpens(root))
    }

    @Test
    @Config(qualifiers = "en-night")
    fun `in English and dark mode both are LTR with nothing clipped`() {
        for ((widget, height) in listOf(TodayLogWidget() to 110, WeekLogWidget() to 180, WeekLogWidget() to 110)) {
            val root = renderWidget(context, widget, 250, height)
            assertEquals(View.LAYOUT_DIRECTION_LTR, widgetTexts(root).first().layoutDirection)
            assertNothingClipped(root, allowEllipsis = isHijriDate)
        }
        assertTrue(widgetTexts(renderWidget(context, WeekLogWidget(), 250, 180)).any { it.text.toString() == "Week Prayer Log" })
    }

    private fun descriptions(view: View): List<String> =
        listOfNotNull(view.contentDescription?.toString()) +
            ((view as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).flatMap { descriptions(g.getChildAt(it)) } } ?: emptyList())

    @Test
    @Config(qualifiers = "en")
    fun `TalkBack reads each Week Prayer Log row as one line, and each Today mark by its state`() {
        val week = descriptions(renderWidget(context, WeekLogWidget(), 250, 180))
        for (names in listOf(listOf("Fajr"), listOf("Dhuhr", "Jumu'ah"), listOf("Asr"), listOf("Maghrib"), listOf("Isha"))) {
            assertTrue("no row for $names in $week", week.any { row -> names.any { row.startsWith("$it:") } })
        }
        val marks = images(renderWidget(context, TodayLogWidget(), 250, 110)).mapNotNull { it.contentDescription?.toString() }
        assertEquals(marks.toString(), 5, marks.size)
        assertTrue(marks.toString(), marks.all { it in setOf("Prayed", "Not marked", "Not yet due") })
    }
}
