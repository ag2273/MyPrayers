package app.mawaqeet.alerts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * The Adhan Alert's "Mark as prayed" action (#26). Calls the prayer core's mark command for the alert's (Prayer Day,
 * Prayer), so it follows the same markability and edit-window rules as a tap on Home, then dismisses the
 * notification (stopping its adhan if it is still playing). It may run hours after the alert: the mark lands as
 * long as that Prayer Day is still in the edit window. Not direct-boot aware: the Prayer Log needs an unlocked phone.
 */
class MarkPrayedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_PRAYED) return
        val prayer = intent.getStringExtra(AlertReceiver.EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
        val day = AlertReceiver.prayerDay(intent)
        val id = intent.getIntExtra(AlertReceiver.EXTRA_REQUEST_CODE, -1)
        if (AdhanPlayerService.playingId == id) context.stopService(Intent(context, AdhanPlayerService::class.java))
        NotificationManagerCompat.from(context).cancel(id)
        if (prayer == null || day == null) return
        val app = context.applicationContext as MawaqeetApplication
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = PrayerCore(app.settings.settings.first(), Clock.systemUTC(), app.prayerLog).mark(day, prayer)
                Log.i(TAG, "Mark as prayed $prayer on $day: $result")
                Widgets.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MarkPrayed"
        const val ACTION_MARK_PRAYED = "app.mawaqeet.action.MARK_PRAYED"

        /** The action's broadcast for one Adhan Alert; [id] is the notification's id, unique per planned alert. */
        fun intent(context: Context, prayer: Prayer, prayerDay: LocalDate, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, MarkPrayedReceiver::class.java)
                .setAction(ACTION_MARK_PRAYED)
                .putExtra(AlertReceiver.EXTRA_PRAYER, prayer.name)
                .putExtra(AlertReceiver.EXTRA_PRAYER_DAY, prayerDay.toString())
                .putExtra(AlertReceiver.EXTRA_REQUEST_CODE, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
