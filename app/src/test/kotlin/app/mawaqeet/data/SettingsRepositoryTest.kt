package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.CityRef
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.Location
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.SavedLocation
import app.mawaqeet.core.PrayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "settings.preferences_pb")

    /** Opens the settings file as a freshly started process would, then closes it again. */
    private fun <T> withRepository(block: suspend (SettingsRepository, DataStore<Preferences>) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            return runBlocking { block(SettingsRepository(store), store) }
        } finally {
            runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }
        }
    }

    @Test
    fun `before any choice the settings are the defaults`() {
        assertEquals(PrayerSettings.Default, withRepository { repo, _ -> repo.settings.first() })
    }

    @Test
    fun `a manual location survives an app restart and becomes the Prayer Times location`() {
        // Cairo's city centre (GeoNames 360630).
        val cairo = SavedLocation(Location(30.0626, 31.2497, ZoneId.of("Africa/Cairo")), LocationSource.MANUAL,
            CityRef(360630L, "Cairo", "القاهرة", "EG", isNear = false))
        withRepository { repo, _ ->
            assertEquals(null, repo.savedLocation.first())
            repo.setLocation(cairo)
        }

        withRepository { repo, _ ->
            assertEquals(cairo, repo.savedLocation.first())
            assertEquals(cairo.location, repo.settings.first().location)
        }
    }

    @Test
    fun `typed coordinates are stored with the nearest city, and a later location without one drops it`() {
        val point = SavedLocation(Location(30.01, 31.21, ZoneId.of("Africa/Cairo")), LocationSource.MANUAL,
            CityRef(360630L, "Cairo", "القاهرة", "EG", isNear = true))
        withRepository { repo, _ -> repo.setLocation(point) }
        assertEquals(point, withRepository { repo, _ -> repo.savedLocation.first() })
        val bare = point.copy(city = null)
        withRepository { repo, _ -> repo.setLocation(bare) }
        assertEquals(bare, withRepository { repo, _ -> repo.savedLocation.first() })
        withRepository { repo, _ -> repo.clearLocation() }
        assertEquals(PrayerSettings.Default, withRepository { repo, _ -> repo.settings.first() })
    }

    @Test
    fun `the Calculation Method and Asr Convention survive an app restart`() {
        withRepository { repo, _ ->
            repo.setCalculationMethod(CalculationMethod.EGYPTIAN)
            repo.setAsrConvention(AsrConvention.HANAFI)
        }

        val restored = withRepository { repo, _ -> repo.settings.first() }

        assertEquals(CalculationMethod.EGYPTIAN, restored.method)
        assertEquals(AsrConvention.HANAFI, restored.asrConvention)
    }

    @Test
    fun `the Hijri offset and digit style survive an app restart`() {
        assertEquals(DigitStyle.AUTOMATIC, withRepository { repo, _ -> repo.digitStyle.first() })
        withRepository { repo, _ ->
            repo.setHijriOffsetDays(-1)
            repo.setDigitStyle(DigitStyle.ARABIC_INDIC)
        }

        withRepository { repo, _ ->
            assertEquals(-1, repo.settings.first().hijriOffsetDays)
            assertEquals(DigitStyle.ARABIC_INDIC, repo.digitStyle.first())
        }
    }

    @Test
    fun `a stored Hijri offset outside the allowed range reads as no offset`() {
        withRepository { _, store -> store.edit { it[intPreferencesKey("hijri_offset_days")] = 5 } }

        assertEquals(0, withRepository { repo, _ -> repo.settings.first() }.hijriOffsetDays)
    }

    @Test
    fun `a stored method this build doesn't know falls back to the default`() {
        withRepository { _, store -> store.edit { it[stringPreferencesKey("calculation_method")] = "TEHRAN" } }

        val restored = withRepository { repo, _ -> repo.settings.first() }

        assertEquals(PrayerSettings.Default.method, restored.method)
    }

    @Test
    fun `alert settings survive an app restart and reach the core's settings`() {
        val fajr = app.mawaqeet.core.PrayerAlertSettings(
            sound = app.mawaqeet.core.AdhanSoundChoice.FULL,
            reminder = true,
            reminderMinutes = 20,
        )
        val maghrib = app.mawaqeet.core.PrayerAlertSettings(adhanAlert = false)
        withRepository { repo, _ ->
            repo.setPrayerAlert(app.mawaqeet.core.Prayer.FAJR, fajr)
            repo.setPrayerAlert(app.mawaqeet.core.Prayer.MAGHRIB, maghrib)
            repo.setAlarmClockMode(true)
            repo.setPlayOnSilent(true)
            repo.setVibration(false)
        }

        val alerts = withRepository { repo, _ -> repo.settings.first() }.alerts

        assertEquals(
            app.mawaqeet.core.AlertSettings(alarmClockMode = true, playOnSilent = true, vibration = false)
                .with(app.mawaqeet.core.Prayer.FAJR, fajr)
                .with(app.mawaqeet.core.Prayer.MAGHRIB, maghrib),
            alerts,
        )
    }

    @Test
    fun `a stored reminder lead time below the minimum reads as the default instead of failing`() {
        withRepository { _, store -> store.edit { it[intPreferencesKey("alert_dhuhr_reminder_minutes")] = 5 } }

        val dhuhr = withRepository { repo, _ -> repo.settings.first() }.alerts.of(app.mawaqeet.core.Prayer.DHUHR)

        assertEquals(15, dhuhr.reminderMinutes)
    }
}
