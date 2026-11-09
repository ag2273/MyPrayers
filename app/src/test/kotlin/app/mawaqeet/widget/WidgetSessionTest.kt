package app.mawaqeet.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Places every widget and lets Glance render it through its real session, as on a phone: composition on the main
 * thread, then `updateAppWidget`. Glance swaps in its "Can't show content" layout when anything there throws, which
 * `compose()` alone can hide (the Prayer Log widgets once read Room in composition, which the main thread refuses).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar")
class WidgetSessionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val receivers: List<Class<out GlanceAppWidgetReceiver>> = listOf(
        NextPrayerWidgetReceiver::class.java,
        NextPrayerCompactWidgetReceiver::class.java,
        TodayTimesWidgetReceiver::class.java,
        DateStripWidgetReceiver::class.java,
        TodayLogWidgetReceiver::class.java,
        WeekLogWidgetReceiver::class.java,
    )

    @Test
    fun `every widget renders through a real Glance session without the error layout`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val placed = receivers.associateWith { receiver ->
            val info = AppWidgetProviderInfo().apply { provider = ComponentName(context, receiver) }
            manager.addInstalledProvider(info)
            manager.createWidget(receiver, android.R.layout.simple_list_item_1).also { manager.addBoundWidget(it, info) }
        }

        runBlocking { Widgets.refreshAll(context) }
        // The sessions run as workers and compose on the main looper; give them a few seconds of (fake) time.
        repeat(50) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            Thread.sleep(50)
        }

        val error = context.getString(androidx.glance.appwidget.R.string.glance_error_layout_text_v2)
        for ((receiver, id) in placed) {
            val texts = widgetTexts(manager.getViewFor(id)).map { it.text.toString() }
            assertTrue("${receiver.simpleName} shows the error layout", error !in texts)
            assertTrue("${receiver.simpleName} was not rendered: $texts", texts.size >= 2)
        }
    }
}
