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

    private val providers = mapOf(
        NextPrayerWidgetReceiver::class.java to R.xml.next_prayer_widget_info,
        NextPrayerCompactWidgetReceiver::class.java to R.xml.next_prayer_compact_widget_info,
        TodayTimesWidgetReceiver::class.java to R.xml.today_times_widget_info,
        DateStripWidgetReceiver::class.java to R.xml.date_strip_widget_info,
        TodayLogWidgetReceiver::class.java to R.xml.today_log_widget_info,
        WeekLogWidgetReceiver::class.java to R.xml.week_log_widget_info,
    )

    /** The `android:previewLayout` of a widget's provider info, or 0. */
    private fun previewLayout(info: Int): Int {
        val parser = context.resources.getXml(info)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                return parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "previewLayout", 0)
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
