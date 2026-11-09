package app.mawaqeet

import android.app.Application
import android.content.Context
import androidx.core.os.UserManagerCompat
import androidx.datastore.preferences.preferencesDataStore
import app.mawaqeet.alerts.AlertRescheduler
import app.mawaqeet.core.CityDirectory
import app.mawaqeet.core.Location
import app.mawaqeet.data.LocationUpdater
import app.mawaqeet.data.PrayerLogDatabase
import app.mawaqeet.data.RoomPrayerLogRepository
import app.mawaqeet.data.SettingsRepository
import app.mawaqeet.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Clock

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

    /** Every location change goes through it, so past Prayer Days keep their place (see [LocationUpdater]). */
    val locationUpdater: LocationUpdater by lazy { LocationUpdater(settings, prayerLog, Clock.systemUTC()) { cities } }

    /** A move the user declined to apply to their manual location, so it is not asked again in this process. */
    @Volatile
    var declinedMove: Location? = null

    /** Lives as long as the process; for work that must outlast any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Every settings value (the first on process start, then each change) reschedules the alerts. Before the
        // first unlock DataStore is unreadable; RescheduleReceiver re-arms the device-protected plan copy instead
        // and BOOT_COMPLETED restarts the process' work after unlock.
        if (UserManagerCompat.isUserUnlocked(this)) {
            appScope.launch { settings.settings.collect { AlertRescheduler.reschedule(this@MawaqeetApplication, it) } }
            // Every later change of what widgets show (method, adjustments, location, digits, language, widget appearance)
            // re-renders them.
            appScope.launch {
                combine(settings.settings, settings.digitStyle, settings.appLanguage, settings.widgetAppearance) { s, d, l, w ->
                    listOf(s, d, l, w)
                }
                    .drop(1)
                    .collect { Widgets.refreshAll(this@MawaqeetApplication) }
            }
            // Every mark, unmark or delete (Home, the Prayer Log screen, "Mark as prayed") re-renders the Prayer Log
            // widgets, which only display the log.
            appScope.launch { prayerLog.changes.drop(1).collect { Widgets.refreshAll(this@MawaqeetApplication) } }
        }
    }
}

/** The city list asset written by tools/cities/generate_cities.py. */
const val CITIES_ASSET = "cities.tsv"
