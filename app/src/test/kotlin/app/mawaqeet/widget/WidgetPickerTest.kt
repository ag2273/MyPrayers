package app.mawaqeet.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** What the launcher's widget picker shows: one distinct name and a real preview per widget. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar")
class WidgetPickerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ANDROID = "http://schemas.android.com/apk/res/android"

    private val providers = mapOf(
        NextPrayerWidgetReceiver::class.java to R.xml.next_prayer_widget_info,
        NextPrayerCompactWidgetReceiver::class.java to R.xml.next_prayer_compact_widget_info,
        TodayTimesWidgetReceiver::class.java to R.xml.today_times_widget_info,
        DateStripWidgetReceiver::class.java to R.xml.date_strip_widget_info,
        TodayLogWidgetReceiver::class.java to R.xml.today_log_widget_info,
        WeekLogWidgetReceiver::class.java to R.xml.week_log_widget_info,
        BigClockWidgetReceiver::class.java to R.xml.big_clock_widget_info,
        GoldClockWidgetReceiver::class.java to R.xml.gold_clock_widget_info,
        ClockPrayerWidgetReceiver::class.java to R.xml.clock_prayer_widget_info,
    )

    /** The `android:previewLayout` of a widget's provider info, or 0. */
    private fun previewLayout(info: Int): Int {
        val parser = context.resources.getXml(info)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                return parser.getAttributeResourceValue(ANDROID, "previewLayout", 0)
            }
        }
        return 0
    }

    @Test
    fun `every widget has its own name in the picker`() {
        val labels = providers.keys.map { receiver ->
            context.packageManager.getReceiverInfo(ComponentName(context, receiver), PackageManager.GET_META_DATA)
                .loadLabel(context.packageManager).toString()
        }
        assertEquals(labels.toString(), labels.size, labels.toSet().size)
    }

    @Test
    fun `Big Clock is listed as «ساعة كبيرة», 4×2 by default`() {
        val label = context.packageManager.getReceiverInfo(ComponentName(context, BigClockWidgetReceiver::class.java), PackageManager.GET_META_DATA)
            .loadLabel(context.packageManager).toString()
        assertEquals("ساعة كبيرة", label)
        val parser = context.resources.getXml(R.xml.big_clock_widget_info)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                assertEquals("4", parser.getAttributeValue(ANDROID, "targetCellWidth"))
                assertEquals("2", parser.getAttributeValue(ANDROID, "targetCellHeight"))
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English Big Clock is listed as Big Clock`() {
        val label = context.packageManager.getReceiverInfo(ComponentName(context, BigClockWidgetReceiver::class.java), PackageManager.GET_META_DATA)
            .loadLabel(context.packageManager).toString()
        assertEquals("Big Clock", label)
    }

    @Test
    fun `Gold Clock is listed as «ساعة ذهبية», 4×2 by default, with a black preview`() {
        val label = context.packageManager.getReceiverInfo(ComponentName(context, GoldClockWidgetReceiver::class.java), PackageManager.GET_META_DATA)
            .loadLabel(context.packageManager).toString()
        assertEquals("ساعة ذهبية", label)
        val parser = context.resources.getXml(R.xml.gold_clock_widget_info)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                assertEquals("4", parser.getAttributeValue(ANDROID, "targetCellWidth"))
                assertEquals("2", parser.getAttributeValue(ANDROID, "targetCellHeight"))
            }
        }
        val preview = RemoteViews(context.packageName, previewLayout(R.xml.gold_clock_widget_info)).apply(context, FrameLayout(context))
        assertEquals(android.graphics.Color.BLACK, preview.backgroundTintList?.defaultColor)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English Gold Clock is listed as Gold Clock`() {
        val label = context.packageManager.getReceiverInfo(ComponentName(context, GoldClockWidgetReceiver::class.java), PackageManager.GET_META_DATA)
            .loadLabel(context.packageManager).toString()
        assertEquals("Gold Clock", label)
    }

    @Test
    fun `Clock and Prayer is listed as «ساعة وصلاة», 4×2 by default, with a preview of the split layout`() {
        val label = context.packageManager.getReceiverInfo(ComponentName(context, ClockPrayerWidgetReceiver::class.java), PackageManager.GET_META_DATA)
            .loadLabel(context.packageManager).toString()
        assertEquals("ساعة وصلاة", label)
        val parser = context.resources.getXml(R.xml.clock_prayer_widget_info)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                assertEquals("4", parser.getAttributeValue(ANDROID, "targetCellWidth"))
                assertEquals("2", parser.getAttributeValue(ANDROID, "targetCellHeight"))
            }
        }
        val preview = RemoteViews(context.packageName, previewLayout(R.xml.clock_prayer_widget_info)).apply(context, FrameLayout(context))
        val texts = widgetTexts(preview).map { it.text.toString() }
        assertTrue(texts.toString(), "الصلاة القادمة" in texts && "بعد" in texts)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English Clock and Prayer is listed as Clock & Prayer`() {
        val label = context.packageManager.getReceiverInfo(ComponentName(context, ClockPrayerWidgetReceiver::class.java), PackageManager.GET_META_DATA)
            .loadLabel(context.packageManager).toString()
        assertEquals("Clock & Prayer", label)
    }

    @Test
    fun `every widget has a preview that the launcher can inflate, with text in it`() {
        val previews = providers.values.map(::previewLayout)
        for ((receiver, preview) in providers.keys.zip(previews)) {
            assertNotEquals("${receiver.simpleName} has no preview", 0, preview)
            // The launcher inflates the preview as RemoteViews: only remotable views are allowed.
            val view = RemoteViews(context.packageName, preview).apply(context, FrameLayout(context))
            assertTrue("${receiver.simpleName}'s preview is empty", widgetTexts(view).size >= 2)
            assertEquals(View.VISIBLE, view.visibility)
        }
        assertEquals("previews are distinct", previews.size, previews.toSet().size)
    }
}
