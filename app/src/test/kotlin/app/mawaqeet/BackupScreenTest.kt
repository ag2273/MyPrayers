package app.mawaqeet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.Prayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Settings → Backup (#98): what the file holds, and export through the system's create-document picker. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupScreenTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val folder = TemporaryFolder()

    private val app get() = ApplicationProvider.getApplicationContext<MawaqeetApplication>()

    private fun openBackup(settings: String, backup: String) {
        compose.onNodeWithText(settings).performClick()
        compose.onNodeWithText(backup).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun lastExport() = runBlocking { app.settings.lastBackupExport.first() }

    /** Taps export and returns the picker request the app started. */
    private fun startExport(label: String): Intent {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull("export starts the system picker", request)
        return request.intent
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the Backup page says what the file holds, warns it is private, and restore waits for the next version`() {
        openBackup("الإعدادات", "النسخة الاحتياطية")

        compose.onNodeWithText("سجل الصلاة", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("الأذان المخصص", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("موقعك وسجل عبادتك", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("لم تُصدَّر نسخة احتياطية بعد").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("استعادة من ملف").performScrollTo().assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "en")
    fun `export writes the Backup File where the user chose and shows the export date`() {
        runBlocking(Dispatchers.IO) {
            app.settings.setCalculationMethod(CalculationMethod.EGYPTIAN)
            app.prayerLog.setPrayed(LocalDate.parse("2026-11-08"), Prayer.FAJR, true)
        }
        openBackup("Settings", "Backup")

        val request = startExport("Export a backup")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
        assertEquals("application/json", request.type)
        assertEquals("mawaqeet-backup-${LocalDate.now()}.json", request.getStringExtra(Intent.EXTRA_TITLE))

        val file = folder.newFile("chosen.json")
        shadowOf(compose.activity).receiveResult(request, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        compose.waitUntil(timeoutMillis = 5_000) { lastExport() != null }

        val written = file.readText()
        assertTrue(written, "\"format\": \"mawaqeet-backup\"" in written)
        assertTrue(written, "\"calculation_method\": \"EGYPTIAN\"" in written)
        assertTrue(written, "\"2026-11-08\": [" in written && "\"FAJR\"" in written)
        // The page follows the stored date once DataStore emits it.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Last export:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Last export:", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `cancelling the picker changes nothing`() {
        openBackup("Settings", "Backup")

        val request = startExport("Export a backup")
        shadowOf(compose.activity).receiveResult(request, Activity.RESULT_CANCELED, null)
        compose.waitForIdle()

        assertNull(lastExport())
        compose.onNodeWithText("No backup exported yet").performScrollTo().assertIsDisplayed()
    }
}
