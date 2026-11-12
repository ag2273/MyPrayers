package app.mawaqeet.alerts

import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.MainActivity
import app.mawaqeet.R
import app.mawaqeet.core.AdhkarSlot
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.WorshipReminderKind

/**
 * A Worship Reminder's gentle notification (#63) on its own «تذكيرات العبادة» channel, with the system's default
 * notification sound (never an adhan). One notification per kind: a new salawat reminder replaces the last one.
 * Tapping opens the app; an adhkar reminder's tap opens its Adhkar Set (#75).
 * Tapping opens the app. The Surat Al-Kahf reminder (#64) also has «قرأتها», which only dismisses it.
 */
internal object WorshipReminderNotification {
    const val CHANNEL = "worship_reminders"

    /** Below the Silence notification's id and every alarm request code; one id per [WorshipReminderKind]. */
    private const val ID_BASE = 1_999_000

    /**
     * [silent] (during a Silence Window, #76) posts it without sound or vibration, whatever the channel says:
     * NotificationCompat's setSilent puts it in a group that alerts only through a summary, and none is posted.
     */
    fun post(context: Context, reminder: PlannedWorshipReminder, silent: Boolean = false) {
        if (!AdhanNotification.canPost(context)) return
        ensureChannel(context)
        val (title, text) = when (reminder.kind) {
            WorshipReminderKind.SALAWAT -> R.string.salawat_reminder_title to R.string.salawat_reminder_text
            WorshipReminderKind.KAHF -> R.string.kahf_reminder_title to R.string.kahf_reminder_text
            WorshipReminderKind.MORNING_ADHKAR -> R.string.adhkar_morning to R.string.adhkar_morning_reminder_text
            WorshipReminderKind.EVENING_ADHKAR -> R.string.adhkar_evening to R.string.adhkar_evening_reminder_text
        }
        val id = id(reminder.kind)
        val tap = reminder.kind.adhkarSet?.let { set ->
            PendingIntent.getActivity(
                context,
                id,
                MainActivity.openAdhkarIntent(context, AdhkarSlot.of(set)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } ?: AlertScheduler.openAppIntent(context)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setContentIntent(tap)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setWhen(reminder.instant.toEpochMilli())
            .setSilent(silent)
        if (reminder.kind == WorshipReminderKind.KAHF) {
            // «قرأتها» only dismisses: the app keeps no Kahf log.
            builder.addAction(
                NotificationCompat.Action(0, context.getString(R.string.kahf_reminder_read), WorshipReminderReceiver.dismissIntent(context, id)),
            )
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** The notification id of [kind]'s reminders: one per kind, so a new one replaces the last. */
    fun id(kind: WorshipReminderKind): Int = ID_BASE + kind.ordinal

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.worship_reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.worship_reminders_channel_description)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }
}
