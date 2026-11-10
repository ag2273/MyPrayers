package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlayback
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import java.time.Duration
import java.time.LocalDate

/**
 * Receives the scheduler's alarms (direct-boot aware, so it also fires before the first unlock):
 * - [ACTION_ALERT] at each Prayer Time and Prayer Reminder: asks the core what to play ([AlertPlayback.decide]), hands that to
 *   [AlertOutput] (vibration, then the adhan player or a plain notification), then reschedules.
 * - [ACTION_MIDNIGHT], the display alarm ([app.mawaqeet.core.AlertPlan.nextDisplayChange]): at local midnight, or at a
 *   Prayer Time with no Adhan Alert. Reschedules only.
 * Both then refresh every widget ([app.mawaqeet.widget.Widgets.refreshAll]).
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
                        prayerDay(intent),
                        fullScreen = intent.getBooleanExtra(EXTRA_FULL_SCREEN, false),
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
                    AlertOutput.deliver(context, notification, sound, playback, adhanVolume(intent))
                }
            }
            ACTION_MIDNIGHT -> Unit
            else -> return
        }
        // Both mark a display change (a Prayer Time or local midnight), so the widgets move on. Before the first unlock
        // Widgets.refreshAll does nothing; BOOT_COMPLETED refreshes them.
        AlertRescheduler.rescheduleAsync(this, context, refreshWidgets = true)
    }

    companion object {
        /** The alert's Prayer Day from [EXTRA_PRAYER_DAY], or null if missing or unreadable (no "Mark as prayed"). */
        internal fun prayerDay(intent: Intent): LocalDate? =
            intent.getStringExtra(EXTRA_PRAYER_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /** The alert's [EXTRA_ADHAN_VOLUME]; missing or unknown reads as the alarm volume. */
        internal fun adhanVolume(intent: Intent): AdhanVolume =
            AdhanVolume.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_ADHAN_VOLUME) } ?: AdhanVolume.ALARM

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

        /** The alert's Prayer Day ([app.mawaqeet.core.PlannedAlert.prayerDay], ISO date), for "Mark as prayed". */
        const val EXTRA_PRAYER_DAY = "prayer_day"
        const val EXTRA_VIBRATION = "vibration"

        /** [app.mawaqeet.core.PlannedAlert.adhanVolume] name: whether the adhan follows the alarm or media volume. */
        const val EXTRA_ADHAN_VOLUME = "adhan_volume"

        /** Alarm-clock mode: show the alert over the lock screen ([AdhanAlarmActivity]). */
        const val EXTRA_FULL_SCREEN = "full_screen"
    }
}
