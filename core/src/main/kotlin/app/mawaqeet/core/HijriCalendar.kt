package app.mawaqeet.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A day, or run of days, of the Islamic year shown on the Hijri Calendar: Hijri [month] (1 = Muharram), [days] of it.
 * A run that reaches 30 ends on the month's last day, 29 or 30. Entries are in calendar order; append new ones in
 * their place only if nothing stores them by ordinal (nothing does).
 */
enum class Occasion(val month: Int, val days: IntRange) {
    HIJRI_NEW_YEAR(1, 1..1),
    TASUA(1, 9..9),
    ASHURA(1, 10..10),
    MAWLID(3, 12..12),
    ISRA_AND_MIRAJ(7, 27..27),
    RAMADAN_START(9, 1..1),
    LAST_TEN_NIGHTS(9, 21..30),
    EID_AL_FITR(10, 1..1),
    FIRST_TEN_OF_DHU_AL_HIJJAH(12, 1..10),
    ARAFAH(12, 9..9),
    EID_AL_ADHA(12, 10..10),
    TASHREEQ(12, 11..13);

    fun fallsOn(hijri: HijriDate): Boolean = hijri.month == month && hijri.day in days
}

/**
 * A kind of voluntary fast of the Sunnah, each with its own reminder switch (#95). Never on a Forbidden Fast Day;
 * [MONDAY_THURSDAY] and [WHITE_DAYS] never in Ramadan, whose fast is obligatory.
 */
enum class SunnahFast {
    /** Every Monday and Thursday. */
    MONDAY_THURSDAY,

    /** The 13th, 14th and 15th of each Hijri month (so in Dhu al-Hijjah only the 14th and 15th). */
    WHITE_DAYS,

    /** 9 Dhu al-Hijjah. */
    ARAFAH,

    /** Tasu'a and Ashura, 9 and 10 Muharram. */
    ASHURA,

    /** 2–7 Shawwal: the six days straight after Eid, as most fast them (they may be fasted anywhere in Shawwal). */
    SIX_OF_SHAWWAL,
}

/**
 * One Gregorian day on the Hijri Calendar: its [hijri] date (with the offset), its [occasions], the Sunnah Fasts it
 * is a day of, and whether it is a Forbidden Fast Day (the two Eids and the days of Tashreeq).
 */
data class CalendarDay(
    val date: LocalDate,
    val hijri: HijriDate,
    val occasions: Set<Occasion>,
    val fasts: Set<SunnahFast>,
    val fastingForbidden: Boolean,
)

/**
 * The Hijri Calendar: Umm al-Qura ([HijriDate]) shifted by the user's [offsetDays], with the fixed table of
 * [Occasion]s and [SunnahFast] rules. Pure: the same dates always give the same days.
 */
class HijriCalendar(private val offsetDays: Int = 0) {
    fun day(date: LocalDate): CalendarDay {
        val hijri = HijriDate.of(date, offsetDays)
        val occasions = Occasion.entries.filterTo(mutableSetOf()) { it.fallsOn(hijri) }
        val forbidden = Occasion.EID_AL_FITR in occasions || Occasion.EID_AL_ADHA in occasions || Occasion.TASHREEQ in occasions
        val fasts = if (forbidden) emptySet() else sunnahFasts(date, hijri)
        return CalendarDay(date, hijri, occasions, fasts, forbidden)
    }

    /** Each day from [first] through [last], in order. */
    fun days(first: LocalDate, last: LocalDate): List<CalendarDay> =
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map(::day).toList()

    /**
     * The «القادم» list from [today]: every Occasion of the next 12 months (one entry per run of days, an Occasion
     * under way today included), plus only the nearest Monday/Thursday fast and the next white days, so the weekly
     * fasts don't flood it. In date order; on the same day, Occasions in table order before fasts.
     */
    fun upcoming(today: LocalDate): List<Upcoming> {
        val end = today.plusMonths(UPCOMING_MONTHS)
        val days = days(today.minusDays(LONGEST_RUN), end.plusDays(LONGEST_RUN))
        val occasions = Occasion.entries.flatMap { occasion ->
            runs(days) { occasion in it.occasions }
                .filter { (first, last) -> !last.date.isBefore(today) && first.date.isBefore(end) }
                .map { (first, last) -> Upcoming.OccasionDays(occasion, first, last, daysAway(today, first)) }
        }
        val fasts = listOf(SunnahFast.MONDAY_THURSDAY, SunnahFast.WHITE_DAYS).mapNotNull { fast ->
            runs(days) { fast in it.fasts }
                .firstOrNull { (_, last) -> !last.date.isBefore(today) }
                ?.let { (first, last) -> Upcoming.FastDays(fast, first, last, daysAway(today, first)) }
        }
        return (occasions + fasts).sortedWith(compareBy<Upcoming> { it.first.date }.thenBy { it.rank })
    }

    private fun sunnahFasts(date: LocalDate, hijri: HijriDate): Set<SunnahFast> = buildSet {
        val ramadan = hijri.month == RAMADAN_MONTH
        if (!ramadan && (date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY)) add(SunnahFast.MONDAY_THURSDAY)
        if (!ramadan && hijri.day in 13..15) add(SunnahFast.WHITE_DAYS)
        if (hijri.month == 12 && hijri.day == 9) add(SunnahFast.ARAFAH)
        if (hijri.month == 1 && hijri.day in 9..10) add(SunnahFast.ASHURA)
        if (hijri.month == 10 && hijri.day in 2..7) add(SunnahFast.SIX_OF_SHAWWAL)
    }

    private companion object {
        const val UPCOMING_MONTHS = 12L

        /** The longest run of days an Occasion spans (the first ten of Dhu al-Hijjah), so a run under way is found whole. */
        const val LONGEST_RUN = 10L

        fun daysAway(today: LocalDate, first: CalendarDay): Int =
            ChronoUnit.DAYS.between(today, first.date).toInt().coerceAtLeast(0)

        /** The maximal runs of consecutive [days] matching [belongs], as (first, last) pairs. */
        fun runs(days: List<CalendarDay>, belongs: (CalendarDay) -> Boolean): List<Pair<CalendarDay, CalendarDay>> {
            val runs = mutableListOf<Pair<CalendarDay, CalendarDay>>()
            var start: CalendarDay? = null
            days.forEachIndexed { i, day ->
                if (belongs(day)) {
                    if (start == null) start = day
                    if (i == days.lastIndex || !belongs(days[i + 1])) {
                        runs += (start ?: day) to day
                        start = null
                    }
                }
            }
            return runs
        }
    }
}

/**
 * An entry of the «القادم» list: an Occasion or a Sunnah Fast from [first] through [last] (the same day for a single
 * day), [daysAway] days from today (0: today, or already under way).
 */
sealed interface Upcoming {
    val first: CalendarDay
    val last: CalendarDay
    val daysAway: Int

    data class OccasionDays(
        val occasion: Occasion,
        override val first: CalendarDay,
        override val last: CalendarDay,
        override val daysAway: Int,
    ) : Upcoming

    data class FastDays(
        val fast: SunnahFast,
        override val first: CalendarDay,
        override val last: CalendarDay,
        override val daysAway: Int,
    ) : Upcoming
}

/** Same-day order: Occasions in table order, then fasts. */
private val Upcoming.rank: Int
    get() = when (this) {
        is Upcoming.OccasionDays -> occasion.ordinal
        is Upcoming.FastDays -> Occasion.entries.size + fast.ordinal
    }
