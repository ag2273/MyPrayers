package app.mawaqeet.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.Location
import app.mawaqeet.core.PastPlace
import app.mawaqeet.core.Prayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Restoring a Backup File (#99) into a real database and settings store. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupRestoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: PrayerLogDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var log: RoomPrayerLogRepository
    private lateinit var adhkar: RoomAdhkarRepository
    private lateinit var backup: BackupFiles

    /** What the app does after a restore: here, only counted (and the Custom Adhan files it let go). */
    private val afterRestore = mutableListOf<Set<String>>()

    private val day7 = LocalDate.parse("2026-11-07")
    private val day8 = LocalDate.parse("2026-11-08")
    private val exportedAt = Instant.parse("2026-11-01T09:00:00Z")

    @Before
    fun open() {
        db = PrayerLogDatabase.open(ctx)
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") })
        log = RoomPrayerLogRepository(db)
        adhkar = RoomAdhkarRepository(db)
        backup = BackupFiles(settings, log, adhkar, db, Clock.fixed(exportedAt, ZoneOffset.UTC)) { released -> afterRestore += released }
    }

    @After
    fun close() {
        db.close()
        ctx.deleteDatabase(PrayerLogDatabase.FILE_NAME)
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    private fun io(block: () -> Unit) = runBlocking { withContext(Dispatchers.IO) { block() } }

    private fun <T> read(block: () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    private fun restore(json: String) = runBlocking { backup.restore { ByteArrayInputStream(json.trimIndent().toByteArray()) } }

    /** A Backup File as another phone exported it: Egyptian method, Fajr and Isha on the 8th, morning adhkar. */
    private val otherPhone = """
        {
          "format": "mawaqeet-backup", "version": 1, "exportedAt": "2026-11-10T00:00:00Z",
          "settings": {
            "calculation_method": "EGYPTIAN",
            "location_latitude": 30.0444, "location_longitude": 31.2357, "location_zone": "Africa/Cairo",
            "location_source": "MANUAL", "location_city_id": 360630, "location_city_name": "Cairo", "location_country": "EG",
            "alert_fajr_reminder_minutes": 20,
            "prayer_silence_enabled": true
          },
          "prayerLog": {
            "firstUse": "2026-09-01",
            "prayed": { "2026-11-08": ["FAJR", "ISHA"] },
            "pastPlaces": [ { "until": "2026-10-01", "latitude": 21.4225, "longitude": 39.8262, "zone": "Asia/Riyadh" } ]
          },
          "adhkarRecord": { "firstUse": "2026-10-01", "completed": { "2026-11-08": ["morning"] } }
        }
    """

    private fun markThisPhone() {
        io {
            log.recordFirstUseIfAbsent(LocalDate.parse("2026-10-20"))
            log.setPrayed(day8, Prayer.DHUHR, true)
            log.setPrayed(day7, Prayer.ASR, true)
            adhkar.recordFirstUseIfAbsent(LocalDate.parse("2026-11-05"))
            adhkar.recordCompleted(day8, "evening")
        }
        runBlocking {
            settings.setCalculationMethod(CalculationMethod.UMM_AL_QURA)
            settings.setLastBackupExport(Instant.parse("2026-10-30T12:00:00Z"))
        }
    }

    @Test
    fun `restoring into a database with marks keeps both sides' marks and takes the file's settings`() {
        markThisPhone()

        val outcome = restore(otherPhone)

        assertEquals(RestoreOutcome.Restored(usesAlertPermissions = true), outcome)
        assertEquals(setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ISHA), read { log.prayedOn(day8) })
        assertEquals(setOf(Prayer.ASR), read { log.prayedOn(day7) })
        assertEquals(LocalDate.parse("2026-09-01"), read { log.firstUseDate() })
        assertEquals(
            listOf(PastPlace(LocalDate.parse("2026-10-01"), Location(21.4225, 39.8262, ZoneId.of("Asia/Riyadh")))),
            read { log.pastPlaces() },
        )
        assertEquals(setOf("morning", "evening"), read { adhkar.completedOn(day8) })
        assertEquals(LocalDate.parse("2026-10-01"), read { adhkar.firstUseDate() })

        val restored = runBlocking { settings.settings.first() }
        assertEquals(CalculationMethod.EGYPTIAN, restored.method)
        assertEquals(30.0444, restored.location.latitude, 0.0)
        assertEquals(20, restored.alerts.of(Prayer.FAJR).reminderMinutes)
        assertTrue(restored.alerts.prayerSilence.enabled)
        assertEquals(360630L, runBlocking { settings.savedLocation.first() }?.city?.id)
        // The last export date is this phone's own.
        assertEquals(Instant.parse("2026-10-30T12:00:00Z"), runBlocking { settings.lastBackupExport.first() })
        assertEquals(1, afterRestore.size)
    }

    @Test
    fun `a restored phone falls back to the bundled recordings, and the Custom Adhan files it used are let go`() {
        runBlocking { settings.setCustomAdhan(AdhanRecording.FULL, "content://files/my-adhan.mp3") }

        restore(otherPhone)

        assertEquals(emptyMap<AdhanRecording, String>(), runBlocking { settings.settings.first() }.alerts.customAdhans)
        assertEquals(listOf(setOf("content://files/my-adhan.mp3")), afterRestore)
    }

    @Test
    fun `settings that need no alert permission are restored without asking for one`() {
        assertEquals(
            RestoreOutcome.Restored(usesAlertPermissions = false),
            restore(otherPhone.replace("\"prayer_silence_enabled\": true", "\"vibration\": false")),
        )
    }

    @Test
    fun `a restored Qiyam reminder in alarm style needs the full-screen alarm permission`() {
        val qiyam = { style: String ->
            otherPhone.replace("\"prayer_silence_enabled\": true", "\"worship_qiyam_enabled\": true, \"worship_qiyam_style\": \"$style\"")
        }

        assertEquals(RestoreOutcome.Restored(usesAlertPermissions = true), restore(qiyam("ALARM")))
        assertEquals(RestoreOutcome.Restored(usesAlertPermissions = false), restore(qiyam("NOTIFICATION")))
    }

    private fun assertUntouched() {
        assertEquals(setOf(Prayer.DHUHR), read { log.prayedOn(day8) })
        assertEquals(LocalDate.parse("2026-10-20"), read { log.firstUseDate() })
        assertEquals(emptyList<PastPlace>(), read { log.pastPlaces() })
        assertEquals(setOf("evening"), read { adhkar.completedOn(day8) })
        assertEquals(CalculationMethod.UMM_AL_QURA, runBlocking { settings.settings.first() }.method)
        assertEquals(emptyList<Set<String>>(), afterRestore)
    }

    @Test
    fun `a damaged file changes nothing`() {
        markThisPhone()

        assertEquals(RestoreOutcome.Damaged, restore(otherPhone.dropLast(40)))
        assertUntouched()
    }

    @Test
    fun `a file from a newer version changes nothing`() {
        markThisPhone()

        assertEquals(RestoreOutcome.NewerVersion, restore(otherPhone.replace("\"version\": 1", "\"version\": 7")))
        assertUntouched()
    }

    @Test
    fun `a setting of the wrong kind refuses the whole file, records included`() {
        markThisPhone()

        assertEquals(RestoreOutcome.Damaged, restore(otherPhone.replace("\"EGYPTIAN\"", "true")))
        assertUntouched()
    }

    @Test
    fun `a file that can't be read changes nothing`() {
        markThisPhone()

        assertEquals(RestoreOutcome.Unreadable, runBlocking { backup.restore { throw IOException("gone") } })
        assertEquals(RestoreOutcome.Unreadable, runBlocking { backup.restore { null } })
        assertUntouched()
    }
}
