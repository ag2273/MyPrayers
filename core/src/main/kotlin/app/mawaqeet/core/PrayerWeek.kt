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
     * yet). Days before it never count as missed.
     */
    val countedFrom: LocalDate,
    val summary: WeeklySummary,
) {
    /** Whether [day] is before the app was first used, so the summary leaves it out. */
    fun isBeforeFirstUse(day: PrayerDay): Boolean = day.date < countedFrom
}

/** How many times a Prayer was marked out of the times it could have been. */
data class PrayerCount(val prayed: Int, val possible: Int)

/**
 * Per-Prayer counts (e.g. Fajr 5/7) and the total (e.g. 31/35) over a [PrayerWeek]. Only Prayer Days on or after
 * the first-use date count, and only Prayers whose time has arrived: a Prayer not yet due is not yet missed.
 */
data class WeeklySummary(val perPrayer: Map<Prayer, PrayerCount>, val total: PrayerCount) {
    fun of(prayer: Prayer): PrayerCount = perPrayer.getValue(prayer)

    companion object {
        internal fun of(days: List<PrayerDay>, countedFrom: LocalDate): WeeklySummary {
            val counted = days.filter { it.date >= countedFrom }
            val perPrayer = Prayer.entries.associateWith { prayer ->
                val states = counted.map { it.stateOf(prayer) }
                PrayerCount(
                    prayed = states.count { it == LogState.PRAYED },
                    possible = states.count { it != LogState.NOT_YET_DUE },
                )
            }
            return WeeklySummary(
                perPrayer,
                PrayerCount(perPrayer.values.sumOf { it.prayed }, perPrayer.values.sumOf { it.possible }),
            )
        }
    }
}
