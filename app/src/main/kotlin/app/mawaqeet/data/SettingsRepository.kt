package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.CityRef
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.Location
import app.mawaqeet.core.LocationSource
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

    suspend fun setDigitStyle(style: DigitStyle) {
        store.edit { it[DIGITS] = style.name }
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
        private val LOCATION_STALE = booleanPreferencesKey("location_maybe_stale")
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
