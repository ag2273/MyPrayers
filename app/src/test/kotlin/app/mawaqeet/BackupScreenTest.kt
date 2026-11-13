package app.mawaqeet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
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
    fun `the Backup page says what the file holds and warns it is private`() {
        openBackup("الإعدادات", "النسخة الاحتياطية")

        compose.onNodeWithText("سجل الصلاة", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("الأذان المخصص", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("موقعك وسجل عبادتك", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("لم تُصدَّر نسخة احتياطية بعد").performScrollTo().assertIsDisplayed()
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

    /** A Backup File as another phone exported it. */
    private fun backupFile(version: Int = 1, silence: Boolean = false): Uri {
        val file = folder.newFile()
        file.writeText(
            """
            {
              "format": "mawaqeet-backup", "version": $version, "exportedAt": "2026-11-10T00:00:00Z",
              "settings": { "calculation_method": "EGYPTIAN", "prayer_silence_enabled": $silence },
              "prayerLog": { "firstUse": "2026-09-01", "prayed": { "2026-11-08": ["ISHA"] }, "pastPlaces": [] },
              "adhkarRecord": { "firstUse": null, "completed": {} }
            }
            """.trimIndent(),
        )
        return Uri.fromFile(file)
    }

    /** Taps restore, answers the open-document picker with [uri], and returns the picker request. */
    private fun pickForRestore(uri: Uri?): Intent {
        compose.onNodeWithText("Restore from a file").performScrollTo().performClick()
        compose.waitForIdle()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull("restore starts the system picker", request)
        shadowOf(compose.activity).receiveResult(
            request.intent,
            if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            uri?.let { Intent().setData(it) },
        )
        compose.waitForIdle()
        return request.intent
    }

    /** The settings file outlives a test; every restore test starts from a method the file it restores doesn't use. */
    private fun startOnUmmAlQura() = runBlocking { app.settings.setCalculationMethod(CalculationMethod.UMM_AL_QURA) }

    private fun method() =runBlocking { app.settings.settings.first().method }

    private fun waitForText(text: String) = compose.waitUntil(timeoutMillis = 5_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    @Config(qualifiers = "en")
    fun `restore asks before replacing the settings, then merges the file and says so`() {
        runBlocking(Dispatchers.IO) { app.prayerLog.setPrayed(LocalDate.parse("2026-11-08"), Prayer.FAJR, true) }
        startOnUmmAlQura()
        openBackup("Settings", "Backup")

        val request = pickForRestore(backupFile())
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)

        // Nothing changes before the user confirms.
        compose.onNodeWithText("replace your current settings", substring = true).assertIsDisplayed()
        assertEquals(CalculationMethod.UMM_AL_QURA, method())
        compose.onNodeWithText("Restore").performClick()

        waitForText("Restored.")
        assertEquals(CalculationMethod.EGYPTIAN, method())
        assertEquals(
            setOf(Prayer.FAJR, Prayer.ISHA),
            runBlocking(Dispatchers.IO) { app.prayerLog.prayedOn(LocalDate.parse("2026-11-08")) },
        )
        assertEquals(0, compose.onAllNodesWithText("Fix alerts").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `cancelling the confirmation changes nothing`() {
        startOnUmmAlQura()
        compose.onNodeWithText("الإعدادات").performClick()
        compose.onNodeWithText("النسخة الاحتياطية").performScrollTo().performClick()
        compose.onNodeWithText("استعادة من ملف").performScrollTo().performClick()
        compose.waitForIdle()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        shadowOf(compose.activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(backupFile()))
        compose.waitForIdle()

        compose.onNodeWithText("ستحلّ إعدادات الملف محل إعداداتك الحالية", substring = true).assertIsDisplayed()
        compose.onNodeWithText("إلغاء").performClick()
        compose.waitForIdle()

        assertEquals(CalculationMethod.UMM_AL_QURA, method())
        assertEquals(0, compose.onAllNodesWithText("ستحلّ إعدادات الملف", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "en")
    fun `restored settings that need an alert permission offer Fix Alerts`() {
        openBackup("Settings", "Backup")
        pickForRestore(backupFile(silence = true))
        compose.onNodeWithText("Restore").performClick()

        waitForText("Restored.")
        compose.onNodeWithText("Prayer Silence or alarm-clock mode", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fix alerts").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `a damaged file, a newer version and an unreadable file each say so and change nothing`() {
        startOnUmmAlQura()
        openBackup("Settings", "Backup")

        val damaged = folder.newFile().apply { writeText("{ \"format\": \"mawaqeet-backup\", ") }
        pickForRestore(Uri.fromFile(damaged))
        compose.onNodeWithText("Restore").performClick()
        waitForText("isn't a Mawaqeet backup")

        pickForRestore(backupFile(version = 9))
        compose.onNodeWithText("Restore").performClick()
        waitForText("newer version of Mawaqeet")

        pickForRestore(Uri.fromFile(java.io.File(folder.root, "missing.json")))
        compose.onNodeWithText("Restore").performClick()
        waitForText("couldn't be read")

        assertEquals(CalculationMethod.UMM_AL_QURA, method())
    }

    @Test
    @Config(qualifiers = "en")
    fun `cancelling the restore picker asks nothing and changes nothing`() {
        startOnUmmAlQura()
        openBackup("Settings", "Backup")

        pickForRestore(null)

        assertEquals(0, compose.onAllNodesWithText("replace your current settings", substring = true).fetchSemanticsNodes().size)
        assertEquals(CalculationMethod.UMM_AL_QURA, method())
    }
}
