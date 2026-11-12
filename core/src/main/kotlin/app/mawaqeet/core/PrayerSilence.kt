package app.mawaqeet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What a Silence Window does to the ringer: «صامت» or «اهتزاز». Stored by name. */
enum class SilenceMode { SILENT, VIBRATE }

/**
 * Settings → Alerts → «الصامت وقت الصلاة»: Prayer Silence puts the phone on [mode] for a while at each chosen Prayer,
 * then restores the previous ringer mode. Off by default. Independent of [AlertSettings.enabled]: it works with every
 * app alert off. Each window lasts [minutes], except Friday Dhuhr (Jumu'ah), which lasts [jumuahMinutes].
 */
data class PrayerSilence(
    val enabled: Boolean = false,
    val mode: SilenceMode = SilenceMode.SILENT,
    /** The Prayers that get a Silence Window: every Prayer unless the user turns some off. */
    val prayers: Set<Prayer> = Prayer.entries.toSet(),
    /** How long a Silence Window lasts, within [MINUTES_RANGE]. */
    val minutes: Int = DEFAULT_MINUTES,
    /** How long Friday Dhuhr's Silence Window lasts, within [JUMUAH_MINUTES_RANGE], so the khutbah is covered. */
    val jumuahMinutes: Int = DEFAULT_JUMUAH_MINUTES,
) {
    init {
        require(minutes in MINUTES_RANGE) { "A Silence Window lasts $MINUTES_RANGE minutes, not $minutes" }
        require(jumuahMinutes in JUMUAH_MINUTES_RANGE) {
            "Jumu'ah's Silence Window lasts $JUMUAH_MINUTES_RANGE minutes, not $jumuahMinutes"
        }
    }

    fun isOn(prayer: Prayer): Boolean = prayer in prayers

    /** Turns Prayer Silence on or off for [prayer] alone. */
    fun with(prayer: Prayer, on: Boolean): PrayerSilence = copy(prayers = if (on) prayers + prayer else prayers - prayer)

    /** How long a Silence Window lasts: [jumuahMinutes] for Jumu'ah, else [minutes]. */
    fun duration(isJumuah: Boolean): Duration = Duration.ofMinutes((if (isJumuah) jumuahMinutes else minutes).toLong())

    /**
     * The duration ([jumuah]: Jumu'ah's) moved by [steps] taps of [MINUTES_STEP] minutes (negative: shorter), kept
     * within [MINUTES_RANGE] or [JUMUAH_MINUTES_RANGE]; the other duration is unchanged.
     */
    fun stepped(jumuah: Boolean, steps: Int): PrayerSilence = if (jumuah) {
        copy(jumuahMinutes = (jumuahMinutes + steps * MINUTES_STEP).coerceIn(JUMUAH_MINUTES_RANGE))
    } else {
        copy(minutes = (minutes + steps * MINUTES_STEP).coerceIn(MINUTES_RANGE))
    }

    companion object {
        const val DEFAULT_MINUTES = 20
        const val DEFAULT_JUMUAH_MINUTES = 45
        val MINUTES_RANGE = 5..60
        val JUMUAH_MINUTES_RANGE = 15..120

        /** One tap of the − or + button in Settings. */
        const val MINUTES_STEP = 5
    }
}

/**
 * One Silence Window the scheduler must open and close: the phone goes to [mode] at [start] (the Prayer Time) and
 * gets its previous ringer mode back at [end]. Identified by [prayerDay] and [prayer], like a [PlannedAlert].
 */
data class SilenceWindow(
    val prayer: Prayer,
    val prayerDay: LocalDate,
    val start: Instant,
    val end: Instant,
    val mode: SilenceMode,
    /** Dhuhr on a Friday. */
    val isJumuah: Boolean,
)
