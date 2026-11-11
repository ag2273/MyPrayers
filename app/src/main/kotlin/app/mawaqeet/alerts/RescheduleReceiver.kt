package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.os.UserManagerCompat
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.widget.Widgets
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * System broadcasts that invalidate the scheduled alarms: boot (alarms don't survive it), locked boot (before
 * the first unlock, from the device-protected plan copy), time set, time-zone change, app update and the
 * exact-alarm permission being granted. The manifest lists the actions; all of them simply reschedule.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) markLocationMaybeStale(context)
        when (intent.action) {
            // The widgets' times and countdown follow the clock, zone and plan; refreshAll skips a locked boot.
            // A Silence Window caught by a reboot or a clock change: restored at once if its end has passed.
            in ACTIONS -> AlertRescheduler.rescheduleAsync(this, context, refreshWidgets = true) {
                PrayerSilencer.inAppStyle(context).catchUp(Instant.now())
            }
            // The system language changed: widgets following it re-render (the alarms don't depend on it).
            Intent.ACTION_LOCALE_CHANGED -> Widgets.refreshAllAsync(this, context)
        }
    }

    /**
     * A time-zone change is a cheap travel signal, but location is never read in the background: it only flags
     * the stored location as possibly stale, and Home shows a hint until the next location check.
     */
    private fun markLocationMaybeStale(context: Context) {
        val app = context.applicationContext as? MawaqeetApplication ?: return
        if (!UserManagerCompat.isUserUnlocked(context)) return
        app.appScope.launch { app.settings.setLocationMaybeStale(true) }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
