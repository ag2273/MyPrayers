package app.mawaqeet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What a Silence Window does to the ringer. Stored by name; vibrate is added with #68. */
enum class SilenceMode { SILENT }

/**
 * Settings → Alerts → «الصامت وقت الصلاة»: Prayer Silence puts the phone on [mode] for a while at each Prayer, then
 * restores the previous ringer mode. Off by default. Independent of [AlertSettings.enabled]: it works with every app
 * alert off. Per-Prayer choices and adjustable durations (20 minutes, 45 for Jumu'ah) come with #68.
 */
data class PrayerSilence(
    val enabled: Boolean = false,
    val mode: SilenceMode = SilenceMode.SILENT,
) {
    /** How long [prayer]'s Silence Window lasts. */
    @Suppress("UNUSED_PARAMETER")
    fun duration(prayer: Prayer, isJumuah: Boolean): Duration = Duration.ofMinutes(DEFAULT_MINUTES)

    companion object {
        const val DEFAULT_MINUTES = 20L
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
