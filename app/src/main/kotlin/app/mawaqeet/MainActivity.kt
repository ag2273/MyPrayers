package app.mawaqeet

import android.Manifest
import android.content.Context
import android.content.Intent
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
import app.mawaqeet.alerts.AlertScheduler
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AlertSound
import app.mawaqeet.alerts.CustomAdhanFiles
import app.mawaqeet.alerts.PrayerSilencer
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
import app.mawaqeet.ui.components.MainScaffold
import app.mawaqeet.ui.components.Tab
import app.mawaqeet.ui.home.HomeScreen
import app.mawaqeet.ui.fixalerts.FixAlerts
import app.mawaqeet.ui.fixalerts.FixAlertsScreen
import app.mawaqeet.ui.location.LocationScreen
import app.mawaqeet.ui.onboarding.OnboardingActions
import app.mawaqeet.ui.onboarding.OnboardingScreen
import app.mawaqeet.ui.log.PrayerLogScreen
import app.mawaqeet.ui.adhkar.AdhkarScreen
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarCatalog
import app.mawaqeet.ui.location.locationName
import app.mawaqeet.ui.permissions.GrantScreen
import app.mawaqeet.ui.settings.SettingsActions
import app.mawaqeet.ui.settings.SettingsHub
import app.mawaqeet.ui.settings.SettingsPageScreen
import app.mawaqeet.ui.settings.SettingsRoute
import app.mawaqeet.widget.WidgetAppearance
import app.mawaqeet.widget.WidgetColors
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
    /** Which screen a widget tap asked for; [OpenRequest.id] makes the same request twice still count. */
    private data class OpenRequest(val opens: Opens, val id: Long = System.nanoTime())

    private var openRequest by mutableStateOf<OpenRequest?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest = Opens.from(intent)?.let(::OpenRequest)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity keeps its own screen; only a fresh launch follows the widget's request.
        if (savedInstanceState == null) openRequest = Opens.from(intent)?.let(::OpenRequest)
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
                val widgetAppearance by settingsStore.widgetAppearance.collectAsState(initial = WidgetAppearance())
                // Wrapped in a list so "not read yet" (null) differs from "no location set" (empty): no default-name flash.
                val storedLocation by remember { settingsStore.savedLocation.map { listOfNotNull(it) } }.collectAsState(initial = null)
                val onboarded by settingsStore.onboardingDone.collectAsState(initial = null)
                val fixAlertsDone by settingsStore.fixAlertsDone.collectAsState(initial = true)
                val current = settings ?: return@MawaqeetTheme
                val onboardingDone = onboarded ?: return@MawaqeetTheme
                val digits = storedDigits ?: return@MawaqeetTheme
                val savedLocation = (storedLocation ?: return@MawaqeetTheme).firstOrNull()
                // A new core whenever the settings change: Home re-queries it, so times and next Prayer follow.
                val core = remember(current) { PrayerCore(current, clock, prayerLog) }
                val adhkarCatalog by produceState<AdhkarCatalog?>(null) { value = withContext(Dispatchers.IO) { app.adhkarCatalog } }
                val adhkar = remember(core, adhkarCatalog) { adhkarCatalog?.let { Adhkar(it, core, app.adhkar) } }
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                // The open Settings page, over the tabs (no bottom bar); null shows the tabs.
                var settingsRoute by rememberSaveable { mutableStateOf<SettingsRoute?>(null) }
                var showLocation by rememberSaveable { mutableStateOf(false) }
                var showFixAlerts by rememberSaveable { mutableStateOf(false) }
                // A widget tap opens today's Prayer Times (Home) or the Prayer Log, from whatever screen was open.
                LaunchedEffect(openRequest) {
                    val request = openRequest ?: return@LaunchedEffect
                    showLocation = false
                    showFixAlerts = false
                    settingsRoute = null
                    tab = if (request.opens == Opens.PRAYER_LOG) Tab.LOG else Tab.TODAY
                    openRequest = null
                }
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
                val grantNotifications = {
                    // The system dialog once; after a refusal it may not show again, so the settings page.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotifications) {
                        askedNotifications = true
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startActivity(AlertPermissions.notificationSettingsIntent(this@MainActivity))
                    }
                }
                if (!onboardingDone) {
                    // A fresh install opens in Arabic (ADR 0002), whatever the system language; the first screen switches.
                    LaunchedEffect(Unit) {
                        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                        }
                    }
                }
                    val settingsActions = remember(settingsStore) {
                        object : SettingsActions {
                            override fun setCalculationMethod(method: CalculationMethod) {
                                scope.launch { settingsStore.setCalculationMethod(method) }
                            }

                            override fun setAsrConvention(convention: AsrConvention) {
                                scope.launch { settingsStore.setAsrConvention(convention) }
                            }

                            override fun setLanguage(languageTag: String) {
                                // Recreates the activity in the new language; the tab is saved, so Settings stays open.
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
                            }

                            override fun setWidgetTransparency(percent: Int) {
                                scope.launch { settingsStore.setWidgetTransparency(percent) }
                            }

                            override fun setWidgetColors(colors: WidgetColors) {
                                scope.launch { settingsStore.setWidgetColors(colors) }
                            }

                            override fun setWidgetNextPrayerLook(on: Boolean) {
                                scope.launch { settingsStore.setWidgetNextPrayerLook(on) }
                            }

                            override fun setDigitStyle(style: DigitStyle) {
                                scope.launch { settingsStore.setDigitStyle(style) }
                            }

                            override fun setHijriOffsetDays(days: Int) {
                                scope.launch { settingsStore.setHijriOffsetDays(days) }
                            }

                            override fun stepAdjustment(prayer: app.mawaqeet.core.Prayer, step: Int) {
                                scope.launch { settingsStore.stepAdjustment(prayer, step) }
                            }

                            override fun setHighLatitudeRule(rule: app.mawaqeet.core.HighLatitudeRule) {
                                scope.launch { settingsStore.setHighLatitudeRule(rule) }
                            }

                            override fun openLocation() {
                                showLocation = true
                            }

                            override fun openFixAlerts() {
                                showFixAlerts = true
                            }

                            override fun updatePrayerAlert(prayer: app.mawaqeet.core.Prayer, change: (app.mawaqeet.core.PrayerAlertSettings) -> app.mawaqeet.core.PrayerAlertSettings) {
                                scope.launch { settingsStore.updatePrayerAlert(prayer, change) }
                            }

                            override fun setAlertsEnabled(on: Boolean) {
                                scope.launch { settingsStore.setAlertsEnabled(on) }
                            }

                            override fun setPrayerSilenceEnabled(on: Boolean) {
                                scope.launch { settingsStore.setPrayerSilenceEnabled(on) }
                            }

                            override fun setPrayerSilenceMode(mode: app.mawaqeet.core.SilenceMode) {
                                scope.launch { settingsStore.setPrayerSilenceMode(mode) }
                            }

                            override fun setPrayerSilencePrayer(prayer: app.mawaqeet.core.Prayer, on: Boolean) {
                                scope.launch { settingsStore.setPrayerSilencePrayer(prayer, on) }
                            }

                            override fun stepPrayerSilenceMinutes(jumuah: Boolean, step: Int) {
                                scope.launch { settingsStore.stepPrayerSilenceMinutes(jumuah, step) }
                            }

                            override fun hasDoNotDisturbAccess(): Boolean = PrayerSilencer.hasAccess(this@MainActivity)

                            override fun openDoNotDisturbAccess() {
                                runCatching {
                                    startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                }
                            }

                            override fun setAlarmClockMode(on: Boolean) {
                                scope.launch { settingsStore.setAlarmClockMode(on) }
                            }

                            override fun tryAdhanAlert(): Boolean {
                                val alarms = getSystemService(android.app.AlarmManager::class.java)
                                if (!AlertScheduler.canScheduleExact(alarms)) return false
                                scope.launch(Dispatchers.IO) {
                                    // The settings as they are now (this object outlives a settings change).
                                    val now = settingsStore.settings.first()
                                    val next = PrayerCore(now, clock, prayerLog).displayDay().next
                                    AlertScheduler(this@MainActivity).scheduleTest(
                                        next.prayer,
                                        next.isJumuah,
                                        now.alerts.alarmClockMode,
                                        AdhanRecording.of(next.prayer, now.alerts.of(next.prayer).sound)
                                            ?.let { AlertSound.of(it, now.alerts) } ?: AlertSound.Silent,
                                        now.alerts.adhanVolume,
                                    )
                                }
                                return true
                            }

                            override fun canShowOverLockScreen(): Boolean =
                                android.os.Build.VERSION.SDK_INT < 34 ||
                                    getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

                            override fun openLockScreenPermission() {
                                if (android.os.Build.VERSION.SDK_INT >= 34) {
                                    runCatching {
                                        startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                                .setData(android.net.Uri.parse("package:$packageName")),
                                        )
                                    }
                                }
                            }

                            override fun setPlayOnSilent(on: Boolean) {
                                scope.launch { settingsStore.setPlayOnSilent(on) }
                            }

                            override fun setVibration(on: Boolean) {
                                scope.launch { settingsStore.setVibration(on) }
                            }

                            override fun setAdhanVolume(volume: app.mawaqeet.core.AdhanVolume) {
                                scope.launch { settingsStore.setAdhanVolume(volume) }
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
                ProvideDisplayFormatter(digits) {
                if (!onboardingDone) {
                    OnboardingScreen(
                        settings = current,
                        savedLocation = savedLocation,
                        loadCities = { app.cities },
                        permissions = permissions,
                        brand = FixAlerts.deviceBrand,
                        actions = object : OnboardingActions {
                            override fun setLanguage(languageTag: String) =
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))

                            override fun chooseLocation(saved: SavedLocation) {
                                scope.launch { app.locationUpdater.change(saved) }
                            }

                            override fun setCalculationMethod(method: CalculationMethod) {
                                scope.launch { settingsStore.setCalculationMethod(method) }
                            }

                            override fun setAsrConvention(convention: AsrConvention) {
                                scope.launch { settingsStore.setAsrConvention(convention) }
                            }

                            override fun grantNotifications() = grantNotifications()

                            override fun grantExactAlarms() =
                                startActivity(AlertPermissions.exactAlarmSettingsIntent(this@MainActivity))

                            override suspend fun readDevicePosition() = DeviceLocation.current(this@MainActivity)

                            override fun finish(batteryDone: Boolean) {
                                scope.launch {
                                    if (batteryDone) settingsStore.setFixAlertsDone(true)
                                    settingsStore.setOnboardingDone(true)
                                }
                            }
                        },
                    )
                } else if (!permissions.allGranted) {
                    GrantScreen(
                        permissions,
                        onGrantNotifications = grantNotifications,
                        onGrantExactAlarms = { startActivity(AlertPermissions.exactAlarmSettingsIntent(this@MainActivity)) },
                    )
                } else if (showFixAlerts) {
                    FixAlertsScreen(
                        onDone = {
                            showFixAlerts = false
                            scope.launch { settingsStore.setFixAlertsDone(true) }
                        },
                        onBack = { showFixAlerts = false },
                    )
                } else if (showLocation) {
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
                } else if (settingsRoute != null) {
                    SettingsPageScreen(
                        settingsRoute ?: return@ProvideDisplayFormatter,
                        current,
                        locationName(savedLocation),
                        digits,
                        core.displayDay().date,
                        settingsActions,
                        widgetAppearance,
                        onNavigate = { settingsRoute = it },
                    )
                } else {
                    MainScaffold(tab, onSelect = { tab = it }) {
                    when (tab) {
                    Tab.SETTINGS -> {
                    SettingsHub(
                        current,
                        locationName(savedLocation),
                        digits,
                        widgetAppearance,
                        alertsAtRisk = !fixAlertsDone && FixAlerts.deviceBrand != null,
                        onOpen = { settingsRoute = it },
                        onOpenFixAlerts = { showFixAlerts = true },
                    )
                    }
                    Tab.LOG -> PrayerLogScreen(core, prayerLog.changes)
                    Tab.ADHKAR -> AdhkarScreen(adhkar, app.adhkar.changes)
                    Tab.TODAY -> HomeScreen(
                        core, clock, prayerLog.changes, locationName(savedLocation),
                        alerts = current.alerts,
                        onOpenLocation = { showLocation = true },
                        locationHint = when {
                            updatedTo != null -> stringResource(
                                R.string.location_updated,
                                updatedTo?.city?.displayName(LocalConfiguration.current.locales[0].language == "ar").orEmpty(),
                            )
                            locationMaybeStale -> stringResource(R.string.location_maybe_stale)
                            else -> null
                        },
                        onLocationHint = { showLocation = true },
                        showFixAlertsTip = !fixAlertsDone && FixAlerts.deviceBrand != null,
                        onOpenFixAlerts = { showFixAlerts = true },
                        onFixAlertsDone = { scope.launch { settingsStore.setFixAlertsDone(true) } },
                    )
                    }
                    }
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

    /** The screen a widget opens. */
    enum class Opens {
        TODAY,
        PRAYER_LOG;

        companion object {
            fun from(intent: Intent?): Opens? = intent?.getStringExtra(EXTRA_OPENS)?.let { name -> entries.firstOrNull { it.name == name } }
        }
    }

    companion object {
        private const val NOTE_MS = 6_000L
        private const val EXTRA_OPENS = "app.mawaqeet.extra.OPENS"

        /**
         * What a widget tap starts: [opens] on top of the app's task, replacing whatever screen was showing (the
         * activity gets it in [onNewIntent] when it is already running).
         */
        fun openIntent(context: Context, opens: Opens): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPENS, opens.name)
            // Distinct data per screen, so the two widgets' PendingIntents never replace each other's extras.
            .setData(Uri.parse("mawaqeet://open/${opens.name.lowercase()}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
