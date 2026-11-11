package app.mawaqeet.core

/**
 * The sound a Prayer's Adhan Alert plays, as the user picks it in Settings → Alerts. Stored by name.
 * [FULL] on Fajr is Fajr's own Full Adhan (with «الصلاة خير من النوم»).
 */
enum class AdhanSoundChoice { SHORT, FULL, SILENT }

/**
 * The adhan recordings an Adhan Alert can play. Each can be replaced by its own Custom Adhan, independently: a
 * Custom Adhan for [FULL] never changes Fajr, which keeps [FAJR_FULL] (its own adhan, with «الصلاة خير من النوم»)
 * until the user chooses a Custom Adhan for Fajr itself. Stored by name.
 */
enum class AdhanRecording {
    SHORT,
    FULL,
    FAJR_FULL,
    ;

    companion object {
        /** The recording [choice] plays for [prayer]; null for [AdhanSoundChoice.SILENT]. */
        fun of(prayer: Prayer, choice: AdhanSoundChoice): AdhanRecording? = when (choice) {
            AdhanSoundChoice.SHORT -> SHORT
            AdhanSoundChoice.FULL -> if (prayer == Prayer.FAJR) FAJR_FULL else FULL
            AdhanSoundChoice.SILENT -> null
        }
    }
}

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

/**
 * The phone volume an adhan follows. [ALARM] plays on the alarm stream (the alarm volume, which the ringer never mutes);
 * [MEDIA] plays on the media stream, so the media volume sets how loud it is. Prayer Reminders keep their own sound.
 */
enum class AdhanVolume { ALARM, MEDIA }

/** Everything in Settings → Alerts. */
data class AlertSettings(
    /**
     * The master switch: false silences the whole app, so no Adhan Alert and no Prayer Reminder is planned for any
     * Prayer, whatever its own settings say (they are kept for when it is turned back on).
     */
    val enabled: Boolean = true,
    /** Per-Prayer settings; a Prayer missing from the map has the defaults. */
    val prayers: Map<Prayer, PrayerAlertSettings> = emptyMap(),
    /** Adhan Alerts are set with `setAlarmClock` (next Prayer on the lock screen) instead of exact alarms. */
    val alarmClockMode: Boolean = false,
    /** The adhan plays even when the ringer is silent or vibrate. */
    val playOnSilent: Boolean = false,
    val vibration: Boolean = true,
    /** Whose volume the adhan follows: the alarm volume (the default) or the media volume. */
    val adhanVolume: AdhanVolume = AdhanVolume.ALARM,
    /**
     * The Custom Adhan chosen for each recording, as an opaque reference (a content URI on Android) the core never
     * opens. A recording missing from the map plays the bundled one.
     */
    val customAdhans: Map<AdhanRecording, String> = emptyMap(),
    /** «الصامت وقت الصلاة»; planned whether or not [enabled] is on. */
    val prayerSilence: PrayerSilence = PrayerSilence(),
) {
    fun of(prayer: Prayer): PrayerAlertSettings = prayers[prayer] ?: PrayerAlertSettings()

    /**
     * What [prayer] actually gets, for Home and the widgets: its own settings, or no Adhan Alert and no Prayer
     * Reminder while the master switch is off.
     */
    fun effective(prayer: Prayer): PrayerAlertSettings =
        if (enabled) of(prayer) else of(prayer).copy(adhanAlert = false, reminder = false)

    fun with(prayer: Prayer, settings: PrayerAlertSettings): AlertSettings = copy(prayers = prayers + (prayer to settings))

    /** The Custom Adhan for [recording], or null when it plays the bundled recording. */
    fun customAdhan(recording: AdhanRecording): String? = customAdhans[recording]

    /** Chooses [reference] as the Custom Adhan for [recording] only; null clears it back to the bundled recording. */
    fun withCustomAdhan(recording: AdhanRecording, reference: String?): AlertSettings =
        copy(customAdhans = if (reference == null) customAdhans - recording else customAdhans + (recording to reference))

    override fun equals(other: Any?): Boolean = other is AlertSettings && enabled == other.enabled &&
        Prayer.entries.all { of(it) == other.of(it) } &&
        alarmClockMode == other.alarmClockMode && playOnSilent == other.playOnSilent && vibration == other.vibration &&
        adhanVolume == other.adhanVolume && customAdhans == other.customAdhans && prayerSilence == other.prayerSilence

    override fun hashCode(): Int =
        listOf(enabled, Prayer.entries.map(::of), alarmClockMode, playOnSilent, vibration, adhanVolume, customAdhans, prayerSilence).hashCode()
}
