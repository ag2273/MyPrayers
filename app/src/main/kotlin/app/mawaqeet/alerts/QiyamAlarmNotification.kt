package app.mawaqeet.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.R
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.QiyamStyle

/**
 * The Qiyam reminder in alarm style «منبّه» (#97), on the alarm-clock mode path: a full-screen notification that opens
 * [QiyamAlarmActivity] over the lock screen, ringing with the phone's default alarm sound on the alarm stream (never an
 * adhan) until Stop, a tap or a swipe ends it, or by itself after [QiyamStyle.ALARM_RINGS_FOR]. The sound is the
 * channel's, looped by [Notification.FLAG_INSISTENT], so it lasts exactly as long as the notification; no service plays it.
 * Posted by [WorshipReminderNotification] only where full-screen alarms are allowed ([AdhanNotification.canShowOverLockScreen]).
 */
internal object QiyamAlarmNotification {
    /** Its own channel: a channel's sound is fixed once created, and this one rings like an alarm. */
    const val CHANNEL = "qiyam_alarm"

    fun post(context: Context, reminder: PlannedWorshipReminder, title: String, text: String) {
        ensureChannel(context)
        val id = WorshipReminderNotification.id(reminder.kind)
        val ringsFor = QiyamStyle.ALARM_RINGS_FOR.toMillis()
        val until = System.currentTimeMillis() + ringsFor
        val stop = WorshipReminderReceiver.dismissIntent(context, id)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            // A tap stops it too (the notification goes, and its sound with it) and opens the app.
            .setContentIntent(AlertScheduler.openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(reminder.instant.toEpochMilli())
            .setFullScreenIntent(QiyamAlarmActivity.pendingIntent(context, id, title, text, until), true)
            .addAction(NotificationCompat.Action(0, alertText(context).getString(R.string.adhan_stop), stop))
            .setTimeoutAfter(ringsFor)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel(context: Context) {
        val text = alertText(context)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, text.getString(R.string.qiyam_alarm_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = text.getString(R.string.qiyam_alarm_channel_description)
                setSound(
                    Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }
}
