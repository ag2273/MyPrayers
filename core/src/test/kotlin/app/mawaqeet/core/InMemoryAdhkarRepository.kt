package app.mawaqeet.core

import java.time.LocalDate

/** The tests' Adhkar repository: plain in-memory maps. */
class InMemoryAdhkarRepository : AdhkarRepository {
    private val counts = mutableMapOf<Triple<LocalDate, String, String>, Int>()
    private val completed = mutableSetOf<Pair<LocalDate, String>>()

    override fun counts(prayerDay: LocalDate, slot: String): Map<String, Int> =
        counts.filterKeys { it.first == prayerDay && it.second == slot }.mapKeys { it.key.third }

    override fun setCount(prayerDay: LocalDate, slot: String, dhikrId: String, count: Int) {
        counts[Triple(prayerDay, slot, dhikrId)] = count
    }

    override fun clearCounts(prayerDay: LocalDate, slot: String) {
        counts.keys.removeAll { it.first == prayerDay && it.second == slot }
    }

    override fun clearCountsBefore(prayerDay: LocalDate) {
        counts.keys.removeAll { it.first < prayerDay }
    }

    override fun completedOn(prayerDay: LocalDate): Set<String> =
        completed.filter { it.first == prayerDay }.mapTo(mutableSetOf()) { it.second }

    override fun recordCompleted(prayerDay: LocalDate, slot: String) {
        completed += prayerDay to slot
    }
}
