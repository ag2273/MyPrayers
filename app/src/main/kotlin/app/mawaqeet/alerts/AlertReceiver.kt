package app.mawaqeet.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mawaqeet.R
import app.mawaqeet.core.Prayer

/**
 * Receives the scheduler's alarms (direct-boot aware, so it also fires before the first unlock):
 * - [ACTION_ALERT] at each Prayer Time: posts the Adhan Alert notification, then reschedules.
 * - [ACTION_MIDNIGHT] at local midnight: reschedules (the display day changes; widgets refresh here in #27).
 */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALERT -> {
                val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { name -> Prayer.entries.firstOrNull { it.name == name } }
                if (prayer != null) {
                    postAdhanAlert(context, prayer, intent.getBooleanExtra(EXTRA_JUMUAH, false), intent.getIntExtra(EXTRA_REQUEST_CODE, 1))
                }
                // #22 starts the Adhan player here; #27 refreshes widgets here.
            }
            ACTION_MIDNIGHT -> Unit // #27 refreshes widgets here.
            else -> return
        }
        AlertRescheduler.rescheduleAsync(this, context)
    }

    private fun postAdhanAlert(context: Context, prayer: Prayer, isJumuah: Boolean, id: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val name = context.getString(if (isJumuah) R.string.prayer_jumuah else prayer.nameRes)
        val notification = NotificationCompat.Builder(context, CHANNEL_ADHAN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Placeholder until the app icon (#33).
            .setContentTitle(context.getString(R.string.adhan_alert_title, name))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(AlertScheduler.openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val ACTION_ALERT = "app.mawaqeet.action.ADHAN_ALERT"
        const val ACTION_MIDNIGHT = "app.mawaqeet.action.LOCAL_MIDNIGHT"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_JUMUAH = "jumuah"
        const val EXTRA_REQUEST_CODE = "request_code"

        /**
         * Silent channel: notification sound and behaviour are fixed once a channel exists, and the adhan audio
         * comes from the player service (#22), not the channel.
         */
        const val CHANNEL_ADHAN = "adhan_alerts"

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ADHAN,
                context.getString(R.string.adhan_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.adhan_channel_description)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
