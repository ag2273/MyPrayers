package app.mawaqeet.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.mawaqeet.alerts.AlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Re-renders the Next Prayer widgets when their countdown drops under an hour, so its unit turns from «ساعة» to
 * «دقيقة» as the launcher's `Chronometer` goes from H:MM:SS to MM:SS. One alarm at a time: each render replaces it.
 */
class CountdownUnitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Widgets.refresh(context.applicationContext, listOf(::NextPrayerWidget, ::NextPrayerRingWidget))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Arms the switch for a countdown of [untilNext] from now; under an hour there is nothing to switch. */
        fun schedule(context: Context, untilNext: Duration) {
            val left = untilNext.minusHours(1)
            if (left.isNegative || left.isZero) return
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val operation = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, CountdownUnitReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // A second past the hour mark, so the render sees the countdown under it.
            val at = SystemClock.elapsedRealtime() + left.toMillis() + 1_000
            if (AlertScheduler.canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, operation)
            }
        }
    }
}
