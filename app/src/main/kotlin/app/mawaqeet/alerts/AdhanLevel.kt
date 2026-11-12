package app.mawaqeet.alerts

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import app.mawaqeet.core.AdhanVolume
import kotlin.math.roundToInt

/**
 * How loud the adhan plays: the level the user set with the slider in Settings → Alerts «صوت الأذان», one per
 * [AdhanVolume] stream, as a fraction of that stream's range.
 *
 * The phone's alarm (or media) volume is shared: another alarm app, a Bedtime or Sleep routine, Do Not Disturb
 * schedules, or the volume keys while earbuds were connected can all move it after the slider was set, and then the
 * adhan used to play at whatever the stream held at that moment, often far louder than chosen. So the player sets the
 * stream to this level while the adhan plays ([apply]) and puts the phone's own level back afterwards
 * ([Applied.restore]).
 *
 * Kept in device-protected storage, like the alert plan, so an adhan that fires before the first unlock still finds it.
 * Nothing stored (the slider was never moved) leaves the stream as it is.
 */
class AdhanLevel(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The chosen level for [volume], 0..1 of the stream's range, or null when the user never set one. */
    fun get(volume: AdhanVolume): Float? =
        if (prefs.contains(key(volume))) prefs.getFloat(key(volume), 0f).coerceIn(0f, 1f) else null

    fun set(volume: AdhanVolume, fraction: Float) {
        prefs.edit().putFloat(key(volume), fraction.coerceIn(0f, 1f)).apply()
    }

    /** Records the stream's current [index] as the chosen level for [volume] (what the slider just set). */
    fun setIndex(audio: AudioManager, volume: AdhanVolume, index: Int) {
        val stream = AdhanSounds.stream(volume)
        val (min, max) = range(audio, stream)
        set(volume, (index - min).toFloat() / (max - min).coerceAtLeast(1))
    }

    /** The stream index the chosen level for [volume] maps to, or null when none is chosen. */
    fun index(audio: AudioManager, volume: AdhanVolume): Int? {
        val fraction = get(volume) ?: return null
        val (min, max) = range(audio, AdhanSounds.stream(volume))
        return (min + fraction * (max - min)).roundToInt().coerceIn(min, max)
    }

    /**
     * Sets [volume]'s stream to the chosen level for the adhan about to play. Returns what to undo afterwards, or null
     * when nothing was changed (no level chosen, already there, or Do Not Disturb refused the change).
     */
    fun apply(audio: AudioManager, volume: AdhanVolume): Applied? {
        val target = index(audio, volume) ?: return null
        val stream = AdhanSounds.stream(volume)
        val previous = audio.getStreamVolume(stream)
        if (previous == target) return null
        return try {
            audio.setStreamVolume(stream, target, 0)
            Applied(stream, previous, audio.getStreamVolume(stream))
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't set the adhan's volume", e)
            null
        }
    }

    /** A stream the player changed for one adhan. */
    class Applied internal constructor(private val stream: Int, private val previous: Int, private val set: Int) {
        /**
         * Puts the phone's own level back, unless the user changed the volume meanwhile (the volume keys during the
         * adhan): then their new level stays.
         */
        fun restore(audio: AudioManager) {
            if (audio.getStreamVolume(stream) != set) return
            runCatching { audio.setStreamVolume(stream, previous, 0) }
        }
    }

    private fun range(audio: AudioManager, stream: Int): Pair<Int, Int> {
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(stream) else 0
        return min to audio.getStreamMaxVolume(stream).coerceAtLeast(min + 1)
    }

    private fun key(volume: AdhanVolume) = "level_${volume.name}"

    private companion object {
        const val PREFS = "adhan_level"
        const val TAG = "AdhanLevel"
    }
}
