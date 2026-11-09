package app.mawaqeet

import android.app.Application
import android.content.Context
import androidx.core.os.UserManagerCompat
import androidx.datastore.preferences.preferencesDataStore
import app.mawaqeet.alerts.AlertRescheduler
import app.mawaqeet.core.CityDirectory
import app.mawaqeet.data.PrayerLogDatabase
import app.mawaqeet.data.RoomPrayerLogRepository
import app.mawaqeet.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** DataStore allows only one instance per file per process, so it is created once, here. */
private val Context.settingsDataStore by preferencesDataStore(name = SettingsRepository.FILE_NAME)

/** Holds the app-wide singletons that screens, widgets and alarm receivers share. */
class MawaqeetApplication : Application() {
    /** The one Prayer Log repository in the process, so its [RoomPrayerLogRepository.changes] reaches every observer. */
    val prayerLog: RoomPrayerLogRepository by lazy { RoomPrayerLogRepository(PrayerLogDatabase.open(this)) }

    /** The one settings store in the process; observe [SettingsRepository.settings] and rebuild the core on change. */
    val settings: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }

    /**
     * The bundled offline city list (asset `cities.tsv`, ~34k cities). Reading it takes a moment: first touch it
     * off the main thread (e.g. `withContext(Dispatchers.IO) { app.cities }`). One per process.
     */
    val cities: CityDirectory by lazy { assets.open(CITIES_ASSET).use(CityDirectory::read) }

    /** Lives as long as the process; for work that must outlast any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Every settings value (the first on process start, then each change) reschedules the alerts. Before the
        // first unlock DataStore is unreadable; RescheduleReceiver re-arms the device-protected plan copy instead
        // and BOOT_COMPLETED restarts the process' work after unlock.
        if (UserManagerCompat.isUserUnlocked(this)) {
            appScope.launch { settings.settings.collect { AlertRescheduler.reschedule(this@MawaqeetApplication, it) } }
        }
    }
}

/** The city list asset written by tools/cities/generate_cities.py. */
const val CITIES_ASSET = "cities.tsv"
