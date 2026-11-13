package app.mawaqeet.data

import app.mawaqeet.core.AdhkarRecordBackup
import app.mawaqeet.core.AdhkarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Adhkar progress and the Adhkar Record in the local database (the Prayer Log's file, so the same backup rules
 * apply). Its methods block: call them (and the core's Adhkar commands and queries) off the main thread.
 */
class RoomAdhkarRepository(private val db: PrayerLogDatabase) : AdhkarRepository {
    private val dao = db.adhkarDao()
    private val version = MutableStateFlow(0)

    /** Changes after every write. Screens observe it to re-query the core. */
    val changes: StateFlow<Int> = version.asStateFlow()

    override fun counts(prayerDay: LocalDate, slot: String): Map<String, Int> =
        dao.counts(prayerDay.toString(), slot).associate { it.dhikrId to it.count }

    override fun setCount(prayerDay: LocalDate, slot: String, dhikrId: String, count: Int) {
        dao.putCount(AdhkarProgressEntry(prayerDay.toString(), slot, dhikrId, count))
        version.update { it + 1 }
    }

    override fun clearCounts(prayerDay: LocalDate, slot: String) {
        dao.clearCounts(prayerDay.toString(), slot)
        version.update { it + 1 }
    }

    // Only past Prayer Days' rows go, which no screen shows.
    override fun clearCountsBefore(prayerDay: LocalDate) {
        dao.clearCountsBefore(prayerDay.toString())
    }

    override fun completedOn(prayerDay: LocalDate): Set<String> = dao.completedOn(prayerDay.toString()).toSet()

    override fun recordCompleted(prayerDay: LocalDate, slot: String) {
        dao.insertRecord(AdhkarRecordEntry(prayerDay.toString(), slot))
        version.update { it + 1 }
    }

    override fun firstUseDate(): LocalDate? = dao.firstUse()?.let(LocalDate::parse)

    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) {
        dao.insertFirstUse(AdhkarFirstUse(prayerDay = prayerDay.toString()))
    }

    override fun deleteRecord(firstUseFrom: LocalDate) {
        dao.deleteRecord(firstUseFrom.toString())
        version.update { it + 1 }
    }

    /** The whole Adhkar Record as a Backup File holds it (not today's counts), read in one transaction. Blocks. */
    fun backup(): AdhkarRecordBackup = db.runInTransaction<AdhkarRecordBackup> {
        AdhkarRecordBackup(
            firstUse = firstUseDate(),
            completed = dao.allRecords()
                .groupBy({ LocalDate.parse(it.prayerDay) }, { it.slot })
                .mapValues { it.value.toSet() },
        )
    }
}
