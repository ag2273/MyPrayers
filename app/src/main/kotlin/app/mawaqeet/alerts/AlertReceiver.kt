package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.core.AlertPlayback
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import java.time.Duration

/**
 * Receives the scheduler's alarms (direct-boot aware, so it also fires before the first unlock):
 * - [ACTION_ALERT] at each Prayer Time: asks the core what to play ([AlertPlayback.decide]), hands that to
 *   [AlertOutput] (vibration, then the adhan player or a plain notification), then reschedules.
 * - [ACTION_MIDNIGHT] at local midnight: reschedules (the display day changes; widgets refresh here in #27).
 */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALERT -> {
                val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { name -> Prayer.entries.firstOrNull { it.name == name } }
                if (prayer != null) {
                    val notification = AdhanNotification(prayer, intent.getBooleanExtra(EXTRA_JUMUAH, false), intent.getIntExtra(EXTRA_REQUEST_CODE, 1))
                    val sound = intent.getStringExtra(EXTRA_SOUND)?.let(::decodeAlertSound) ?: AlertSound.ShortAdhan
                    val now = System.currentTimeMillis()
                    val late = Duration.ofMillis((now - intent.getLongExtra(EXTRA_AT, now)).coerceAtLeast(0))
                    // #23: pass the "play even on silent" and vibration settings into decide().
                    AlertOutput.deliver(context, notification, sound, AlertPlayback.decide(sound, AdhanSounds.deviceAudio(context), late))
                }
                // #27 refreshes widgets here.
            }
            ACTION_MIDNIGHT -> Unit // #27 refreshes widgets here.
            else -> return
        }
        AlertRescheduler.rescheduleAsync(this, context)
    }

    companion object {
        const val ACTION_ALERT = "app.mawaqeet.action.ADHAN_ALERT"
        const val ACTION_MIDNIGHT = "app.mawaqeet.action.LOCAL_MIDNIGHT"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_JUMUAH = "jumuah"
        const val EXTRA_REQUEST_CODE = "request_code"

        /** The alert's [AlertSound], as [encodeAlertSound] writes it. */
        const val EXTRA_SOUND = "sound"

        /** The alert's planned instant (epoch millis), to tell a late delivery from an on-time one. */
        const val EXTRA_AT = "at"
    }
}
