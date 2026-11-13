package app.mawaqeet

import android.app.Application
import android.content.Context
import androidx.core.os.UserManagerCompat
import androidx.datastore.preferences.preferencesDataStore
import app.mawaqeet.alerts.AlertRescheduler
import app.mawaqeet.alerts.CustomAdhanFiles
import app.mawaqeet.alerts.PrayerSilencer
import app.mawaqeet.core.AdhkarCatalog
import app.mawaqeet.core.CityDirectory
import app.mawaqeet.core.Location
import app.mawaqeet.data.BackupFiles
import app.mawaqeet.data.LocationUpdater
import app.mawaqeet.data.PrayerLogDatabase
import app.mawaqeet.data.RoomAdhkarRepository
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
    val prayerLog: RoomPrayerLogRepository by lazy { RoomPrayerLogRepository(database) }

    /** The one Adhkar repository in the process (same database file as the Prayer Log, same backup rules). */
    val adhkar: RoomAdhkarRepository by lazy { RoomAdhkarRepository(database) }

    /** The bundled adhkar (asset `adhkar.tsv`). Small, but first touch it off the main thread. */
    val adhkarCatalog: AdhkarCatalog by lazy { assets.open(ADHKAR_ASSET).use(AdhkarCatalog::read) }

    private val database: PrayerLogDatabase by lazy { PrayerLogDatabase.open(this) }

    /** The one settings store in the process; observe [SettingsRepository.settings] and rebuild the core on change. */
    val settings: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }

    /**
     * The bundled offline city list (asset `cities.tsv`, ~34k cities). Reading it takes a moment: first touch it
     * off the main thread (e.g. `withContext(Dispatchers.IO) { app.cities }`). One per process.
     */
    val cities: CityDirectory by lazy { assets.open(CITIES_ASSET).use(CityDirectory::read) }

    /** Every location change goes through it, so past Prayer Days keep their place (see [LocationUpdater]). */
    val locationUpdater: LocationUpdater by lazy { LocationUpdater(settings, prayerLog, Clock.systemUTC()) { cities } }

    /**
     * Settings → Backup: exports and restores the Backup File. After a restore, the Custom Adhan files it let go
     * lose their permission, and the alerts and every widget follow the restored settings and records at once.
     */
    val backup: BackupFiles by lazy {
        BackupFiles(settings, prayerLog, adhkar, database, Clock.systemUTC()) { released ->
            CustomAdhanFiles.release(this, released)
            AlertRescheduler.reschedule(this)
            Widgets.refreshAll(this)
        }
    }

    /** A move the user declined to apply to their manual location, so it is not asked again in this process. */
    @Volatile
    var declinedMove: Location? = null

    /** Lives as long as the process; for work that must outlast any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // A user's own sound change during a Silence Window ends it (works before the first unlock too).
        PrayerSilencer.watchRinger(this)
        // Every settings value (the first on process start, then each change) reschedules the alerts. Before the
        // first unlock DataStore is unreadable; RescheduleReceiver re-arms the device-protected plan copy instead
        // and BOOT_COMPLETED restarts the process' work after unlock.
        if (UserManagerCompat.isUserUnlocked(this)) {
            // Digits and language too: they travel with the plan, for the receivers' notifications.
            appScope.launch {
                combine(settings.settings, settings.digitStyle, settings.appLanguage) { s, _, _ -> s }
                    .collect { AlertRescheduler.reschedule(this@MawaqeetApplication, it) }
            }
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

/** The Adhkar Sets' texts, counts and sources (format: core `AdhkarCatalog`). */
const val ADHKAR_ASSET = "adhkar.tsv"
