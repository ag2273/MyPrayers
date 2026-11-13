package app.mawaqeet.data

import app.mawaqeet.core.Backup
import app.mawaqeet.core.BackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.Clock

/**
 * The Backup File on Android (#86): gathers a [BackupSnapshot] from the settings store and the database and writes
 * it with the core's [Backup]. The file goes only where the user chose (a Storage Access Framework document); the
 * app never sends it anywhere. One per process, `MawaqeetApplication.backup`.
 */
class BackupFiles(
    private val settings: SettingsRepository,
    private val prayerLog: RoomPrayerLogRepository,
    private val adhkar: RoomAdhkarRepository,
    private val clock: Clock,
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
}
