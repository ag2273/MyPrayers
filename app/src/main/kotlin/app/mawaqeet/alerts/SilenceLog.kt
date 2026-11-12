package app.mawaqeet.alerts

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The last [MAX_LINES] things Prayer Silence did, each with the ringer mode and Do Not Disturb filter it saw, kept on
 * the phone only (device-protected storage, so a window ending before the first unlock is logged too). Phones differ
 * in what they do around an app's silencing (Samsung turns Do Not Disturb on and can move the ringer), so this is what
 * the user can copy from Settings → Alerts when the sound did not come back.
 */
internal object SilenceLog {
    private const val PREFS = "prayer_silence_log"
    private const val KEY = "lines"
    const val MAX_LINES = 60

    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    fun add(context: Context, event: String, ringer: Int, filter: Int, now: Instant = Instant.now()) {
        val line = "${time.format(now.atZone(ZoneId.systemDefault()))} $event ringer=${ringerName(ringer)} dnd=$filter"
        synchronized(this) {
            val prefs = prefs(context)
            val lines = (prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() } + line).takeLast(MAX_LINES)
            prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
        }
    }

    fun read(context: Context): String = prefs(context).getString(KEY, "").orEmpty()

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ringerName(mode: Int) = when (mode) {
        0 -> "silent"
        1 -> "vibrate"
        2 -> "normal"
        else -> mode.toString()
    }
}
