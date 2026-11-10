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
import app.mawaqeet.core.LogState
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

    /** The intent the widget's one tap target that isn't a Prayer's column starts. */
    private fun tapOpens(root: View): MainActivity.Opens? {
        val targets = clickableViews(root).filter { !isColumn(it) }
        assertEquals("one tap target besides the Prayers' columns", 1, targets.size)
        targets.single().performClick()
        return MainActivity.Opens.from(shadowOf(context as Application).nextStartedActivity)
    }

    /** A Prayer's column in Today with Prayer Log: its description is on it, or on a view it wraps or sits in. */
    private fun isColumn(view: View): Boolean {
        val children = (view as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) } } ?: emptyList()
        val around = (listOf(view, view.parent as? View) + children).mapNotNull { it?.contentDescription?.toString() }
        return around.any { d -> (ARABIC_NAMES + ENGLISH_NAMES).any { d.startsWith("$it:") } }
    }

    /** What TalkBack reads for each of Today with Prayer Log's five columns: «name: state», and how to change it. */
    private fun columnDescriptions(root: View, names: List<String> = ARABIC_NAMES): List<String> =
        descriptions(root).filter { d -> names.any { d.startsWith("$it:") } }

    @Test
    fun `Today with Prayer Log shows its title, the count marked and the five Prayers in Arabic, with nothing clipped`() {
        val root = renderWidget(context, TodayLogWidget(), 250, 150)
        val words = widgetTexts(root).map { it.text.toString() }

        for (name in listOf("سجل اليوم", "الفجر", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertTrue("no count in $words", words.any { it.endsWith(" من ٥") })
        assertEquals(View.LAYOUT_DIRECTION_RTL, widgetTexts(root).first().layoutDirection)
        assertNothingClipped(root, allowEllipsis = isHijriDate)
    }

    @Test
    fun `one row high, Today with Prayer Log drops its header and keeps the five Prayers and their marks unclipped`() {
        val root = renderWidget(context, TodayLogWidget(), 250, 80)
        val words = widgetTexts(root).map { it.text.toString() }

        assertTrue("header kept in $words", "سجل اليوم" !in words)
        for (name in listOf("الفجر", "العصر", "المغرب", "العشاء")) assertTrue("$name missing in $words", name in words)
        assertEquals(5, columnDescriptions(root).size)
        assertNothingClipped(root)
    }

    @Test
    fun `Week Prayer Log draws all seven days for every Prayer, even before the app was first used`() {
        val root = renderWidget(context, WeekLogWidget(), 250, 180)
        // 7 × 5 dots, besides the frame's own images.
        val dots = images(root).count { it.drawable != null && it.id != R.id.widget_bg }
        assertTrue("only $dots dots", dots >= 35)
    }

    @Test
    fun `a mark made in the app shows as prayed on the next render`() {
        val core = core()
        val today = io { core.prayerDay(core.displayDay().date) }
        val due = Prayer.entries.firstOrNull { today.isMarkable(it) } ?: return // before Fajr: nothing can be marked yet
        fun labels() = columnDescriptions(renderWidget(context, TodayLogWidget(), 250, 110))

        assertEquals(0, labels().count { ": صُلّيت" in it })
        io { core.mark(today.date, due) }
        assertEquals(1, labels().count { ": صُلّيت" in it })
        io { core.unmark(today.date, due) }
        assertEquals(0, labels().count { ": صُلّيت" in it })
    }

    @Test
    fun `Today with Prayer Log opens today's Prayer Times, and each Prayer whose time has come is its own tap target`() {
        val root = renderWidget(context, TodayLogWidget(), 250, 110)
        val core = core()
        val day = io { core.prayerDay(core.displayDay().date) }
        val due = Prayer.entries.count { day.isMarkable(it) }
        assertEquals(due, clickableViews(root).count { isColumn(it) })
        assertEquals(MainActivity.Opens.TODAY, tapOpens(root))
    }

    @Test
    fun `tapping a due Prayer's column marks it, and tapping again removes the mark`() {
        val core = core()
        val today = io { core.prayerDay(core.displayDay().date) }
        val due = Prayer.entries.firstOrNull { today.isMarkable(it) } ?: return // before Fajr: nothing can be marked yet
        val tap = androidx.glance.action.actionParametersOf(
            ToggleMarkAction.PRAYER to due.name,
            ToggleMarkAction.DATE to today.date.toString(),
        )
        val id = object : androidx.glance.GlanceId {}

        runBlocking { ToggleMarkAction().onAction(context, id, tap) }
        assertEquals(LogState.PRAYED, io { core.prayerDay(today.date).stateOf(due) })
        runBlocking { ToggleMarkAction().onAction(context, id, tap) }
        assertEquals(LogState.NOT_MARKED, io { core.prayerDay(today.date).stateOf(due) })
    }

    @Test
    fun `Week Prayer Log shows five rows and the total, with nothing clipped, and opens the Prayer Log`() {
        val root = renderWidget(context, WeekLogWidget(), 250, 180)
        val words = widgetTexts(root).map { it.text.toString() }

        assertTrue("سجل الأسبوع" in words)
        assertTrue("no total in $words", words.any { it.startsWith("من ") })
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
        val marks = columnDescriptions(renderWidget(context, TodayLogWidget(), 250, 110), ENGLISH_NAMES)
        assertEquals(marks.toString(), 5, marks.size)
        assertTrue(marks.toString(), marks.all { m -> listOf("Prayed", "Not marked", "Not yet due").any { ": $it" in m } })
    }

    private companion object {
        val ARABIC_NAMES = listOf("الفجر", "الظهر", "الجمعة", "العصر", "المغرب", "العشاء")
        val ENGLISH_NAMES = listOf("Fajr", "Dhuhr", "Jumu'ah", "Asr", "Maghrib", "Isha")
    }
}
