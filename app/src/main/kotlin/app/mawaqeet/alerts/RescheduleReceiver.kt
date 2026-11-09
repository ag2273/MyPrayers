package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * System broadcasts that invalidate the scheduled alarms: boot (alarms don't survive it), locked boot (before
 * the first unlock, from the device-protected plan copy), time set, time-zone change, app update and the
 * exact-alarm permission being granted. The manifest lists the actions; all of them simply reschedule.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in ACTIONS) AlertRescheduler.rescheduleAsync(this, context)
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
