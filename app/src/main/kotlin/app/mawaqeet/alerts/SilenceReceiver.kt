package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import java.time.Instant
import java.time.LocalDate

/**
 * Receives a Silence Window's two alarms (direct-boot aware): [ACTION_START] silences the phone, [ACTION_END] restores
 * it, both through [PrayerSilencer]. The window travels in the intent, since settings can't be read before the first
 * unlock. Nothing is rescheduled here: the plan moves on with the Adhan Alerts and the display alarm.
 */
class SilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val window = window(intent) ?: return
        when (intent.action) {
            ACTION_START -> PrayerSilencer(context).start(window)
            ACTION_END -> PrayerSilencer(context).end(window)
        }
    }

    companion object {
        const val ACTION_START = "app.mawaqeet.action.SILENCE_START"
        const val ACTION_END = "app.mawaqeet.action.SILENCE_END"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_PRAYER_DAY = "prayer_day"
        private const val EXTRA_START = "start"
        private const val EXTRA_END = "end"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_JUMUAH = "jumuah"

        /** The broadcast [window]'s start ([start] true) or end alarm delivers. */
        internal fun intent(context: Context, window: SilenceWindow, start: Boolean): Intent =
            Intent(context, SilenceReceiver::class.java)
                .setAction(if (start) ACTION_START else ACTION_END)
                .putExtra(EXTRA_PRAYER, window.prayer.name)
                .putExtra(EXTRA_PRAYER_DAY, window.prayerDay.toString())
                .putExtra(EXTRA_START, window.start.toEpochMilli())
                .putExtra(EXTRA_END, window.end.toEpochMilli())
                .putExtra(EXTRA_MODE, window.mode.name)
                .putExtra(EXTRA_JUMUAH, window.isJumuah)

        private fun window(intent: Intent): SilenceWindow? = runCatching {
            SilenceWindow(
                prayer = Prayer.valueOf(intent.getStringExtra(EXTRA_PRAYER)!!),
                prayerDay = LocalDate.parse(intent.getStringExtra(EXTRA_PRAYER_DAY)!!),
                start = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START, 0)),
                end = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_END, 0)),
                mode = SilenceMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: SilenceMode.SILENT.name),
                isJumuah = intent.getBooleanExtra(EXTRA_JUMUAH, false),
            )
        }.getOrNull()
    }
}
