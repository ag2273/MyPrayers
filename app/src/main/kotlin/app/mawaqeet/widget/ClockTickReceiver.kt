package app.mawaqeet.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.alerts.AlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Re-renders the Clock Widgets ([Widgets.clocks]) when their countdown and bar go stale: each whole minute, or at a
 * Stop that falls between minutes ([ClockContent.nextChange]). The launcher ticks the clock itself.
 *
 * Non-wakeup (`RTC`): with the screen off the alarm waits, and fires as soon as the phone wakes, so a Clock widget
 * costs nothing while nobody looks. One alarm at a time: each Clock render arms the next and replaces it. Only
 * a render arms it, so with no Clock widget on the home screen nothing renders and the chain stops; removing the
 * last one also cancels it ([cancel]).
 */
class ClockTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Widgets.refresh(context.applicationContext, Widgets.clocks)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Arms the next re-render at [at]. */
        fun schedule(context: Context, at: Instant) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val operation = operation(context)
            if (AlertScheduler.canScheduleExact(alarmManager)) {
                alarmManager.setExact(AlarmManager.RTC, at.toEpochMilli(), operation)
            } else {
                // Without exact alarms the system may deliver it a little late: the countdown still moves each minute or so.
                alarmManager.set(AlarmManager.RTC, at.toEpochMilli(), operation)
            }
        }

        /** Stops the re-renders: the last Clock widget has left the home screen. */
        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(operation(context))
        }

        private fun operation(context: Context) = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ClockTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
