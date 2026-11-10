package app.mawaqeet.core

import java.time.LocalDate

/**
 * Seven consecutive Prayer Days × five Prayers, as the Prayer Log screen and the Week Prayer Log widget show them,
 * with the week's summary. No streaks, badges or scores: only counts.
 */
data class PrayerWeek(
    /** Oldest first; the last one is the week's latest Prayer Day (today for the current week). */
    val days: List<PrayerDay>,
    /**
     * The first Prayer Day the summary counts: the first-use date (or the current Prayer Day if none is recorded
     * yet). Days before it never count as missed, only as prayed where marked.
     */
    val countedFrom: LocalDate,
    val summary: WeeklySummary,
) {
    /** Whether [day] is before the app was first used, so the summary counts only what was marked on it. */
    fun isBeforeFirstUse(day: PrayerDay): Boolean = day.date < countedFrom
}

/** How many times a Prayer was marked out of the times it could have been. */
data class PrayerCount(val prayed: Int, val possible: Int)

/**
 * Per-Prayer counts (e.g. Fajr 5/7) and the total (e.g. 31/35) over a [PrayerWeek]. On Prayer Days from the
 * first-use date every Prayer whose time has arrived counts (a Prayer not yet due is not yet missed); before it, only
 * the Prayers marked prayed count, so marking earlier days adds to the week and leaving them unmarked never misses one.
 */
data class WeeklySummary(val perPrayer: Map<Prayer, PrayerCount>, val total: PrayerCount) {
    fun of(prayer: Prayer): PrayerCount = perPrayer.getValue(prayer)

    companion object {
        internal fun of(days: List<PrayerDay>, countedFrom: LocalDate): WeeklySummary {
            val perPrayer = Prayer.entries.associateWith { prayer ->
                // Before first use only what was marked counts (a Prayer marked later still shows); nothing there is missed.
                val states = days.map { it.stateOf(prayer) to (it.date >= countedFrom) }
                PrayerCount(
                    prayed = states.count { (state, _) -> state == LogState.PRAYED },
                    possible = states.count { (state, counted) -> if (counted) state != LogState.NOT_YET_DUE else state == LogState.PRAYED },
                )
            }
            return WeeklySummary(
                perPrayer,
                PrayerCount(perPrayer.values.sumOf { it.prayed }, perPrayer.values.sumOf { it.possible }),
            )
        }
    }
}
