package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.Backup
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.CityRef
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.HighLatitudeRule
import app.mawaqeet.core.Location
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.core.PrayerSilence
import app.mawaqeet.core.AdhkarReminder
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.DuhaReminder
import app.mawaqeet.core.FastReminders
import app.mawaqeet.core.QiyamReminder
import app.mawaqeet.core.QiyamStyle
import app.mawaqeet.core.SunnahPrayerReminders
import app.mawaqeet.core.KahfReminder
import app.mawaqeet.core.SunnahFast
import app.mawaqeet.core.SalawatReminder
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.WorshipReminders
import app.mawaqeet.core.RamadanSettings
import app.mawaqeet.core.SuhoorReminder
import app.mawaqeet.core.SavedLocation
import app.mawaqeet.core.SettingValue
import app.mawaqeet.widget.WidgetAppearance
import app.mawaqeet.widget.WidgetColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

/**
 * The user's settings in a Preferences DataStore (file `datastore/settings.preferences_pb`), read as the core's
 * [PrayerSettings]. Enums are stored by name; a missing or unknown name reads as [PrayerSettings.Default]'s value.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    /** The current settings, emitted again on every change. Build a new PrayerCore from each value. */
    val settings: Flow<PrayerSettings> = store.data
        .map { it.prayerSettings() }
        .distinctUntilChanged()

    private fun Preferences.prayerSettings(): PrayerSettings {
        val default = PrayerSettings.Default
        return default.copy(
            location = savedLocation()?.location ?: default.location,
            method = this[METHOD].toEnum(default.method),
            asrConvention = this[ASR].toEnum(default.asrConvention),
            hijriOffsetDays = this[HIJRI_OFFSET]?.takeIf { it in PrayerSettings.HIJRI_OFFSET_RANGE }
                ?: default.hijriOffsetDays,
            alerts = alertSettings(),
            adjustments = Prayer.entries
                .mapNotNull { prayer ->
                    this[adjustmentKey(prayer)]?.takeIf { it != 0 && it in PrayerSettings.ADJUSTMENT_RANGE }?.let { prayer to it }
                }
                .toMap(),
            highLatitudeRule = this[HIGH_LATITUDE_RULE].toEnum(default.highLatitudeRule),
        )
    }

    /** The location the user set (city, coordinates or, from #24, automatic); null until they set one. */
    val savedLocation: Flow<SavedLocation?> = store.data
        .map { it.savedLocation() }
        .distinctUntilChanged()

    /** App-only display preference (not core input): how digits look everywhere, for [app.mawaqeet.core.DisplayFormatter]. */
    val digitStyle: Flow<DigitStyle> = store.data
        .map { it.digitStyle() }
        .distinctUntilChanged()

    private fun Preferences.digitStyle(): DigitStyle = this[DIGITS].toEnum(DigitStyle.AUTOMATIC)

    suspend fun setLocation(saved: SavedLocation) {
        store.edit {
            it[LATITUDE] = saved.location.latitude
            it[LONGITUDE] = saved.location.longitude
            it[ZONE] = saved.location.zone.id
            it[LOCATION_SOURCE] = saved.source.name
            val city = saved.city
            if (city != null) {
                it[CITY_ID] = city.id
                it[CITY_NAME] = city.name
                val arabicName = city.arabicName
                if (arabicName != null) it[CITY_NAME_AR] = arabicName else it.remove(CITY_NAME_AR)
                it[COUNTRY] = city.countryCode
                it[NEAR_CITY] = city.isNear
            } else {
                listOf(CITY_ID, CITY_NAME, CITY_NAME_AR, COUNTRY, NEAR_CITY).forEach { key -> it.remove(key) }
            }
        }
    }

    /** A stored location, or null if none is stored or its zone is unknown to this device. */
    private fun Preferences.savedLocation(): SavedLocation? {
        val latitude = this[LATITUDE] ?: return null
        val longitude = this[LONGITUDE] ?: return null
        val zone = try { ZoneId.of(this[ZONE] ?: return null) } catch (_: DateTimeException) { return null }
        return SavedLocation(
            Location(latitude, longitude, zone),
            this[LOCATION_SOURCE].toEnum(LocationSource.MANUAL),
            cityRef(),
        )
    }

    private fun Preferences.cityRef(): CityRef? {
        val id = this[CITY_ID] ?: return null
        val name = this[CITY_NAME] ?: return null
        val country = this[COUNTRY] ?: return null
        return CityRef(id, name, this[CITY_NAME_AR], country, this[NEAR_CITY] ?: false)
    }

    /** Forgets the stored location: Prayer Times fall back to [PrayerSettings.Default]'s. */
    suspend fun clearLocation() {
        store.edit { prefs -> LOCATION_KEYS.forEach { prefs.remove(it) } }
    }

    suspend fun setCalculationMethod(method: CalculationMethod) {
        store.edit { it[METHOD] = method.name }
    }

    suspend fun setAsrConvention(convention: AsrConvention) {
        store.edit { it[ASR] = convention.name }
    }

    suspend fun setHijriOffsetDays(days: Int) {
        require(days in PrayerSettings.HIJRI_OFFSET_RANGE)
        store.edit { it[HIJRI_OFFSET] = days }
    }

    /** Settings → Prayer Times: [minutes] (within [PrayerSettings.ADJUSTMENT_RANGE]) added to [prayer]'s time. */
    suspend fun setAdjustment(prayer: Prayer, minutes: Int) {
        require(minutes in PrayerSettings.ADJUSTMENT_RANGE)
        store.edit { if (minutes == 0) it.remove(adjustmentKey(prayer)) else it[adjustmentKey(prayer)] = minutes }
    }

    /**
     * Moves [prayer]'s adjustment by [step] minutes, kept within [PrayerSettings.ADJUSTMENT_RANGE]. It reads and writes
     * in one edit, so quick taps each count even before the screen shows the last one.
     */
    suspend fun stepAdjustment(prayer: Prayer, step: Int) {
        store.edit {
            val range = PrayerSettings.ADJUSTMENT_RANGE
            val minutes = ((it[adjustmentKey(prayer)] ?: 0) + step).coerceIn(range.first, range.last)
            if (minutes == 0) it.remove(adjustmentKey(prayer)) else it[adjustmentKey(prayer)] = minutes
        }
    }

    /** Settings → Advanced. */
    suspend fun setHighLatitudeRule(rule: HighLatitudeRule) {
        store.edit { it[HIGH_LATITUDE_RULE] = rule.name }
    }

    /**
     * True after a time-zone change broadcast (a cheap travel signal that cannot read location) until the next
     * location check or location change. Home shows a hint while it is set.
     */
    val locationMaybeStale: Flow<Boolean> = store.data.map { it[LOCATION_STALE] ?: false }.distinctUntilChanged()

    suspend fun setLocationMaybeStale(stale: Boolean) {
        store.edit { it[LOCATION_STALE] = stale }
    }

    /** False until the user finishes onboarding (#29); until then the app opens to it instead of Home. */
    val onboardingDone: Flow<Boolean> = store.data.map { it[ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { it[ONBOARDING_DONE] = done }
    }

    /** True once the user tapped "Done" on the Fix alerts tip or screen (#29): Home stops showing the tip. */
    val fixAlertsDone: Flow<Boolean> = store.data.map { it[FIX_ALERTS_DONE] ?: false }.distinctUntilChanged()

    suspend fun setFixAlertsDone(done: Boolean) {
        store.edit { it[FIX_ALERTS_DONE] = done }
    }

    suspend fun setDigitStyle(style: DigitStyle) {
        store.edit { it[DIGITS] = style.name }
    }

    /**
     * A copy of the per-app language (BCP 47 tags, "" = follow the system) for widgets and receivers: on Android 8–12
     * AppCompat only knows the per-app language in a process where an activity has run. [app.mawaqeet.MainActivity]
     * keeps it in sync; read it through [app.mawaqeet.ui.appLocale].
     */
    val appLanguage: Flow<String> = store.data.map { it[APP_LANGUAGE] ?: "" }.distinctUntilChanged()

    /** Settings → Widgets (#32): one appearance for every Widget Variant. Unknown or off-step values read as the default. */
    val widgetAppearance: Flow<WidgetAppearance> = store.data.map { it.widgetAppearance() }.distinctUntilChanged()

    private fun Preferences.widgetAppearance() = WidgetAppearance(
        transparency = this[WIDGET_TRANSPARENCY]
            ?.takeIf { it in WidgetAppearance.TRANSPARENCY_RANGE && it % WidgetAppearance.TRANSPARENCY_STEP == 0 } ?: 0,
        colors = this[WIDGET_COLORS]?.let { name -> WidgetColors.entries.firstOrNull { it.name == name } } ?: WidgetColors.APP,
        nextPrayerLook = this[WIDGET_NEXT_PRAYER_LOOK] ?: false,
    )

    suspend fun setWidgetTransparency(percent: Int) {
        WidgetAppearance(transparency = percent) // validates
        store.edit { it[WIDGET_TRANSPARENCY] = percent }
    }

    suspend fun setWidgetColors(colors: WidgetColors) {
        store.edit { it[WIDGET_COLORS] = colors.name }
    }

    /** Settings → Widgets: every widget on the Next Prayer's deep surface, or (off) each in its own look. */
    suspend fun setWidgetNextPrayerLook(on: Boolean) {
        store.edit { it[WIDGET_NEXT_PRAYER_LOOK] = on }
    }

    suspend fun setAppLanguage(languageTags: String) {
        store.edit { it[APP_LANGUAGE] = languageTags }
    }

    /** Settings → Alerts. Every change reaches the alarms through [settings] (the app reschedules on each value). */
    suspend fun setPrayerAlert(prayer: Prayer, alert: PrayerAlertSettings) = updatePrayerAlert(prayer) { alert }

    /**
     * Changes one Prayer's alert settings from their stored value, atomically, so quick successive changes
     * (sound, then reminder) never overwrite each other with a stale copy.
     */
    suspend fun updatePrayerAlert(prayer: Prayer, change: (PrayerAlertSettings) -> PrayerAlertSettings) {
        val keys = PrayerAlertKeys(prayer)
        store.edit {
            val alert = change(it.alertSettings().of(prayer))
            it[keys.adhanAlert] = alert.adhanAlert
            it[keys.sound] = alert.sound.name
            it[keys.reminder] = alert.reminder
            it[keys.reminderMinutes] = alert.reminderMinutes
        }
    }

    /** The master switch: off stops every Adhan Alert and Prayer Reminder (the plan then holds none). */
    suspend fun setAlertsEnabled(on: Boolean) {
        store.edit { it[ALERTS_ENABLED] = on }
    }

    /**
     * Settings → Alerts → «الصامت وقت الصلاة» (#61). Independent of the master switch. The screen shows it on only while
     * Do Not Disturb access is granted too; without access the Silence Windows are planned but change nothing.
     */
    suspend fun setPrayerSilenceEnabled(on: Boolean) {
        store.edit { it[PRAYER_SILENCE_ENABLED] = on }
    }

    /** «صامت» or «اهتزاز» (#68). */
    suspend fun setPrayerSilenceMode(mode: SilenceMode) {
        store.edit { it[PRAYER_SILENCE_MODE] = mode.name }
    }

    /** Turns Prayer Silence on or off for [prayer] alone (#68); every Prayer is on until turned off. */
    suspend fun setPrayerSilencePrayer(prayer: Prayer, on: Boolean) {
        store.edit {
            val off = it[PRAYER_SILENCE_OFF].orEmpty()
            it[PRAYER_SILENCE_OFF] = if (on) off - prayer.name else off + prayer.name
        }
    }

    /**
     * Moves the Silence Window's duration ([jumuah] true: Jumu'ah's) by [step] taps ([PrayerSilence.stepped], which
     * keeps it within its range). Reads and writes in one edit, so quick taps each count.
     */
    suspend fun stepPrayerSilenceMinutes(jumuah: Boolean, step: Int) {
        store.edit {
            val next = it.alertSettings().prayerSilence.stepped(jumuah, step)
            if (jumuah) it[PRAYER_SILENCE_JUMUAH_MINUTES] = next.jumuahMinutes else it[PRAYER_SILENCE_MINUTES] = next.minutes
        }
    }

    /** Settings → Alerts → «تذكيرات العبادة» → «رمضان» (#90): Imsak moved [step] taps of 5 minutes, within 0–30. */
    suspend fun stepImsakMinutes(step: Int) {
        store.edit { it[RAMADAN_IMSAK_MINUTES] = it.alertSettings().ramadan.stepped(step).imsakMinutes }
    }

    /** «تذكير السحور» under «رمضان» (#91); planned only on Fasting Days while the master switch is on. */
    suspend fun setSuhoorReminder(on: Boolean) {
        store.edit { it[RAMADAN_SUHOOR_ENABLED] = on }
    }

    /** The suhoor reminder's [minutes] before Fajr, one of [SuhoorReminder.MINUTES]; kept while it is off. */
    suspend fun setSuhoorReminderMinutes(minutes: Int) {
        require(minutes in SuhoorReminder.MINUTES)
        store.edit { it[RAMADAN_SUHOOR_MINUTES] = minutes }
    }

    /** «تنبيه الإمساك» under «رمضان» (#91); planned only on Fasting Days while the master switch is on. */
    suspend fun setImsakAlert(on: Boolean) {
        store.edit { it[RAMADAN_IMSAK_ALERT] = on }
    }

    /** Settings → Alerts → «تذكيرات العبادة» → «الصلاة على النبي ﷺ» (#63); planned only while the master switch is on. */
    suspend fun setSalawatReminder(on: Boolean) {
        store.edit { it[SALAWAT_ENABLED] = on }
    }

    /** Every [hours] hours, one of [SalawatReminder.INTERVAL_HOURS]; kept while salawat reminders are off. */
    suspend fun setSalawatInterval(hours: Int) {
        require(hours in SalawatReminder.INTERVAL_HOURS)
        store.edit { it[SALAWAT_INTERVAL_HOURS] = hours }
    }

    /** «أذكار الصباح» or «أذكار المساء» under «تذكيرات العبادة» (#75); [set] is MORNING or EVENING. */
    suspend fun setAdhkarReminder(set: AdhkarSetId, on: Boolean) {
        store.edit { it[adhkarReminderKeys(set).first] = on }
    }

    /** [minutes] after Fajr (morning) or Asr (evening), one of [AdhkarReminder.MINUTES]; kept while the reminder is off. */
    suspend fun setAdhkarReminderMinutes(set: AdhkarSetId, minutes: Int) {
        require(minutes in AdhkarReminder.MINUTES)
        store.edit { it[adhkarReminderKeys(set).second] = minutes }
    }

    /** Settings → Alerts → «تذكيرات العبادة» → «سورة الكهف» (#64); planned only while the master switch is on. */
    suspend fun setKahfReminder(on: Boolean) {
        store.edit { it[KAHF_ENABLED] = on }
    }

    /**
     * Settings → Alerts → «تذكيرات العبادة» → «صيام السنة» (#95): [fast]'s reminder the night before, under key
     * `worship_fast_<fast>` (e.g. `worship_fast_arafah`); planned only while the master switch is on.
     */
    suspend fun setFastReminder(fast: SunnahFast, on: Boolean) {
        store.edit { it[fastReminderKey(fast)] = on }
    }

    /** «صلاة الضحى» under «تذكيرات العبادة» → «السنن» (#96); planned only while the master switch is on. */
    suspend fun setDuhaReminder(on: Boolean) {
        store.edit { it[DUHA_ENABLED] = on }
    }

    /** [minutes] after Sunrise, one of [DuhaReminder.MINUTES]; kept while the Duha reminder is off. */
    suspend fun setDuhaReminderMinutes(minutes: Int) {
        require(minutes in DuhaReminder.MINUTES)
        store.edit { it[DUHA_MINUTES] = minutes }
    }

    /** «قيام الليل» under «تذكيرات العبادة» → «السنن» (#96); planned only while the master switch is on. */
    suspend fun setQiyamReminder(on: Boolean) {
        store.edit { it[QIYAM_ENABLED] = on }
    }

    /** The Qiyam reminder's [QiyamStyle] (#97), by name under `worship_qiyam_style`; kept while the reminder is off. */
    suspend fun setQiyamStyle(style: QiyamStyle) {
        store.edit { it[QIYAM_STYLE] = style.name }
    }

    suspend fun setAlarmClockMode(on: Boolean) {
        store.edit { it[ALARM_CLOCK_MODE] = on }
    }

    suspend fun setPlayOnSilent(on: Boolean) {
        store.edit { it[PLAY_ON_SILENT] = on }
    }

    suspend fun setVibration(on: Boolean) {
        store.edit { it[VIBRATION] = on }
    }

    /** Whether the adhan follows the alarm or the media volume; reaches the alarms through [settings] like the rest. */
    suspend fun setAdhanVolume(volume: AdhanVolume) {
        store.edit { it[ADHAN_VOLUME] = volume.name }
    }

    /**
     * Chooses [reference] (a content URI the app holds a persistable read permission to) as the Custom Adhan for
     * [recording] only, or clears it with null. Returns the references no recording uses any more, whose
     * permissions the caller should release.
     */
    suspend fun setCustomAdhan(recording: AdhanRecording, reference: String?): Set<String> {
        var unused = emptySet<String>()
        store.edit {
            val before = it.alertSettings().customAdhans
            if (reference == null) it.remove(customAdhanKey(recording)) else it[customAdhanKey(recording)] = reference
            unused = before.values.toSet() - it.alertSettings().customAdhans.values.toSet()
        }
        return unused
    }

    /** Settings → Backup: when a Backup File was last exported from this phone; null before the first. Never in the file. */
    val lastBackupExport: Flow<Instant?> = store.data.map { prefs -> prefs[LAST_BACKUP_EXPORT]?.let(Instant::ofEpochMilli) }
        .distinctUntilChanged()

    suspend fun setLastBackupExport(at: Instant) {
        store.edit { it[LAST_BACKUP_EXPORT] = at.toEpochMilli() }
    }

    /**
     * Every stored setting by its key, for the Backup File (#86): the location and Calculation Method, alerts,
     * reminders, display and widgets. Keys about this phone only (and every Custom Adhan file) stay out
     * ([Backup.restorable]). [restoreSettings] writes each value back with its key's own type.
     */
    suspend fun backupSettings(): Map<String, SettingValue> = store.data.first().asMap()
        .filterKeys { key -> Backup.restorable(key.name) }
        .mapNotNull { (key, value) ->
            val setting = when (value) {
                is Boolean -> SettingValue.Flag(value)
                is Int -> SettingValue.Whole(value.toLong())
                is Long -> SettingValue.Whole(value)
                is Double -> SettingValue.Decimal(value)
                is Float -> SettingValue.Decimal(value.toDouble())
                is String -> SettingValue.Text(value)
                is Set<*> -> SettingValue.TextSet(value.filterIsInstance<String>().toSet())
                else -> null
            }
            setting?.let { key.name to it }
        }
        .toMap()

    /**
     * Whether [restored] (a restore's settings) can be stored as they are: every value of the kind its setting
     * reads. Check it before writing anything else of a restore, so a bad file changes nothing.
     */
    fun canRestore(restored: Map<String, SettingValue>): Boolean = try {
        mutablePreferencesOf().replaceWith(restored)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    /**
     * A restore (#99): [restored] replaces every setting at once, Custom Adhan files included (the bundled
     * recordings play again); only the keys about this phone (the last export date, onboarding, Fix Alerts) stay.
     * Returns the Custom Adhan references no recording uses any more, whose permissions the caller should release.
     * Throws [IllegalArgumentException], storing nothing, when [canRestore] is false.
     */
    suspend fun restoreSettings(restored: Map<String, SettingValue>): Set<String> {
        var released = emptySet<String>()
        store.edit { prefs ->
            released = prefs.alertSettings().customAdhans.values.toSet()
            prefs.replaceWith(restored)
        }
        return released
    }

    /** Replaces every setting but this phone's own with [restored]; throws [IllegalArgumentException] on a clash. */
    private fun MutablePreferences.replaceWith(restored: Map<String, SettingValue>) {
        asMap().keys.filter { Backup.restorable(it.name) || it.name.startsWith(CUSTOM_ADHAN_PREFIX) }.forEach { remove(it) }
        for ((name, value) in restored.filterKeys(Backup::restorable)) {
            when (value) {
                is SettingValue.Flag -> this[booleanPreferencesKey(name)] = value.value
                is SettingValue.Whole -> if (name in LONG_KEYS) {
                    this[longPreferencesKey(name)] = value.value
                } else {
                    require(value.value in Int.MIN_VALUE..Int.MAX_VALUE) { "$name is out of range" }
                    this[intPreferencesKey(name)] = value.value.toInt()
                }
                is SettingValue.Decimal -> this[doublePreferencesKey(name)] = value.value
                is SettingValue.Text -> this[stringPreferencesKey(name)] = value.value
                is SettingValue.TextSet -> this[stringSetPreferencesKey(name)] = value.value
            }
        }
        // Read everything as the app will: a value of the wrong kind (a flag where a name belongs) fails here.
        try {
            prayerSettings()
            savedLocation()
            digitStyle()
            widgetAppearance()
            this[APP_LANGUAGE]?.length
        } catch (e: ClassCastException) {
            throw IllegalArgumentException("a restored setting has the wrong kind", e)
        }
    }

    private fun Preferences.alertSettings(): AlertSettings {
        val default = AlertSettings()
        val defaultPrayer = PrayerAlertSettings()
        return AlertSettings(
            enabled = this[ALERTS_ENABLED] ?: default.enabled,
            prayers = Prayer.entries.associateWith { prayer ->
                val keys = PrayerAlertKeys(prayer)
                PrayerAlertSettings(
                    adhanAlert = this[keys.adhanAlert] ?: defaultPrayer.adhanAlert,
                    sound = this[keys.sound].toEnum(defaultPrayer.sound),
                    reminder = this[keys.reminder] ?: defaultPrayer.reminder,
                    reminderMinutes = this[keys.reminderMinutes]?.takeIf { it >= PrayerAlertSettings.MIN_REMINDER_MINUTES }
                        ?: defaultPrayer.reminderMinutes,
                )
            },
            alarmClockMode = this[ALARM_CLOCK_MODE] ?: default.alarmClockMode,
            playOnSilent = this[PLAY_ON_SILENT] ?: default.playOnSilent,
            vibration = this[VIBRATION] ?: default.vibration,
            adhanVolume = this[ADHAN_VOLUME].toEnum(default.adhanVolume),
            customAdhans = AdhanRecording.entries
                .mapNotNull { recording -> this[customAdhanKey(recording)]?.takeIf { it.isNotEmpty() }?.let { recording to it } }
                .toMap(),
            ramadan = RamadanSettings(
                imsakMinutes = this[RAMADAN_IMSAK_MINUTES]?.takeIf { it in RamadanSettings.IMSAK_MINUTES_RANGE }
                    ?: default.ramadan.imsakMinutes,
                suhoorReminder = SuhoorReminder(
                    enabled = this[RAMADAN_SUHOOR_ENABLED] ?: default.ramadan.suhoorReminder.enabled,
                    minutes = this[RAMADAN_SUHOOR_MINUTES]?.takeIf { it in SuhoorReminder.MINUTES }
                        ?: default.ramadan.suhoorReminder.minutes,
                ),
                imsakAlert = this[RAMADAN_IMSAK_ALERT] ?: default.ramadan.imsakAlert,
            ),
            prayerSilence = PrayerSilence(
                enabled = this[PRAYER_SILENCE_ENABLED] ?: default.prayerSilence.enabled,
                mode = this[PRAYER_SILENCE_MODE].toEnum(default.prayerSilence.mode),
                prayers = this[PRAYER_SILENCE_OFF].orEmpty().let { off -> Prayer.entries.filterNot { it.name in off }.toSet() },
                minutes = this[PRAYER_SILENCE_MINUTES]?.takeIf { it in PrayerSilence.MINUTES_RANGE }
                    ?: default.prayerSilence.minutes,
                jumuahMinutes = this[PRAYER_SILENCE_JUMUAH_MINUTES]?.takeIf { it in PrayerSilence.JUMUAH_MINUTES_RANGE }
                    ?: default.prayerSilence.jumuahMinutes,
            ),
            worshipReminders = WorshipReminders(
                salawat = SalawatReminder(
                    enabled = this[SALAWAT_ENABLED] ?: default.worshipReminders.salawat.enabled,
                    intervalHours = this[SALAWAT_INTERVAL_HOURS]?.takeIf { it in SalawatReminder.INTERVAL_HOURS }
                        ?: default.worshipReminders.salawat.intervalHours,
                ),
                morningAdhkar = adhkarReminder(AdhkarSetId.MORNING, default.worshipReminders.morningAdhkar),
                eveningAdhkar = adhkarReminder(AdhkarSetId.EVENING, default.worshipReminders.eveningAdhkar),
                kahf = KahfReminder(enabled = this[KAHF_ENABLED] ?: default.worshipReminders.kahf.enabled),
                fasts = FastReminders(
                    SunnahFast.entries.filterTo(mutableSetOf()) { this[fastReminderKey(it)] ?: default.worshipReminders.fasts.isOn(it) },
                ),
                sunnahPrayers = default.worshipReminders.sunnahPrayers.let { sunnah ->
                    SunnahPrayerReminders(
                        duha = DuhaReminder(
                            enabled = this[DUHA_ENABLED] ?: sunnah.duha.enabled,
                            minutes = this[DUHA_MINUTES]?.takeIf { it in DuhaReminder.MINUTES } ?: sunnah.duha.minutes,
                        ),
                        qiyam = QiyamReminder(
                            enabled = this[QIYAM_ENABLED] ?: sunnah.qiyam.enabled,
                            style = this[QIYAM_STYLE].toEnum(sunnah.qiyam.style),
                        ),
                    )
                },
            ),
        )
    }

    /** Keys `adjust_<prayer>`, e.g. `adjust_fajr`: minutes added to that Prayer Time. */
    private fun adjustmentKey(prayer: Prayer) = intPreferencesKey("adjust_${prayer.name.lowercase()}")

    /** Keys `custom_adhan_short|full|fajr_full`: the Custom Adhan's content URI. */
    private fun customAdhanKey(recording: AdhanRecording) = stringPreferencesKey(CUSTOM_ADHAN_PREFIX + recording.name.lowercase())

    /** Keys `alert_<prayer>_adhan|sound|reminder|reminder_minutes`, e.g. `alert_fajr_sound`. */
    private class PrayerAlertKeys(prayer: Prayer) {
        private val prefix = "alert_${prayer.name.lowercase()}_"
        val adhanAlert = booleanPreferencesKey(prefix + "adhan")
        val sound = stringPreferencesKey(prefix + "sound")
        val reminder = booleanPreferencesKey(prefix + "reminder")
        val reminderMinutes = intPreferencesKey(prefix + "reminder_minutes")
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    companion object {
        /** The DataStore file name, for `preferencesDataStore(name = ...)` and the backup rules (#25). */
        const val FILE_NAME = "settings"

        /** Custom Adhan files are content URIs on this phone: never in a Backup File ([Backup.restorable]). */
        private const val CUSTOM_ADHAN_PREFIX = Backup.CUSTOM_ADHAN_PREFIX
        private val LAST_BACKUP_EXPORT = longPreferencesKey("backup_last_export")

        /** The only settings stored as a Long; every other [SettingValue.Whole] is an Int. */
        private val LONG_KEYS = setOf("location_city_id", "backup_last_export")

        private val METHOD = stringPreferencesKey("calculation_method")
        private val ASR = stringPreferencesKey("asr_convention")
        private val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
        private val HIGH_LATITUDE_RULE = stringPreferencesKey("high_latitude_rule")
        private val DIGITS = stringPreferencesKey("digit_style")
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val WIDGET_TRANSPARENCY = intPreferencesKey("widget_transparency")
        private val WIDGET_COLORS = stringPreferencesKey("widget_colors")
        private val WIDGET_NEXT_PRAYER_LOOK = booleanPreferencesKey("widget_next_prayer_look")
        private val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        private val ALARM_CLOCK_MODE = booleanPreferencesKey("alarm_clock_mode")
        private val PLAY_ON_SILENT = booleanPreferencesKey("play_on_silent")
        private val VIBRATION = booleanPreferencesKey("vibration")
        private val ADHAN_VOLUME = stringPreferencesKey("adhan_volume")
        private val PRAYER_SILENCE_ENABLED = booleanPreferencesKey("prayer_silence_enabled")

        /** [SilenceMode] name. */
        private val PRAYER_SILENCE_MODE = stringPreferencesKey("prayer_silence_mode")

        /** Names of the Prayers turned off for Prayer Silence; a Prayer not listed is on. */
        private val PRAYER_SILENCE_OFF = stringSetPreferencesKey("prayer_silence_off")
        private val PRAYER_SILENCE_MINUTES = intPreferencesKey("prayer_silence_minutes")
        private val PRAYER_SILENCE_JUMUAH_MINUTES = intPreferencesKey("prayer_silence_jumuah_minutes")

        /** Worship Reminders (#63): «الصلاة على النبي ﷺ» on or off, and its interval in hours. */
        private val SALAWAT_ENABLED = booleanPreferencesKey("worship_salawat_enabled")
        private val SALAWAT_INTERVAL_HOURS = intPreferencesKey("worship_salawat_interval_hours")

        /** Worship Reminders (#75): the morning and evening adhkar reminders, on or off, and their minutes after the Prayer. */
        private fun adhkarReminderKeys(set: AdhkarSetId) = when (set) {
            AdhkarSetId.MORNING -> MORNING_ADHKAR_ENABLED to MORNING_ADHKAR_MINUTES
            AdhkarSetId.EVENING -> EVENING_ADHKAR_ENABLED to EVENING_ADHKAR_MINUTES
            AdhkarSetId.AFTER_PRAYER -> throw IllegalArgumentException("After-prayer adhkar have no reminder")
        }

        private fun fastReminderKey(fast: SunnahFast) = booleanPreferencesKey("worship_fast_${fast.name.lowercase()}")

        private fun Preferences.adhkarReminder(set: AdhkarSetId, default: AdhkarReminder): AdhkarReminder {
            val (enabled, minutes) = adhkarReminderKeys(set)
            return AdhkarReminder(
                enabled = this[enabled] ?: default.enabled,
                minutes = this[minutes]?.takeIf { it in AdhkarReminder.MINUTES } ?: default.minutes,
            )
        }

        private val MORNING_ADHKAR_ENABLED = booleanPreferencesKey("worship_morning_adhkar_enabled")
        private val MORNING_ADHKAR_MINUTES = intPreferencesKey("worship_morning_adhkar_minutes")
        private val EVENING_ADHKAR_ENABLED = booleanPreferencesKey("worship_evening_adhkar_enabled")
        private val EVENING_ADHKAR_MINUTES = intPreferencesKey("worship_evening_adhkar_minutes")

        /** Worship Reminders (#64): «سورة الكهف» on or off. */
        private val KAHF_ENABLED = booleanPreferencesKey("worship_kahf_enabled")

        /** Sunnah Prayer Reminders (#96): «صلاة الضحى» on or off and its minutes after Sunrise, «قيام الليل» on or off. */
        private val DUHA_ENABLED = booleanPreferencesKey("worship_duha_enabled")
        private val DUHA_MINUTES = intPreferencesKey("worship_duha_minutes")
        private val QIYAM_ENABLED = booleanPreferencesKey("worship_qiyam_enabled")
        private val QIYAM_STYLE = stringPreferencesKey("worship_qiyam_style")
        private val RAMADAN_IMSAK_MINUTES = intPreferencesKey("ramadan_imsak_minutes")

        /** Ramadan reminders (#91): «تذكير السحور» on or off and its minutes before Fajr, «تنبيه الإمساك» on or off. */
        private val RAMADAN_SUHOOR_ENABLED = booleanPreferencesKey("ramadan_suhoor_enabled")
        private val RAMADAN_SUHOOR_MINUTES = intPreferencesKey("ramadan_suhoor_minutes")
        private val RAMADAN_IMSAK_ALERT = booleanPreferencesKey("ramadan_imsak_alert")
        private val LOCATION_STALE = booleanPreferencesKey("location_maybe_stale")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val FIX_ALERTS_DONE = booleanPreferencesKey("fix_alerts_done")
        private val LATITUDE = doublePreferencesKey("location_latitude")
        private val LONGITUDE = doublePreferencesKey("location_longitude")
        private val ZONE = stringPreferencesKey("location_zone")
        private val LOCATION_SOURCE = stringPreferencesKey("location_source")
        private val CITY_ID = longPreferencesKey("location_city_id")
        private val CITY_NAME = stringPreferencesKey("location_city_name")
        private val CITY_NAME_AR = stringPreferencesKey("location_city_name_ar")
        private val COUNTRY = stringPreferencesKey("location_country")
        private val NEAR_CITY = booleanPreferencesKey("location_near_city")
        private val LOCATION_KEYS = listOf(LATITUDE, LONGITUDE, ZONE, LOCATION_SOURCE, CITY_ID, CITY_NAME, CITY_NAME_AR, COUNTRY, NEAR_CITY)
    }
}
