package app.mawaqeet.widget

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MawaqeetApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone in English, the app in Arabic (the common case): the launcher lays widgets out in the phone's direction,
 * and Glance's own views follow it, so the widgets must still read right to left.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en")
class WidgetDirectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun arabicApp() = runBlocking { (context as MawaqeetApplication).settings.setAppLanguage("ar") }

    /** The settings store outlives a test in this process: leave the language as other tests expect it. */
    @After
    fun systemLanguage() = runBlocking { (context as MawaqeetApplication).settings.setAppLanguage("") }

    private fun render(widget: GlanceAppWidget, width: Int, height: Int) = renderWidget(context, widget, width, height)

    /** Left edge in the widget (the views aren't attached to a window). */
    private fun View.screenLeft(): Int {
        var left = 0
        var v: View? = this
        while (v != null) {
            left += v.left
            v = v.parent as? View
        }
        return left
    }

    private fun View.textView(text: String): TextView = widgetTexts(this).first { it.text.toString() == text }

    private fun TextView.drawnLeft(): Float = screenLeft() + totalPaddingLeft + layout.getLineLeft(0)
    private fun TextView.drawnRight(): Float = screenLeft() + totalPaddingLeft + layout.getLineRight(0)

    private val weekdays = listOf("الأحد", "الاثنين", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    /**
     * The Gregorian date: it starts with the weekday and has no time in it, which keeps Friday's next-Prayer line
     * («الجمعة ١٢:٠٥») out; the Hijri date ends in هـ.
     */
    private fun gregorian(root: View): TextView =
        widgetTexts(root).first { t -> weekdays.any { t.text.startsWith(it) } && "هـ" !in t.text && ':' !in t.text }

    private fun hijri(root: View): TextView = widgetTexts(root).first { "هـ" in it.text }

    /** [a]'s box is to the right of [b]'s: [a] reads first in Arabic. */
    private fun assertReadsBefore(a: View, b: View) =
        assertTrue("${(a as? TextView)?.text} at ${a.screenLeft()} should be right of ${(b as? TextView)?.text} at ${b.screenLeft()}", a.screenLeft() > b.screenLeft())

    @Test
    fun `Today's Prayer Times puts Fajr on the right and Isha on the left, and the date on the right`() {
        val root = render(TodayTimesWidget(), 250, 110)
        assertReadsBefore(root.textView("الفجر"), root.textView("العشاء"))
        assertTrue("date on the right", gregorian(root).drawnRight() > root.width * 0.75f)
    }

    @Test
    fun `Today with Prayer Log puts Fajr on the right and its title on the right`() {
        val root = render(TodayLogWidget(), 250, 150)
        assertReadsBefore(root.textView("الفجر"), root.textView("العشاء"))
        assertTrue("Fajr's column on the right", root.textView("الفجر").screenLeft() > root.width * 0.75f)
        assertTrue("title on the right", root.textView("سجل اليوم").drawnRight() > root.width * 0.75f)
    }

    @Test
    fun `Week Prayer Log puts the Prayer names and the title on the right`() {
        val root = render(WeekLogWidget(), 250, 180)
        val fajr = root.textView("الفجر")
        assertTrue("label column on the right", fajr.screenLeft() > root.width / 2)
        assertTrue(fajr.drawnRight() > root.width * 0.75f)
        assertTrue("title on the right", root.textView("سجل الأسبوع").drawnRight() > root.width * 0.75f)
    }

    @Test
    fun `Next Prayer starts on the right, with its countdown`() {
        for (height in listOf(160, 110)) {
            val root = render(NextPrayerWidget(), 160, height)
            val countdown = widgetTexts(root).single { it.id == app.mawaqeet.R.id.countdown }
            assertTrue("countdown shown at height $height", countdown.height > 0)
            assertTrue("countdown on the right at height $height", countdown.drawnRight() > root.width * 0.6f)
            val name = widgetTexts(root).first { t -> names.any { t.text.startsWith(it) } }
            assertTrue("name on the right at height $height", name.drawnRight() > root.width * 0.6f)
        }
        // 4×2: the name on the right, its time on the left.
        val wide = render(NextPrayerWidget(), 300, 150)
        val name = widgetTexts(wide).first { it.text.toString() in names }
        val time = widgetTexts(wide).first { it.text.length == 5 && it.text[2] == ':' }
        assertReadsBefore(name, time)
        assertTrue("name on the right", name.drawnRight() > wide.width * 0.75f)
        assertTrue("time on the left: ${time.text} at ${time.screenLeft()} of ${wide.width}", time.screenLeft() < wide.width * 0.25f)
    }

    private val names = listOf("الفجر", "الظهر", "الجمعة", "العصر", "المغرب", "العشاء")

    @Test
    fun `Date strip puts the dates on the right and the next Prayer on the left`() {
        val root = render(DateStripWidget(), 300, 60)
        assertTrue(gregorian(root).drawnRight() > root.width * 0.75f)
        // Three parts in reading order: the Gregorian date, the Hijri date, the next Prayer.
        assertReadsBefore(gregorian(root), hijri(root))
        assertReadsBefore(hijri(root), root.textView("القادمة"))
        assertReadsBefore(root.textView("القادمة"), widgetTexts(root).first { t -> names.any { t.text.startsWith("$it ") } })
    }
}
