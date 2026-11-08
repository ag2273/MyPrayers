package app.mawaqeet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.home.HomeScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import java.time.Clock

/** AppCompatActivity (not ComponentActivity) so the per-app language backport works on Android 8–12. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val clock = Clock.systemUTC()
        // Fixed default location until settings exist (#16, #21, #24).
        val core = PrayerCore(PrayerSettings.Default, clock)
        setContent {
            MawaqeetTheme {
                HomeScreen(core, clock)
            }
        }
    }
}
