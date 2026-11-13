package app.mawaqeet.data

import app.mawaqeet.core.Backup
import app.mawaqeet.core.BackupSnapshot
import app.mawaqeet.core.QiyamStyle
import app.mawaqeet.core.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock

/** How a restore from a Backup File ended. Every outcome but [Restored] changed nothing. */
sealed interface RestoreOutcome {
    /**
     * The file's settings replaced the phone's and its records were merged in. [usesAlertPermissions]: the
     * restored settings use Prayer Silence, alarm-clock mode or the Qiyam reminder in alarm style (a full-screen
     * alarm, #97), so the app offers Fix Alerts.
     */
    data class Restored(val usesAlertPermissions: Boolean) : RestoreOutcome

    /** Not a Backup File, or a damaged one. */
    data object Damaged : RestoreOutcome

    /** A Backup File from a newer app version. */
    data object NewerVersion : RestoreOutcome

    /** The chosen file couldn't be opened or read. */
    data object Unreadable : RestoreOutcome
}

/**
 * The Backup File on Android (#86): gathers a [BackupSnapshot] from the settings store and the database and writes
 * it with the core's [Backup], and restores one. The file goes only where the user chose (a Storage Access
 * Framework document); the app never sends it anywhere. One per process, `MawaqeetApplication.backup`.
 */
class BackupFiles(
    private val settings: SettingsRepository,
    private val prayerLog: RoomPrayerLogRepository,
    private val adhkar: RoomAdhkarRepository,
    private val database: PrayerLogDatabase,
    private val clock: Clock,
    /**
     * Runs after every successful restore, with the Custom Adhan references the restore let go: the app releases
     * their permissions, reschedules the alerts and refreshes the widgets.
     */
    private val afterRestore: suspend (releasedCustomAdhans: Set<String>) -> Unit,
) {
    /** Everything the Backup File carries, as stored now. */
    suspend fun snapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        BackupSnapshot(settings.backupSettings(), prayerLog.backup(), adhkar.backup())
    }

    /**
     * Writes a Backup File to the stream [open] gives (closed afterwards), then stores the export time. Returns false,
     * with the last export date unchanged, when the file can't be opened or written.
     */
    suspend fun export(open: () -> OutputStream?): Boolean = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val bytes = Backup.export(snapshot(), exportedAt = now)
        try {
            val out = open() ?: return@withContext false
            out.use { it.write(bytes) }
        } catch (_: IOException) {
            return@withContext false
        } catch (_: SecurityException) {
            return@withContext false
        }
        settings.setLastBackupExport(now)
        true
    }

    /**
     * Restores the Backup File [open] gives (closed afterwards), with the core's merge rules ([Backup.restore]):
     * the records in one transaction, then the settings, then [afterRestore]. Everything is checked before the
     * first write, so a file that can't be read, isn't a Backup File, or is from a newer version changes nothing.
     */
    suspend fun restore(open: () -> InputStream?): RestoreOutcome = withContext(Dispatchers.IO) {
        val bytes = read(open) ?: return@withContext RestoreOutcome.Unreadable
        val merged = when (val result = Backup.restore(bytes, snapshot())) {
            is RestoreResult.Restored -> result.snapshot
            RestoreResult.Damaged -> return@withContext RestoreOutcome.Damaged
            RestoreResult.NewerVersion -> return@withContext RestoreOutcome.NewerVersion
        }
        if (!settings.canRestore(merged.settings)) return@withContext RestoreOutcome.Damaged

        database.runInTransaction {
            prayerLog.restore(merged.prayerLog)
            adhkar.restore(merged.adhkarRecord)
        }
        val released = settings.restoreSettings(merged.settings)
        afterRestore(released)
        val alerts = settings.settings.first().alerts
        val qiyam = alerts.worshipReminders.sunnahPrayers.qiyam
        RestoreOutcome.Restored(
            usesAlertPermissions = alerts.alarmClockMode || alerts.prayerSilence.enabled ||
                (qiyam.enabled && qiyam.style == QiyamStyle.ALARM),
        )
    }

    /** The file's bytes, or null when it can't be opened or read. A file far larger than any backup counts as unreadable. */
    private fun read(open: () -> InputStream?): ByteArray? = try {
        open()?.use { input ->
            // readNBytes needs API 33; this reads at most one byte past the limit.
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() <= MAX_FILE_BYTES) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray().takeIf { it.size <= MAX_FILE_BYTES }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private companion object {
        /** Years of marks are well under a megabyte; anything this big isn't a Backup File. */
        const val MAX_FILE_BYTES = 32 * 1024 * 1024
    }
}
