package app.mawaqeet.core

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Where Prayer Log entries live. The Android app implements it with a local database; tests use an in-memory one.
 *
 * Entries are keyed by (Prayer Day, Prayer), where a Prayer Day is identified by the local calendar date of its
 * Fajr at the place the user was that day. Never by a clock time, so recalculation never removes marks.
 * Implementations store what they are told; every rule (markability, edit window) lives in [PrayerCore].
 */
interface PrayerLogRepository {
    /** The Prayers marked as prayed on [prayerDay]. */
    fun prayedOn(prayerDay: LocalDate): Set<Prayer>

    fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean)

    /** Whether any Prayer Day has a Prayer marked as prayed. */
    fun hasAnyEntry(): Boolean

    /** The Prayer Day the app was first used on, or null before it is recorded. */
    fun firstUseDate(): LocalDate?

    /** Records [prayerDay] as the first-use date unless one is already recorded. */
    fun recordFirstUseIfAbsent(prayerDay: LocalDate)

    /** Every recorded [PastPlace], in any order. Empty until the location first changes. */
    fun pastPlaces(): List<PastPlace>

    /** Stores [place] as told; [PrayerCore.recordLocationChange] decides what to add. */
    fun addPastPlace(place: PastPlace)

    /**
     * Removes every Prayer Log entry and records [firstUseFrom] as the first-use date, replacing any recorded one,
     * in one step. Past places stay: they fix which day each Prayer Day was, not what was prayed.
     */
    fun deleteAllEntries(firstUseFrom: LocalDate)
}

/**
 * Where the user was for past Prayer Days: [location] is in effect for every Prayer Day before [until] and on or
 * after the previous entry's [until] (or since always, for the earliest entry). Prayer Days from the latest
 * entry's [until] on use the current settings' location. Only the location is kept: a Calculation Method or Asr
 * Convention change still recalculates every day.
 */
data class PastPlace(val until: LocalDate, val location: Location)

/** A Prayer's Prayer Log state on one Prayer Day. */
enum class LogState {
    PRAYED,

    /** Its Prayer Time has arrived but it is not marked. */
    NOT_MARKED,

    /** Its Prayer Time has not arrived yet, so it cannot be marked. */
    NOT_YET_DUE,
}

/** The outcome of a mark or unmark command. */
enum class LogCommandResult {
    DONE,

    /** Rejected: the Prayer's time has not arrived yet. */
    NOT_YET_DUE,

    /** Rejected: the Prayer Day is outside the edit window (today plus the 6 previous Prayer Days). */
    OUTSIDE_EDIT_WINDOW,
}

/**
 * The span from one Fajr to the next, with its Prayer Times and each Prayer's Prayer Log state as of now.
 *
 * [date] is its identity: the local calendar date of its Fajr at the location. It does not depend on the
 * Calculation Method or any other setting.
 */
data class PrayerDay(
    val date: LocalDate,
    /** The five Prayer Times, in day order. */
    val prayerTimes: List<PrayerTime>,
    val sunrise: ZonedDateTime,
    val states: Map<Prayer, LogState>,
    /** Whether [date] is within the edit window (today plus the 6 previous Prayer Days). */
    val isEditable: Boolean,
    /** [date] in the Hijri calendar, with the user's Hijri offset (the day label shows it after the Gregorian date). */
    val hijriDate: HijriDate,
) {
    fun stateOf(prayer: Prayer): LogState = states.getValue(prayer)

    /** Whether tapping [prayer] would mark or unmark it: its time has arrived and the day is editable. */
    fun isMarkable(prayer: Prayer): Boolean = isEditable && stateOf(prayer) != LogState.NOT_YET_DUE
}
