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
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer

/** The Adhan Alert notification, shared by [AlertReceiver] (no sound) and [AdhanPlayerService] (while playing). */
data class AdhanNotification(val prayer: Prayer, val isJumuah: Boolean, val id: Int) {

    /**
     * The notification's builder on [channel]. Callers add their own actions: the player adds Stop while it plays;
     * "Mark as prayed" (#26) belongs here so every variant has it.
     */
    fun builder(context: Context, channel: String = CHANNEL_ADHAN): NotificationCompat.Builder {
        ensureChannels(context)
        val name = context.getString(if (isJumuah) R.string.prayer_jumuah else prayer.nameRes)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Placeholder until the app icon (#33).
            .setContentTitle(context.getString(R.string.adhan_alert_title, name))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(AlertScheduler.openAppIntent(context))
            .setAutoCancel(true)
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
