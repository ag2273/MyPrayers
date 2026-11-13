package app.mawaqeet.alerts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.WorshipReminderKind
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Receives a Worship Reminder's alarm (#63, direct-boot aware) and posts [WorshipReminderNotification], off the main
 * thread. The reminder travels in the intent. Nothing is rescheduled here: the plan moves on with the Adhan Alerts and the display alarm.
 * An adhkar reminder (#75) first asks the Adhkar core, off the main thread, whether its set is already complete for
 * the reminder's Prayer Day, and is dropped if so; when that cannot be read (before the first unlock) it is posted.
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
        // Preferences (the silence record) and the database: off the main thread.
        ReceiverWork.run(this) {
            // A Silence Window whose end alarm never came gets its sound back now.
            PrayerSilencer(context).endIfOverdue()
            // Inside a Silence Window the reminder must not break the silence (#76).
            val silent = PrayerSilencer(context).isActive()
            val set = reminder.kind.adhkarSet
            if (set == null || !isComplete(context, set, reminder.prayerDay)) {
                WorshipReminderNotification.post(context, reminder, silent = silent)
            }
        }
    }

    private suspend fun isComplete(context: Context, set: AdhkarSetId, prayerDay: LocalDate): Boolean {
        val app = context.applicationContext as? MawaqeetApplication ?: return false
        return runCatching {
            val core = PrayerCore(app.settings.settings.first(), Clock.systemUTC(), app.prayerLog)
            Adhkar(app.adhkarCatalog, core, app.adhkar).isComplete(set, prayerDay)
        }.getOrDefault(false)
    }

    companion object {
        const val ACTION_REMIND = "app.mawaqeet.action.WORSHIP_REMINDER"

        /** Waits for the last reminder to be checked and posted (tests). */
        internal suspend fun idle() = ReceiverWork.idle()

        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PRAYER_DAY = "prayer_day"
        private const val EXTRA_AT = "at"
        private const val EXTRA_SLOT = "slot"

        /** A Sunnah Fast reminder's [app.mawaqeet.core.FastDay] (#95), as [encodeFastDay] writes it. */
        private const val EXTRA_FAST = "fast"

        /** An alarm-style Qiyam reminder (#97): [PlannedWorshipReminder.alarm]. */
        private const val EXTRA_ALARM = "alarm"

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
                .putExtra(EXTRA_FAST, encodeFastDay(reminder.fast))
                .putExtra(EXTRA_ALARM, reminder.alarm)

        private fun reminder(intent: Intent): PlannedWorshipReminder? = runCatching {
            PlannedWorshipReminder(
                kind = WorshipReminderKind.valueOf(intent.getStringExtra(EXTRA_KIND)!!),
                instant = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, 0)),
                prayerDay = LocalDate.parse(intent.getStringExtra(EXTRA_PRAYER_DAY)!!),
                slot = intent.getIntExtra(EXTRA_SLOT, 0),
                fast = decodeFastDay(intent.getStringExtra(EXTRA_FAST).orEmpty()),
                alarm = intent.getBooleanExtra(EXTRA_ALARM, false),
            )
        }.getOrNull()
    }
}
