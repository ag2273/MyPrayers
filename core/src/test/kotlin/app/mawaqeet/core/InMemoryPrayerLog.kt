package app.mawaqeet.core

import java.time.LocalDate

/** The tests' Prayer Log repository: a plain in-memory set. */
class InMemoryPrayerLog : PrayerLogRepository {
    private val entries = mutableSetOf<Pair<LocalDate, Prayer>>()
    private var firstUse: LocalDate? = null

    override fun prayedOn(prayerDay: LocalDate): Set<Prayer> =
        entries.filter { it.first == prayerDay }.mapTo(mutableSetOf()) { it.second }

    override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) {
        if (prayed) entries += prayerDay to prayer else entries -= prayerDay to prayer
    }

    override fun firstUseDate(): LocalDate? = firstUse

    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) {
        if (firstUse == null) firstUse = prayerDay
    }
}
