package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlayback
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import java.time.Duration

/**
 * Receives the scheduler's alarms (direct-boot aware, so it also fires before the first unlock):
 * - [ACTION_ALERT] at each Prayer Time and Prayer Reminder: asks the core what to play ([AlertPlayback.decide]), hands that to
 *   [AlertOutput] (vibration, then the adhan player or a plain notification), then reschedules.
 * - [ACTION_MIDNIGHT] at local midnight: reschedules (the display day changes; widgets refresh here in #27).
 */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALERT -> {
                val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { name -> Prayer.entries.firstOrNull { it.name == name } }
                if (prayer != null) {
                    val now = System.currentTimeMillis()
                    val at = intent.getLongExtra(EXTRA_AT, now)
                    val kind = intent.getStringExtra(EXTRA_KIND)?.let { name -> AlertKind.entries.firstOrNull { it.name == name } }
                        ?: AlertKind.ADHAN_ALERT
                    val notification = AdhanNotification(
                        prayer,
                        intent.getBooleanExtra(EXTRA_JUMUAH, false),
                        intent.getIntExtra(EXTRA_REQUEST_CODE, 1),
                        kind,
                        intent.getLongExtra(EXTRA_PRAYER_TIME, at),
                    )
                    val sound = intent.getStringExtra(EXTRA_SOUND)?.let(::decodeAlertSound) ?: AlertSound.ShortAdhan
                    val late = Duration.ofMillis((now - at).coerceAtLeast(0))
                    // Settings travel in the intent (from the plan): DataStore is unreadable before the first unlock.
                    val playback = AlertPlayback.decide(
                        sound,
                        AdhanSounds.deviceAudio(context),
                        late,
                        playOnSilent = intent.getBooleanExtra(EXTRA_PLAY_ON_SILENT, false),
                        vibration = intent.getBooleanExtra(EXTRA_VIBRATION, true),
                    )
                    AlertOutput.deliver(context, notification, sound, playback)
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

        /** [AlertKind] name; missing reads as an Adhan Alert. */
        const val EXTRA_KIND = "kind"

        /** The Prayer Time (epoch millis); for a Prayer Reminder, later than [EXTRA_AT]. */
        const val EXTRA_PRAYER_TIME = "prayer_time"

        /** [app.mawaqeet.core.PlannedAlert.playOnSilent] / [app.mawaqeet.core.PlannedAlert.vibration]. */
        const val EXTRA_PLAY_ON_SILENT = "play_on_silent"
        const val EXTRA_VIBRATION = "vibration"
    }
}
