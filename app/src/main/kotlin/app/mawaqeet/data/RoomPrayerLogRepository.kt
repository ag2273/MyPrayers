package app.mawaqeet.data

import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * The Prayer Log in the local database. Its methods block: call them (and the core's log queries and commands)
 * off the main thread.
 */
class RoomPrayerLogRepository(db: PrayerLogDatabase) : PrayerLogRepository {
    private val dao = db.dao()
    private val version = MutableStateFlow(0)

    /** Changes after every write to the Prayer Log. Screens and widgets observe it to re-query the core. */
    val changes: StateFlow<Int> = version.asStateFlow()

    override fun prayedOn(prayerDay: LocalDate): Set<Prayer> =
        dao.prayedOn(prayerDay.toString()).mapNotNullTo(mutableSetOf()) { name ->
            Prayer.entries.firstOrNull { it.name == name }
        }

    override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) {
        if (prayed) {
            dao.insert(PrayerLogEntry(prayerDay.toString(), prayer.name))
        } else {
            dao.delete(prayerDay.toString(), prayer.name)
        }
        version.update { it + 1 }
    }

    override fun firstUseDate(): LocalDate? = dao.firstUse()?.let(LocalDate::parse)

    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) {
        dao.insertFirstUse(FirstUse(prayerDay = prayerDay.toString()))
    }
}
