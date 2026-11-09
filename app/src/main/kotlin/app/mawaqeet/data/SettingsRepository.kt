package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.DigitStyle
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
                hijriOffsetDays = prefs[HIJRI_OFFSET]?.takeIf { it in PrayerSettings.HIJRI_OFFSET_RANGE }
                    ?: default.hijriOffsetDays,
            )
        }
        .distinctUntilChanged()

    /** App-only display preference (not core input): how digits look everywhere, for [app.mawaqeet.core.DisplayFormatter]. */
    val digitStyle: Flow<DigitStyle> = store.data
        .map { it[DIGITS].toEnum(DigitStyle.AUTOMATIC) }
        .distinctUntilChanged()

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
    }
}
