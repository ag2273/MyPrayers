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

    /**
     * A file the user chose in place of the bundled [replaces] recording; [reference] is opaque to the core (a
     * content URI on Android). If the file can't be read when the alert fires, [fallback] plays instead.
     */
    data class CustomAdhan(val reference: String, val replaces: AdhanRecording) : AlertSound {
        val fallback: AlertSound get() = bundled(replaces)
    }

    /** Notification only. */
    data object Silent : AlertSound

    /** A Prayer Reminder's short notification sound (the reminder channel's own); never an adhan. */
    data object ReminderTone : AlertSound

    companion object {
        /** The bundled sound of [recording]. */
        fun bundled(recording: AdhanRecording): AlertSound = when (recording) {
            AdhanRecording.SHORT -> ShortAdhan
            AdhanRecording.FULL -> FullAdhan
            AdhanRecording.FAJR_FULL -> FajrFullAdhan
        }

        /** What [recording] plays under [settings]: its Custom Adhan if one is chosen, else the bundled one. */
        fun of(recording: AdhanRecording, settings: AlertSettings): AlertSound =
            settings.customAdhan(recording)?.let { CustomAdhan(it, recording) } ?: bundled(recording)
    }
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
    /** [AlertSettings.adhanVolume], carried for the same reason: the stream the adhan player uses. */
    val adhanVolume: AdhanVolume = AdhanVolume.ALARM,
    /** Maghrib's Adhan Alert on a Fasting Day (#91), announced as «حان وقت الإفطار». Never on a Prayer Reminder. */
    val isIftar: Boolean = false,
)

/**
 * Every alert due within [AlertPlan.WINDOW] after now, in time order, plus the next local midnight at the
 * location (the scheduler sets an alarm there so the display day and widgets switch date), the Silence Windows and
 * the Worship Reminders.
 */
data class AlertPlan(
    val alerts: List<PlannedAlert>,
    val nextLocalMidnight: Instant,
    /**
     * The next moment the display day changes with no Adhan Alert to announce it: a Prayer Time whose Adhan Alert
     * is turned off, or [nextLocalMidnight], whichever comes first. The scheduler's display alarm goes here, so
     * widgets move on at every Prayer Time and at midnight without polling.
     */
    val nextDisplayChange: Instant = nextLocalMidnight,
    /**
     * Prayer Silence: every Silence Window that has not ended and starts within [WINDOW], in time order (one in progress
     * is kept, so the alarm that restores the sound stays set). Empty while Prayer Silence is off.
     */
    val silenceWindows: List<SilenceWindow> = emptyList(),
    /** Every Worship Reminder due after now and within [WINDOW], in time order. Empty with the master switch off. */
    val worshipReminders: List<PlannedWorshipReminder> = emptyList(),
) {
    companion object {
        /** The rolling window the plan covers; rebuilt on every alarm, so it never runs dry. */
        val WINDOW: java.time.Duration = java.time.Duration.ofHours(48)
    }
}
