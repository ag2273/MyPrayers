package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.PrayerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
                method = prefs[METHOD].toEnum(default.method),
                asrConvention = prefs[ASR].toEnum(default.asrConvention),
            )
        }
        .distinctUntilChanged()

    suspend fun setCalculationMethod(method: CalculationMethod) {
        store.edit { it[METHOD] = method.name }
    }

    suspend fun setAsrConvention(convention: AsrConvention) {
        store.edit { it[ASR] = convention.name }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    companion object {
        /** The DataStore file name, for `preferencesDataStore(name = ...)` and the backup rules (#25). */
        const val FILE_NAME = "settings"

        private val METHOD = stringPreferencesKey("calculation_method")
        private val ASR = stringPreferencesKey("asr_convention")
    }
}
