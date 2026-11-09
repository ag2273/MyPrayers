package app.mawaqeet.core

import java.time.Duration

/** The phone's ringer setting, as the Android side reads it from the audio manager. */
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/** What the phone is doing when an alert fires. */
data class DeviceAudio(val ringer: RingerMode, val inCall: Boolean)

/**
 * What an alert actually does on the phone right now: which sound to [play] (null = none) and whether to [vibrate].
 * The notification is always posted; this only decides sound and vibration.
 *
 * The adhan plays on the alarm stream, which Android does not mute in silent or vibrate mode, so the ringer is
 * respected here rather than by the OS.
 */
data class AlertPlayback(val play: AlertSound?, val vibrate: Boolean) {
    companion object {
        /** An alert delivered later than this (the phone was off, or the alarm was held back) is never played aloud. */
        val MAX_LATENESS: Duration = Duration.ofMinutes(10)

        /**
         * @param late how long after its planned instant the alert is being delivered.
         */
        fun decide(sound: AlertSound, device: DeviceAudio, late: Duration): AlertPlayback {
            val vibrate = device.ringer != RingerMode.SILENT
            val audible = sound != AlertSound.Silent &&
                device.ringer == RingerMode.NORMAL &&
                late <= MAX_LATENESS &&
                !(device.inCall && sound.isFullLength)
            return AlertPlayback(play = sound.takeIf { audible }, vibrate = vibrate)
        }

        /** Everything but the Short Adhan; a Custom Adhan counts as full length, since its length is unknown. */
        private val AlertSound.isFullLength: Boolean get() = this != AlertSound.ShortAdhan
    }
}
