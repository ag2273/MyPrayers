package app.mawaqeet.alerts

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.mawaqeet.BuildConfig
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlayback
import app.mawaqeet.core.AlertSound

/**
 * Carries out the core's [AlertPlayback] decision for one alert: vibrates, then either starts
 * [AdhanPlayerService] (which posts the notification with Stop) or posts the notification itself. A Prayer
 * Reminder never starts the player: its sound is the reminder channel's.
 */
object AlertOutput {
    fun deliver(context: Context, notification: AdhanNotification, sound: AlertSound, playback: AlertPlayback) {
        if (playback.vibrate) vibrate(context)
        val play = playback.play
        when {
            notification.kind == AlertKind.PRAYER_REMINDER ->
                // The reminder channel's own short sound; a reminder that must stay quiet goes on the silent channel.
                notification.post(context, notification.builder(context, if (play == null) AdhanNotification.CHANNEL_ADHAN else AdhanNotification.CHANNEL_REMINDERS).build())
            play == null -> notification.post(context)
            BuildConfig.ADHAN_CHANNEL_SOUND_FALLBACK ->
                notification.post(context, notification.builder(context, AdhanNotification.CHANNEL_ADHAN_SOUND).build())
            else -> try {
                ContextCompat.startForegroundService(context, AdhanPlayerService.playIntent(context, notification, play))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (12+) is an IllegalStateException: never lose the alert.
                Log.w("AlertOutput", "Adhan player could not start", e)
                notification.post(context)
            }
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION") // The AudioAttributes overload covers API 26+; VibrationAttributes needs 33.
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1), attributes)
    }
}
