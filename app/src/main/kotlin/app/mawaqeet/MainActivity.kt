package app.mawaqeet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.ui.home.HomeScreen
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
                val current = settings ?: return@MawaqeetTheme
                // A new core whenever the settings change: Home re-queries it, so times and next Prayer follow.
                val core = remember(current) { PrayerCore(current, clock, prayerLog) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                if (showSettings) {
                    val actions = remember(settingsStore) {
                        object : SettingsActions {
                            override fun setCalculationMethod(method: CalculationMethod) {
                                scope.launch { settingsStore.setCalculationMethod(method) }
                            }

                            override fun setAsrConvention(convention: AsrConvention) {
                                scope.launch { settingsStore.setAsrConvention(convention) }
                            }
                        }
                    }
                    SettingsScreen(current, actions, onBack = { showSettings = false })
                } else {
                    HomeScreen(core, clock, prayerLog.changes, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
