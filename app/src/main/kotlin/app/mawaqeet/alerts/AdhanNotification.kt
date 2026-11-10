package app.mawaqeet.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mawaqeet.R
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import java.time.LocalDate

/**
 * The alert notification, shared by [AlertReceiver] (no sound) and [AdhanPlayerService] (while playing). A
 * [AlertKind.PRAYER_REMINDER] says the Prayer is near and counts down to [prayerTime] (epoch millis).
 *
 * An Adhan Alert with its [prayerDay] carries "Mark as prayed" (#26), which marks [prayer] on that Prayer Day through
 * [MarkPrayedReceiver], so Isha after midnight lands on the previous Prayer Day like a tap on Home.
 */
data class AdhanNotification(
    val prayer: Prayer,
    val isJumuah: Boolean,
    val id: Int,
    val kind: AlertKind = AlertKind.ADHAN_ALERT,
    val prayerTime: Long = 0,
    val prayerDay: LocalDate? = null,
    /** Alarm-clock mode: the Adhan Alert also opens [AdhanAlarmActivity] over the lock screen, like an alarm. */
    val fullScreen: Boolean = false,
) {

    /**
     * The notification's builder on [channel], with "Mark as prayed" on an Adhan Alert. Callers add their own
     * actions: the player adds Stop while it plays.
     */
    fun builder(context: Context, channel: String = CHANNEL_ADHAN): NotificationCompat.Builder {
        ensureChannels(context)
        val name = context.getString(if (isJumuah) R.string.prayer_jumuah else prayer.nameRes)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(AlertScheduler.openAppIntent(context))
            .setAutoCancel(true)
        return if (kind == AlertKind.PRAYER_REMINDER) {
            builder
                .setContentTitle(context.getString(R.string.prayer_reminder_title, name))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setWhen(prayerTime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter((prayerTime - System.currentTimeMillis()).coerceAtLeast(60_000))
        } else {
            builder
                .setContentTitle(context.getString(R.string.adhan_alert_title, name))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .apply { if (fullScreen) setFullScreenIntent(AdhanAlarmActivity.pendingIntent(context, this@AdhanNotification), true) }
                .apply {
                    val day = prayerDay ?: return@apply
                    addAction(
                        NotificationCompat.Action(
                            0,
                            context.getString(R.string.mark_as_prayed),
                            MarkPrayedReceiver.intent(context, prayer, day, id),
                        ),
                    )
                }
        }
    }

    /** Posts (or replaces) the notification if notifications are allowed. */
    fun post(context: Context, notification: Notification = builder(context).build()) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        /**
         * Silent channel: channel sound and behaviour are fixed once created, and the adhan comes from
         * [AdhanPlayerService]. Vibration is done by the app ([AlertOutput]), so the channel doesn't vibrate.
         */
        const val CHANNEL_ADHAN = "adhan_alerts"

        /**
         * Fallback channel (build flag `ADHAN_CHANNEL_SOUND_FALLBACK`): the bundled adhan is the channel's own sound
         * on the alarm stream; dismissing the notification stops it. One channel per sound: a new bundled sound or a
         * Custom Adhan needs a new channel id (sounds can't change after creation).
         */
        const val CHANNEL_ADHAN_SOUND = "adhan_alerts_sound_v1"

        /**
         * Prayer Reminders: the system's default short notification sound (respects silent and vibrate mode by
         * itself); vibration is done by the app ([AlertOutput]) so the vibration setting applies.
         */
        const val CHANNEL_REMINDERS = "prayer_reminders"

        fun canPost(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ADHAN, context.getString(R.string.adhan_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.adhan_channel_description)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.reminder_channel_description)
                    setSound(
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(false)
                },
            )
            if (app.mawaqeet.BuildConfig.ADHAN_CHANNEL_SOUND_FALLBACK) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ADHAN_SOUND,
                        context.getString(R.string.adhan_sound_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = context.getString(R.string.adhan_sound_channel_description)
                        setSound(
                            AdhanSounds.bundled(context, AlertSound.FullAdhan),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        enableVibration(false)
                    },
                )
            }
        }

        private val Prayer.nameRes: Int
            get() = when (this) {
                Prayer.FAJR -> R.string.prayer_fajr
                Prayer.DHUHR -> R.string.prayer_dhuhr
                Prayer.ASR -> R.string.prayer_asr
                Prayer.MAGHRIB -> R.string.prayer_maghrib
                Prayer.ISHA -> R.string.prayer_isha
            }
    }
}
