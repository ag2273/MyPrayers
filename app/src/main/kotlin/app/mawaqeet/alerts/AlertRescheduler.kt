package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.os.UserManagerCompat
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.widget.Widgets
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/**
 * The one entry point for (re)scheduling alerts. Every trigger (boot, locked boot, time set, time-zone change,
 * app update, exact-alarm grant, each alarm firing, each settings change, permissions granted in the app) calls
 * [reschedule]. Once the user has unlocked, it asks the core for a fresh plan from the stored settings; before
 * the first unlock it re-arms the plan copy in device-protected storage.
 */
object AlertRescheduler {
    private val mutex = Mutex()
    private val clock: Clock = Clock.systemUTC()

    suspend fun reschedule(context: Context, settings: PrayerSettings? = null) = mutex.withLock {
        val app = context.applicationContext as MawaqeetApplication
        val scheduler = AlertScheduler(app)
        if (UserManagerCompat.isUserUnlocked(app)) {
            val current = settings ?: app.settings.settings.first()
            val style = NotificationStyle(app.settings.digitStyle.first(), app.settings.appLanguage.first())
            scheduler.apply(PrayerCore(current, clock, app.prayerLog).alertPlan(), clock.instant(), style)
        } else {
            scheduler.rearmStoredPlan(clock.instant())
        }
    }

    /**
     * For receivers: reschedules off the main thread, keeping the broadcast alive until done. With [refreshWidgets]
     * it then refreshes every widget ([Widgets.refreshAll]; a broadcast can `goAsync` only once). [first] runs before
     * the reschedule, in the same background work.
     */
    fun rescheduleAsync(
        receiver: BroadcastReceiver,
        context: Context,
        refreshWidgets: Boolean = false,
        first: suspend () -> Unit = {},
    ) {
        val pending = receiver.goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                first()
                reschedule(context)
                if (refreshWidgets) Widgets.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
