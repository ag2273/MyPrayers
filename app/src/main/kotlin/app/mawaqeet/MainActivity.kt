package app.mawaqeet

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import app.mawaqeet.alerts.AlertPermissions
import app.mawaqeet.alerts.AlertRescheduler
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.home.HomeScreen
import app.mawaqeet.ui.permissions.GrantScreen
import app.mawaqeet.ui.settings.SettingsActions
import app.mawaqeet.ui.settings.SettingsScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        setContent {
            MawaqeetTheme {
                // Null until the stored settings are read, so the default method's times never flash.
                val settings by settingsStore.settings.collectAsState(initial = null)
                val storedDigits by settingsStore.digitStyle.collectAsState(initial = null)
                val current = settings ?: return@MawaqeetTheme
                val digits = storedDigits ?: return@MawaqeetTheme
                // A new core whenever the settings change: Home re-queries it, so times and next Prayer follow.
                val core = remember(current) { PrayerCore(current, clock, prayerLog) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
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
                        }
                    }
                    SettingsScreen(current, digits, core.displayDay().date, actions, onBack = { showSettings = false })
                } else {
                    HomeScreen(core, clock, prayerLog.changes, onOpenSettings = { showSettings = true })
                }
                }
            }
        }
    }
}
