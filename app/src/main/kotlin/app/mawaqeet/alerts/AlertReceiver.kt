package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlayback
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import java.time.Instant
import java.time.Duration
import java.time.LocalDate

/**
 * Receives the scheduler's alarms (direct-boot aware, so it also fires before the first unlock):
 * - [ACTION_ALERT] at each Prayer Time and Prayer Reminder: asks the core what to play ([AlertPlayback.decide]), hands that to
 *   [AlertOutput] (vibration, then the adhan player or a plain notification), then reschedules. An Adhan Alert also
 *   opens its Prayer's Silence Window, if one is planned: once the adhan stops, or at once when none plays.
 * - [ACTION_MIDNIGHT], the display alarm ([app.mawaqeet.core.AlertPlan.nextDisplayChange]): at local midnight, or at a
 *   Prayer Time with no Adhan Alert. Reschedules only.
 * Both then refresh every widget ([app.mawaqeet.widget.Widgets.refreshAll]).
 */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        var silence: SilenceWindow? = null
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
                    val adhanPlaying = AlertOutput.deliver(context, notification, sound, playback, adhanVolume(intent))
                    val window = if (kind == AlertKind.ADHAN_ALERT) silenceWindow(intent, prayer, notification.prayerDay) else null
                    when {
                        window == null -> Unit
                        // Recorded at once, before the player can stop.
                        adhanPlaying -> PrayerSilencer(context).awaitAdhan(window)
                        else -> silence = window
                    }
                }
            }
            ACTION_MIDNIGHT -> Unit
            else -> return
        }
        // Both mark a display change (a Prayer Time or local midnight), so the widgets move on. Before the first unlock
        // Widgets.refreshAll does nothing; BOOT_COMPLETED refreshes them.
        val startSilence = silence
        AlertRescheduler.rescheduleAsync(this, context, refreshWidgets = true) {
            startSilence?.let { PrayerSilencer.inAppStyle(context).start(it) }
        }
    }

    /**
     * The Silence Window this Adhan Alert's Prayer opens ([EXTRA_SILENCE_END]; the scheduler set no start alarm for it):
     * it starts when the adhan stops, or now when no adhan plays.
     */
    private fun silenceWindow(intent: Intent, prayer: Prayer, prayerDay: LocalDate?): SilenceWindow? {
        if (prayerDay == null || !intent.hasExtra(EXTRA_SILENCE_END)) return null
        val mode = SilenceMode.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SILENCE_MODE) } ?: return null
        return SilenceWindow(
            prayer = prayer,
            prayerDay = prayerDay,
            start = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SILENCE_START, 0)),
            end = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SILENCE_END, 0)),
            mode = mode,
            isJumuah = intent.getBooleanExtra(EXTRA_JUMUAH, false),
        )
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

        /**
         * The Silence Window this Adhan Alert opens, if Prayer Silence is on for its Prayer: start and planned end (epoch
         * millis) and [SilenceMode] name. Absent when there is none.
         */
        const val EXTRA_SILENCE_START = "silence_start"
        const val EXTRA_SILENCE_END = "silence_end"
        const val EXTRA_SILENCE_MODE = "silence_mode"
    }
}
