package app.mawaqeet.core

import java.time.Instant
import java.time.LocalDate

/** Whether an alert announces the Prayer Time itself or comes before it. */
enum class AlertKind { ADHAN_ALERT, PRAYER_REMINDER }

/** What an alert sounds like. The Android side maps each to an audio source (#22, #28). */
sealed interface AlertSound {
    data object ShortAdhan : AlertSound
    data object FullAdhan : AlertSound
    data object FajrFullAdhan : AlertSound

    /** A file the user chose; [reference] is opaque to the core (a content URI on Android). */
    data class CustomAdhan(val reference: String) : AlertSound

    /** Notification only. */
    data object Silent : AlertSound

    /** A Prayer Reminder's short notification sound (the reminder channel's own); never an adhan. */
    data object ReminderTone : AlertSound
}

/** How an alert's alarm is set: an exact alarm (the default) or an alarm clock shown on the lock screen. */
enum class DeliveryMode { EXACT, ALARM_CLOCK }

/** One alert the scheduler must raise. */
data class PlannedAlert(
    val instant: Instant,
    val prayer: Prayer,
    val kind: AlertKind,
    val sound: AlertSound,
    val deliveryMode: DeliveryMode,
    /** The Prayer Day the alert's Prayer belongs to; with [prayer] and [kind] it identifies the alert. */
    val prayerDay: LocalDate,
    /** Dhuhr on a Friday, announced as Jumu'ah. */
    val isJumuah: Boolean,
    /** The Prayer Time itself: [instant] for an Adhan Alert, later for a Prayer Reminder. */
    val prayerTime: Instant = instant,
    /**
     * [AlertSettings.playOnSilent] and [AlertSettings.vibration], carried with each alert because the receiver
     * may fire before the first unlock, when settings can't be read. Pass them to [AlertPlayback.decide].
     */
    val playOnSilent: Boolean = false,
    val vibration: Boolean = true,
)

/**
 * Every alert due within [AlertPlan.WINDOW] after now, in time order, plus the next local midnight at the
 * location (the scheduler sets an alarm there so the display day and widgets switch date).
 */
data class AlertPlan(
    val alerts: List<PlannedAlert>,
    val nextLocalMidnight: Instant,
) {
    companion object {
        /** The rolling window the plan covers; rebuilt on every alarm, so it never runs dry. */
        val WINDOW: java.time.Duration = java.time.Duration.ofHours(48)
    }
}
