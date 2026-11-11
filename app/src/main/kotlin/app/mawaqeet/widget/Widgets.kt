package app.mawaqeet.widget

import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.os.UserManagerCompat
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one entry point that re-renders every home-screen widget. Called from the display alarms
 * (`AlertReceiver`: each Prayer Time and local midnight), from `RescheduleReceiver` (boot, time, zone, locale) and
 * on every settings, digit or language change (`MawaqeetApplication`). The periods are the Next Prayer widgets'
 * half-hourly update, which moves their progress line and ring, and the Clock widgets' minute ([ClockTickReceiver]).
 */
object Widgets {
    /** Every Widget Variant's [GlanceAppWidget] class; each new variant adds itself here. */
    private val all: List<() -> GlanceAppWidget> get() = listOf(::NextPrayerWidget, ::NextPrayerRingWidget, ::TodayTimesWidget, ::DateStripWidget, ::TodayLogWidget, ::WeekLogWidget) + clocks

    /** The Clock Widgets, re-rendered each minute by [ClockTickReceiver]; each new Clock variant adds itself here. */
    val clocks: List<() -> GlanceAppWidget> = listOf(::BigClockWidget, ::GoldClockWidget, ::ClockPrayerWidget)

    /**
     * Bumped on every refresh, in each widget's Glance state. A widget reads it with `currentState(REFRESHED_AT)` and
     * recomputes its content when it changes, since a running Glance session recomposes instead of calling
     * `provideGlance` again.
     */
    val REFRESHED_AT = longPreferencesKey("refreshed_at")

    /** Re-renders every placed widget. Does nothing before the first unlock (settings are unreadable then). */
    suspend fun refreshAll(context: Context) = refresh(context, all)

    /** Re-renders the placed widgets of the given kinds only (the Next Prayer's half-hourly progress update). */
    suspend fun refresh(context: Context, kinds: List<() -> GlanceAppWidget>) {
        if (!UserManagerCompat.isUserUnlocked(context)) return
        val manager = GlanceAppWidgetManager(context)
        val stamp = System.currentTimeMillis()
        for (create in kinds) {
            val widget = create()
            for (id in manager.getGlanceIds(widget.javaClass)) {
                updateAppWidgetState(context, id) { it[REFRESHED_AT] = stamp }
                widget.update(context, id)
            }
        }
    }

    /** For receivers that don't reschedule: refreshes off the main thread, keeping the broadcast alive until done. */
    fun refreshAllAsync(receiver: BroadcastReceiver, context: Context) {
        val pending = receiver.goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
