package app.mawaqeet.alerts

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeviceAudio
import app.mawaqeet.core.RingerMode

/** Maps the core's [AlertSound]s to audio the phone can play, and reads the phone's audio state. */
object AdhanSounds {
    /**
     * The bundled recording for a sound. `res/raw/adhan_placeholder` is generated at build time (see
     * `app/build.gradle.kts`): the dev-assets clip in debug builds when present, otherwise a silent clip. It
     * stands in for the Short, Full and Fajr Full Adhan until cleared recordings are bundled.
     *
     * Uses the name-based `android.resource://<package>/raw/<name>` form: resource ids can change on upgrade.
     */
    fun bundled(context: Context, sound: AlertSound): Uri {
        // A Custom Adhan's bundled sound is the recording it replaces ([AlertSound.CustomAdhan.fallback]).
        val bundledSound = if (sound is AlertSound.CustomAdhan) sound.fallback else sound
        val name = when (bundledSound) {
            AlertSound.ShortAdhan, AlertSound.FullAdhan, AlertSound.FajrFullAdhan, is AlertSound.CustomAdhan,
            AlertSound.Silent, AlertSound.ReminderTone,
            -> "adhan_placeholder"
        }
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("raw")
            .appendPath(name)
            .build()
    }

    /**
     * How an adhan plays for [volume]: on the alarm stream ([AdhanVolume.ALARM], the alarm volume) or the media stream
     * ([AdhanVolume.MEDIA], the media volume). Used by the adhan player and the Settings preview alike.
     */
    fun attributes(volume: AdhanVolume): AudioAttributes = when (volume) {
        AdhanVolume.ALARM -> AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        AdhanVolume.MEDIA -> AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    /** The [AudioManager] stream whose volume [volume] follows. */
    fun stream(volume: AdhanVolume): Int = when (volume) {
        AdhanVolume.ALARM -> AudioManager.STREAM_ALARM
        AdhanVolume.MEDIA -> AudioManager.STREAM_MUSIC
    }

    /** What to play: a Custom Adhan's own URI (#28), else the bundled recording. */
    fun uri(context: Context, sound: AlertSound): Uri =
        if (sound is AlertSound.CustomAdhan) Uri.parse(sound.reference) else bundled(context, sound)

    fun deviceAudio(context: Context): DeviceAudio {
        val audio = context.getSystemService(AudioManager::class.java)
        val ringer = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
        val inCall = audio.mode in setOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE)
        return DeviceAudio(ringer, inCall)
    }
}
