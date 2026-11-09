package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.CityRef
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.Location
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.core.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DateTimeException
import java.time.ZoneId

/**
 * The user's settings in a Preferences DataStore (file `datastore/settings.preferences_pb`), read as the core's
 * [PrayerSettings]. Enums are stored by name; a missing or unknown name reads as [PrayerSettings.Default]'s value.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    /** The current settings, emitted again on every change. Build a new PrayerCore from each value. */
    val settings: Flow<PrayerSettings> = store.data
        .map { prefs ->
            val default = PrayerSettings.Default
            default.copy(
                location = prefs.savedLocation()?.location ?: default.location,
                method = prefs[METHOD].toEnum(default.method),
                asrConvention = prefs[ASR].toEnum(default.asrConvention),
                hijriOffsetDays = prefs[HIJRI_OFFSET]?.takeIf { it in PrayerSettings.HIJRI_OFFSET_RANGE }
                    ?: default.hijriOffsetDays,
                alerts = prefs.alertSettings(),
            )
        }
        .distinctUntilChanged()

    /** The location the user set (city, coordinates or, from #24, automatic); null until they set one. */
    val savedLocation: Flow<SavedLocation?> = store.data
        .map { it.savedLocation() }
        .distinctUntilChanged()

    /** App-only display preference (not core input): how digits look everywhere, for [app.mawaqeet.core.DisplayFormatter]. */
    val digitStyle: Flow<DigitStyle> = store.data
        .map { it[DIGITS].toEnum(DigitStyle.AUTOMATIC) }
        .distinctUntilChanged()

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

    suspend fun setAlarmClockMode(on: Boolean) {
        store.edit { it[ALARM_CLOCK_MODE] = on }
    }

    suspend fun setPlayOnSilent(on: Boolean) {
        store.edit { it[PLAY_ON_SILENT] = on }
    }

    suspend fun setVibration(on: Boolean) {
        store.edit { it[VIBRATION] = on }
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

    private fun Preferences.alertSettings(): AlertSettings {
        val default = AlertSettings()
        val defaultPrayer = PrayerAlertSettings()
        return AlertSettings(
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
            customAdhans = AdhanRecording.entries
                .mapNotNull { recording -> this[customAdhanKey(recording)]?.takeIf { it.isNotEmpty() }?.let { recording to it } }
                .toMap(),
        )
    }

    /** Keys `custom_adhan_short|full|fajr_full`: the Custom Adhan's content URI. */
    private fun customAdhanKey(recording: AdhanRecording) = stringPreferencesKey("custom_adhan_${recording.name.lowercase()}")

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

        private val METHOD = stringPreferencesKey("calculation_method")
        private val ASR = stringPreferencesKey("asr_convention")
        private val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
        private val DIGITS = stringPreferencesKey("digit_style")
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val ALARM_CLOCK_MODE = booleanPreferencesKey("alarm_clock_mode")
        private val PLAY_ON_SILENT = booleanPreferencesKey("play_on_silent")
        private val VIBRATION = booleanPreferencesKey("vibration")
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
