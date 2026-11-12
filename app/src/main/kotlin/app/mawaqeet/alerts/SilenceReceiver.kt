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
        // Preferences, the stored plan and AudioManager: off the main thread.
        ReceiverWork.run(this) {
            when (action) {
                ACTION_START -> PrayerSilencer(context).start(window)
                ACTION_END -> PrayerSilencer(context).end(window)
                ACTION_RESTORE -> PrayerSilencer(context).restore(window)
            }
        }
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
