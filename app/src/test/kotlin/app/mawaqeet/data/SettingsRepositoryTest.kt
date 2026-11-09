package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.PrayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

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
            scope.cancel()
        }
    }

    @Test
    fun `before any choice the settings are the defaults`() {
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
    fun `a stored method this build doesn't know falls back to the default`() {
        withRepository { _, store -> store.edit { it[stringPreferencesKey("calculation_method")] = "TEHRAN" } }

        val restored = withRepository { repo, _ -> repo.settings.first() }

        assertEquals(PrayerSettings.Default.method, restored.method)
    }
}
