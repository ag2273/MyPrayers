package app.mawaqeet.alerts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.WorshipReminderKind
import java.time.Instant
import java.time.LocalDate

/**
 * Receives a Worship Reminder's alarm (#63, direct-boot aware) and posts [WorshipReminderNotification]. The reminder
 * travels in the intent. Nothing is rescheduled here: the plan moves on with the Adhan Alerts and the display alarm.
 * Also receives «قرأتها» ([ACTION_DISMISS], #64), which only dismisses the reminder.
 */
class WorshipReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS) {
            WorshipReminderNotification.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
            return
        }
        if (intent.action != ACTION_REMIND) return
        val reminder = reminder(intent) ?: return
        WorshipReminderNotification.post(context, reminder)
    }

    companion object {
        const val ACTION_REMIND = "app.mawaqeet.action.WORSHIP_REMINDER"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PRAYER_DAY = "prayer_day"
        private const val EXTRA_AT = "at"
        private const val EXTRA_SLOT = "slot"

        /** A reminder's «قرأتها» (#64): dismisses its notification, nothing else. */
        const val ACTION_DISMISS = "app.mawaqeet.action.WORSHIP_REMINDER_DISMISS"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        /** The broadcast that dismisses the Worship Reminder notification [id]. */
        internal fun dismissIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, WorshipReminderReceiver::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_NOTIFICATION_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** The broadcast [reminder]'s alarm delivers. */
        internal fun intent(context: Context, reminder: PlannedWorshipReminder): Intent =
            Intent(context, WorshipReminderReceiver::class.java)
                .setAction(ACTION_REMIND)
                .putExtra(EXTRA_KIND, reminder.kind.name)
                .putExtra(EXTRA_PRAYER_DAY, reminder.prayerDay.toString())
                .putExtra(EXTRA_AT, reminder.instant.toEpochMilli())
                .putExtra(EXTRA_SLOT, reminder.slot)

        private fun reminder(intent: Intent): PlannedWorshipReminder? = runCatching {
            PlannedWorshipReminder(
                kind = WorshipReminderKind.valueOf(intent.getStringExtra(EXTRA_KIND)!!),
                instant = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, 0)),
                prayerDay = LocalDate.parse(intent.getStringExtra(EXTRA_PRAYER_DAY)!!),
                slot = intent.getIntExtra(EXTRA_SLOT, 0),
            )
        }.getOrNull()
    }
}
