package app.mawaqeet.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.R
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.WorshipReminderKind

/**
 * A Worship Reminder's gentle notification (#63) on its own «تذكيرات العبادة» channel, with the system's default
 * notification sound (never an adhan). One notification per kind: a new salawat reminder replaces the last one.
 * Tapping opens the app.
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
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setContentIntent(AlertScheduler.openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setWhen(reminder.instant.toEpochMilli())
            .setSilent(silent)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BASE + reminder.kind.ordinal, notification)
    }

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
