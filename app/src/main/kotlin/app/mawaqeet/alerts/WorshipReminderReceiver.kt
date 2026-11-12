package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.WorshipReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Receives a Worship Reminder's alarm (#63, direct-boot aware) and posts [WorshipReminderNotification]. The reminder
 * travels in the intent. Nothing is rescheduled here: the plan moves on with the Adhan Alerts and the display alarm.
 * An adhkar reminder (#75) first asks the Adhkar core, off the main thread, whether its set is already complete for
 * the reminder's Prayer Day, and is dropped if so; when that cannot be read (before the first unlock) it is posted.
 */
class WorshipReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val reminder = reminder(intent) ?: return
        val set = reminder.kind.adhkarSet
        if (set == null) {
            WorshipReminderNotification.post(context, reminder)
            return
        }
        val pending = goAsync()
        lastCheck = scope.launch {
            try {
                if (!isComplete(context, set, reminder.prayerDay)) WorshipReminderNotification.post(context, reminder)
            } finally {
                pending.finish()
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

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var lastCheck: Job? = null

        /** Waits for the last adhkar reminder's completeness check (tests). */
        internal suspend fun idle() {
            lastCheck?.join()
        }
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
