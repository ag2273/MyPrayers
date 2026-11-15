package app.mawaqeet.alerts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.alerts.AlertScheduler.Companion.startRequestCode
import app.mawaqeet.core.SilenceWindow

/**
 * Receives a Silence Window's two alarms (direct-boot aware): [ACTION_START] (set only when no Adhan Alert opens the
 * window; otherwise [AlertReceiver] starts it once the adhan stops) silences the phone, [ACTION_END] restores
 * it, and [ACTION_RESTORE] («استعادة الصوت» on the notification) restores it early, all through [PrayerSilencer]. The
 * window travels in the intent and the notification's digits and language in the stored plan, since settings can't
 * be read before the first unlock (and a receiver never reads them). Nothing is rescheduled here: the plan moves on
 * with the Adhan Alerts and the display alarm.
 */
class SilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val window = intent.silenceWindow() ?: return
        val action = intent.action
        val work = when (action) {
            ACTION_START -> SilenceService.ACTION_START
            ACTION_END -> SilenceService.ACTION_END
            ACTION_RESTORE -> SilenceService.ACTION_RESTORE
            else -> return
        }
        // In a foreground service on Android 17, which ignores an app's ringer changes from the background; elsewhere
        // here, off the main thread (preferences, the stored plan and AudioManager).
        SilenceService.run(context, work, window) { block -> ReceiverWork.run(this) { block() } }
    }

    companion object {
        const val ACTION_START = "app.mawaqeet.action.SILENCE_START"
        const val ACTION_END = "app.mawaqeet.action.SILENCE_END"
        const val ACTION_RESTORE = "app.mawaqeet.action.SILENCE_RESTORE"

        /** The broadcast [window]'s start ([start] true) or end alarm delivers. */
        internal fun intent(context: Context, window: SilenceWindow, start: Boolean): Intent =
            Intent(context, SilenceReceiver::class.java)
                .setAction(if (start) ACTION_START else ACTION_END)
                .putSilenceWindow(window)

        /** «استعادة الصوت» on [window]'s notification. */
        internal fun restoreIntent(context: Context, window: SilenceWindow): PendingIntent = PendingIntent.getBroadcast(
            context,
            window.startRequestCode,
            intent(context, window, start = false).setAction(ACTION_RESTORE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
