package app.mawaqeet

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import app.mawaqeet.alerts.AlertPermissions
import app.mawaqeet.alerts.AlertRescheduler
import app.mawaqeet.alerts.CustomAdhanFiles
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.CityDirectory
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.LocationChangeCheck
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.LocationUpdate
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.SavedLocation
import app.mawaqeet.data.LocationCheckResult
import app.mawaqeet.location.DeviceLocation
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.home.HomeScreen
import app.mawaqeet.ui.location.LocationScreen
import app.mawaqeet.ui.log.PrayerLogScreen
import app.mawaqeet.ui.location.locationName
import app.mawaqeet.ui.permissions.GrantScreen
import app.mawaqeet.ui.settings.SettingsActions
import app.mawaqeet.ui.settings.SettingsScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock

/** AppCompatActivity (not ComponentActivity) so the per-app language backport works on Android 8–12. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val clock = Clock.systemUTC()
        val app = application as MawaqeetApplication
        val prayerLog = app.prayerLog
        val settingsStore = app.settings
        lifecycleScope.launch(Dispatchers.IO) {
            PrayerCore(settingsStore.settings.first(), clock, prayerLog).recordFirstUse()
        }
        // AppCompat knows the per-app language here (the activity is recreated on every change, and on 13+ the system
        // may change it); widgets and receivers on 8–12 read this copy. A changed value refreshes the widgets.
        val languageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        app.appScope.launch {
            if (settingsStore.appLanguage.first() != languageTags) settingsStore.setAppLanguage(languageTags)
        }
        setContent {
            MawaqeetTheme {
                // Null until the stored settings are read, so the default method's times never flash.
                val settings by settingsStore.settings.collectAsState(initial = null)
                val storedDigits by settingsStore.digitStyle.collectAsState(initial = null)
                // Wrapped in a list so "not read yet" (null) differs from "no location set" (empty): no default-name flash.
                val storedLocation by remember { settingsStore.savedLocation.map { listOfNotNull(it) } }.collectAsState(initial = null)
                val current = settings ?: return@MawaqeetTheme
                val digits = storedDigits ?: return@MawaqeetTheme
                val savedLocation = (storedLocation ?: return@MawaqeetTheme).firstOrNull()
                // A new core whenever the settings change: Home re-queries it, so times and next Prayer follow.
                val core = remember(current) { PrayerCore(current, clock, prayerLog) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showLocation by rememberSaveable { mutableStateOf(false) }
                var showLog by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                // Re-checked on every resume, so a permission revoked in system settings brings the Grant screen back.
                var permissions by remember { mutableStateOf(AlertPermissions.of(this)) }
                LifecycleResumeEffect(Unit) {
                    permissions = AlertPermissions.of(this@MainActivity)
                    onPauseOrDispose { }
                }
                var askedNotifications by rememberSaveable { mutableStateOf(false) }
                val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    permissions = AlertPermissions.of(this@MainActivity)
                }
                // Travel check: on every resume, if approximate location was granted, read it (foreground only).
                var proposedMove by remember { mutableStateOf<SavedLocation?>(null) }
                // The short note after an automatic update, e.g. «تم تحديث الموقع إلى الإسكندرية», shown on Home.
                var updatedTo by remember { mutableStateOf<SavedLocation?>(null) }
                LaunchedEffect(updatedTo) {
                    if (updatedTo != null) {
                        delay(NOTE_MS)
                        updatedTo = null
                    }
                }
                LifecycleResumeEffect(Unit) {
                    val job = lifecycleScope.launch {
                        when (val result = checkTravel(app)) {
                            is LocationCheckResult.Updated -> updatedTo = result.saved
                            is LocationCheckResult.Ask -> proposedMove = result.proposed
                            else -> Unit
                        }
                    }
                    onPauseOrDispose { job.cancel() }
                }
                val locationMaybeStale by settingsStore.locationMaybeStale.collectAsState(initial = false)
                proposedMove?.let { proposed ->
                    MoveDialog(
                        proposed,
                        onUpdate = {
                            proposedMove = null
                            lifecycleScope.launch {
                                app.locationUpdater.change(proposed)
                                updatedTo = proposed
                            }
                        },
                        onKeep = {
                            proposedMove = null
                            app.declinedMove = proposed.location
                        },
                    )
                }
                LaunchedEffect(permissions.allGranted) {
                    if (permissions.allGranted) launch(Dispatchers.IO) { AlertRescheduler.reschedule(app) }
                }
                ProvideDisplayFormatter(digits) {
                if (!permissions.allGranted) {
                    GrantScreen(
                        permissions,
                        onGrantNotifications = {
                            // The system dialog once; after a refusal it may not show again, so the settings page.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotifications) {
                                askedNotifications = true
                                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                startActivity(AlertPermissions.notificationSettingsIntent(this@MainActivity))
                            }
                        },
                        onGrantExactAlarms = { startActivity(AlertPermissions.exactAlarmSettingsIntent(this@MainActivity)) },
                    )
                } else if (showSettings && showLocation) {
                    // The city list is read once per process, off the main thread, only when this screen opens.
                    val cities by produceState<CityDirectory?>(null) { value = withContext(Dispatchers.IO) { app.cities } }
                    LocationScreen(
                        cities = cities,
                        settings = current,
                        onChoose = { scope.launch { app.locationUpdater.change(it) } },
                        onAcceptSuggestion = {
                            scope.launch {
                                settingsStore.setCalculationMethod(it.method)
                                settingsStore.setAsrConvention(it.asrConvention)
                            }
                        },
                        onBack = { showLocation = false },
                        readDevicePosition = { DeviceLocation.current(this@MainActivity) },
                    )
                } else if (showSettings) {
                    val actions = remember(settingsStore) {
                        object : SettingsActions {
                            override fun setCalculationMethod(method: CalculationMethod) {
                                scope.launch { settingsStore.setCalculationMethod(method) }
                            }

                            override fun setAsrConvention(convention: AsrConvention) {
                                scope.launch { settingsStore.setAsrConvention(convention) }
                            }

                            override fun setLanguage(languageTag: String) {
                                // Recreates the activity in the new language; showSettings is saved, so Settings stays open.
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
                            }

                            override fun setDigitStyle(style: DigitStyle) {
                                scope.launch { settingsStore.setDigitStyle(style) }
                            }

                            override fun setHijriOffsetDays(days: Int) {
                                scope.launch { settingsStore.setHijriOffsetDays(days) }
                            }

                            override fun openLocation() {
                                showLocation = true
                            }

                            override fun updatePrayerAlert(prayer: app.mawaqeet.core.Prayer, change: (app.mawaqeet.core.PrayerAlertSettings) -> app.mawaqeet.core.PrayerAlertSettings) {
                                scope.launch { settingsStore.updatePrayerAlert(prayer, change) }
                            }

                            override fun setAlarmClockMode(on: Boolean) {
                                scope.launch { settingsStore.setAlarmClockMode(on) }
                            }

                            override fun setPlayOnSilent(on: Boolean) {
                                scope.launch { settingsStore.setPlayOnSilent(on) }
                            }

                            override fun setVibration(on: Boolean) {
                                scope.launch { settingsStore.setVibration(on) }
                            }

                            override fun deletePrayerLog() {
                                // A core from the stored settings: these actions outlive the composition's core.
                                scope.launch(Dispatchers.IO) {
                                    PrayerCore(settingsStore.settings.first(), clock, prayerLog).deletePrayerLog()
                                }
                            }

                            override fun setCustomAdhan(recording: AdhanRecording, uri: Uri?) {
                                // Keep read access before storing, so the alert can open the file after a reboot.
                                if (uri != null) CustomAdhanFiles.keep(this@MainActivity, uri)
                                scope.launch {
                                    val unused = settingsStore.setCustomAdhan(recording, uri?.toString())
                                    CustomAdhanFiles.release(this@MainActivity, unused)
                                }
                            }
                        }
                    }
                    SettingsScreen(
                        current,
                        locationName(savedLocation),
                        digits,
                        core.displayDay().date,
                        actions,
                        onBack = { showSettings = false },
                    )
                } else if (showLog) {
                    PrayerLogScreen(core, prayerLog.changes, onBack = { showLog = false })
                } else {
                    HomeScreen(
                        core, clock, prayerLog.changes, locationName(savedLocation),
                        onOpenSettings = { showSettings = true },
                        onOpenLog = { showLog = true },
                        locationHint = when {
                            updatedTo != null -> stringResource(
                                R.string.location_updated,
                                updatedTo?.city?.displayName(LocalConfiguration.current.locales[0].language == "ar").orEmpty(),
                            )
                            locationMaybeStale -> stringResource(R.string.location_maybe_stale)
                            else -> null
                        },
                        onLocationHint = { showSettings = true; showLocation = true },
                    )
                }
                }
            }
        }
    }

    /**
     * Reads the approximate position (if granted) and applies the travel rule: an automatic location is updated
     * (Updated: show the note); a manual one is only proposed (Ask), unless the user already declined that move.
     */
    private suspend fun checkTravel(app: MawaqeetApplication): LocationCheckResult? {
        val position = DeviceLocation.current(this) ?: return null
        val declined = app.declinedMove
        if (declined != null && LocationChangeCheck.check(
                SavedLocation(declined, LocationSource.MANUAL, null), position.latitude, position.longitude,
            ) == LocationUpdate.NOTHING
        ) {
            return null
        }
        return app.locationUpdater.onDevicePosition(position.latitude, position.longitude)
    }

    private companion object {
        const val NOTE_MS = 6_000L
    }

}

/** Manual location, but the device is more than 10 km away: ask before changing it (yes makes it automatic). */
@Composable
private fun MoveDialog(proposed: SavedLocation, onUpdate: () -> Unit, onKeep: () -> Unit) {
    val name = proposed.city?.displayName(LocalConfiguration.current.locales[0].language == "ar").orEmpty()
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.location_move_title)) },
        text = { Text(stringResource(R.string.location_move_question, name)) },
        confirmButton = { TextButton(onClick = onUpdate) { Text(stringResource(R.string.location_move_update)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.location_move_keep)) } },
    )
}
