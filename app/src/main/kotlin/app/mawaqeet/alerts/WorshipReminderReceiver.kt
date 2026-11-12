package app.mawaqeet.alerts

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
 */
class WorshipReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val reminder = reminder(intent) ?: return
        // Inside a Silence Window the reminder must not break the silence (#76).
        WorshipReminderNotification.post(context, reminder, silent = PrayerSilencer(context).isActive())
    }

    companion object {
        const val ACTION_REMIND = "app.mawaqeet.action.WORSHIP_REMINDER"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PRAYER_DAY = "prayer_day"
        private const val EXTRA_AT = "at"
        private const val EXTRA_SLOT = "slot"

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
