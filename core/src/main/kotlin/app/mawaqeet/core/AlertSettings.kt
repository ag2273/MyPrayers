package app.mawaqeet.core

/**
 * The sound a Prayer's Adhan Alert plays, as the user picks it in Settings → Alerts. Stored by name.
 * [FULL] on Fajr is Fajr's own Full Adhan (with «الصلاة خير من النوم»).
 */
enum class AdhanSoundChoice { SHORT, FULL, SILENT }

/** One Prayer's alert settings. */
data class PrayerAlertSettings(
    /** Whether an Adhan Alert fires at the Prayer Time. */
    val adhanAlert: Boolean = true,
    val sound: AdhanSoundChoice = AdhanSoundChoice.SHORT,
    /** Whether a Prayer Reminder fires [reminderMinutes] before the Prayer Time. */
    val reminder: Boolean = false,
    /** Lead time of the Prayer Reminder, at least [MIN_REMINDER_MINUTES]. Kept while the reminder is off. */
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
) {
    init {
        require(reminderMinutes >= MIN_REMINDER_MINUTES) {
            "A Prayer Reminder needs at least $MIN_REMINDER_MINUTES minutes, not $reminderMinutes"
        }
    }

    companion object {
        const val MIN_REMINDER_MINUTES = 10
        const val DEFAULT_REMINDER_MINUTES = 15
    }
}

/** Everything in Settings → Alerts. */
data class AlertSettings(
    /** Per-Prayer settings; a Prayer missing from the map has the defaults. */
    val prayers: Map<Prayer, PrayerAlertSettings> = emptyMap(),
    /** Adhan Alerts are set with `setAlarmClock` (next Prayer on the lock screen) instead of exact alarms. */
    val alarmClockMode: Boolean = false,
    /** The adhan plays even when the ringer is silent or vibrate. */
    val playOnSilent: Boolean = false,
    val vibration: Boolean = true,
) {
    fun of(prayer: Prayer): PrayerAlertSettings = prayers[prayer] ?: PrayerAlertSettings()

    fun with(prayer: Prayer, settings: PrayerAlertSettings): AlertSettings = copy(prayers = prayers + (prayer to settings))

    override fun equals(other: Any?): Boolean = other is AlertSettings &&
        Prayer.entries.all { of(it) == other.of(it) } &&
        alarmClockMode == other.alarmClockMode && playOnSilent == other.playOnSilent && vibration == other.vibration

    override fun hashCode(): Int = listOf(Prayer.entries.map(::of), alarmClockMode, playOnSilent, vibration).hashCode()
}
