package app.mawaqeet

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.mawaqeet.data.PrayerLogDatabase
import app.mawaqeet.data.RoomPrayerLogRepository
import app.mawaqeet.data.SettingsRepository

/** DataStore allows only one instance per file per process, so it is created once, here. */
private val Context.settingsDataStore by preferencesDataStore(name = SettingsRepository.FILE_NAME)

/** Holds the app-wide singletons that screens, widgets and alarm receivers share. */
class MawaqeetApplication : Application() {
    /** The one Prayer Log repository in the process, so its [RoomPrayerLogRepository.changes] reaches every observer. */
    val prayerLog: RoomPrayerLogRepository by lazy { RoomPrayerLogRepository(PrayerLogDatabase.open(this)) }

    /** The one settings store in the process; observe [SettingsRepository.settings] and rebuild the core on change. */
    val settings: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }
}
