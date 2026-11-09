package app.mawaqeet.core

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * The prayer core: owns every rule about Prayer Times and the Prayer Log. Android code is a thin adapter around it.
 *
 * It is cheap to build: create a new one whenever [settings] change. The current instant always comes from
 * [clock], so tests pass a fixed or fake clock. Prayer Log entries are read and written through [log].
 */
class PrayerCore(
    private val settings: PrayerSettings,
    private val clock: Clock,
    private val log: PrayerLogRepository,
) {
    /** The calendar date to display now, at the location, with its Prayer Times and Sunrise. */
    fun displayDay(): DisplayDay {
        val now = clock.instant()
        val date = LocalDate.ofInstant(now, settings.location.zone)
        val day = calculateDay(settings, date)
        val next = day.prayerTimes.firstOrNull { it.time.toInstant().isAfter(now) }
            ?: calculateDay(settings, date.plusDays(1)).prayerTimes.first()
        return DisplayDay(
            date = date, prayerTimes = day.prayerTimes, sunrise = day.sunrise, next = next,
            hijriDate = HijriDate.of(date, settings.hijriOffsetDays),
        )
    }

    /** The Prayer Day now falls in. */
    fun currentPrayerDay(): PrayerDay = places().let { prayerDay(prayerDayDateAt(clock.instant(), it), it) }

    /** The Prayer Day [instant] belongs to: from its Fajr (inclusive) to the next Fajr. */
    fun prayerDayAt(instant: Instant): PrayerDay = places().let { prayerDay(prayerDayDateAt(instant, it), it) }

    /** The Prayer Day identified by [date], with each Prayer's state as of now. */
    fun prayerDay(date: LocalDate): PrayerDay = prayerDay(date, places())

    private fun prayerDay(date: LocalDate, places: List<PastPlace>): PrayerDay {
        val now = clock.instant()
        val day = calculateDay(settingsOn(date, places), date)
        val prayed = log.prayedOn(date)
        val states = day.prayerTimes.associate { (prayer, time) ->
            prayer to when {
                // A mark is never hidden, even if travel moved its Prayer Time later.
                prayer in prayed -> LogState.PRAYED
                time.toInstant().isAfter(now) -> LogState.NOT_YET_DUE
                else -> LogState.NOT_MARKED
            }
        }
        val today = prayerDayDateAt(now, places)
        return PrayerDay(
            date = date,
            prayerTimes = day.prayerTimes,
            sunrise = day.sunrise,
            states = states,
            isEditable = date <= today && date >= today.minusDays(EDIT_WINDOW_DAYS - 1),
            hijriDate = HijriDate.of(date, settings.hijriOffsetDays),
        )
    }

    /**
     * The seven Prayer Days ending with [lastDay] (the current Prayer Day by default), oldest first, with each
     * Prayer's state and the week's summary. Browse older weeks with `lastDay = <previous lastDay>.minusDays(7)`;
     * only today and the 6 previous Prayer Days are editable.
     */
    fun week(lastDay: LocalDate? = null): PrayerWeek {
        val places = places()
        val end = lastDay ?: prayerDayDateAt(clock.instant(), places)
        val days = (WEEK_DAYS - 1 downTo 0L).map { prayerDay(end.minusDays(it), places) }
        val countedFrom = log.firstUseDate() ?: prayerDayDateAt(clock.instant(), places)
        return PrayerWeek(days, countedFrom, WeeklySummary.of(days, countedFrom))
    }

    /**
     * Permanently deletes every Prayer Log entry (Settings → Prayer Log, after the user confirms). The summary
     * then counts from the current Prayer Day, so the wiped days never show as missed.
     */
    fun deletePrayerLog() = log.deleteAllEntries(firstUseFrom = prayerDayDateAt(clock.instant(), places()))

    /** Marks [prayer] on the Prayer Day [date] as prayed, unless it isn't markable yet or the day is read-only. */
    fun mark(date: LocalDate, prayer: Prayer): LogCommandResult = setPrayed(date, prayer, true)

    /** Removes the mark from [prayer] on the Prayer Day [date], under the same rules as [mark]. */
    fun unmark(date: LocalDate, prayer: Prayer): LogCommandResult = setPrayed(date, prayer, false)

    /**
     * Every Adhan Alert due after now (exclusive, so an alarm firing now is not planned again) and within the
     * next [AlertPlan.WINDOW]. Sunrise never alerts. Each Prayer follows its [PrayerAlertSettings]; its sound is
     * the chosen recording's Custom Adhan when one is set ([AlertSettings.customAdhans]).
     */
    fun alertPlan(): AlertPlan {
        val now = clock.instant()
        val end = now.plus(AlertPlan.WINDOW)
        val zone = settings.location.zone
        val today = LocalDate.ofInstant(now, zone)
        // Yesterday too: a Prayer Day is a calendar date's Fajr onwards, and the window spans at most 3 dates.
        val days = (-1L..2L).map { offset -> today.plusDays(offset).let { it to calculateDay(settings, it) } }
        val alerts = days.flatMap { (date, day) ->
            day.prayerTimes.flatMap { time -> plannedAlerts(time, date, settings.alerts) }
        }.filter { it.instant.isAfter(now) && !it.instant.isAfter(end) }.sortedBy { it.instant }
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        // Prayer Times no Adhan Alert announces still move the display day's next Prayer (widgets).
        val unannounced = days.flatMap { (_, day) -> day.prayerTimes }
            .filter { !settings.alerts.of(it.prayer).adhanAlert }
            .map { it.time.toInstant() }
            .filter { it.isAfter(now) && it.isBefore(midnight) }
        return AlertPlan(alerts, midnight, unannounced.minOrNull() ?: midnight)
    }

    private fun plannedAlerts(time: PrayerTime, prayerDay: LocalDate, alerts: AlertSettings): List<PlannedAlert> {
        val prayer = alerts.of(time.prayer)
        val at = time.time.toInstant()
        fun alert(kind: AlertKind, instant: Instant, sound: AlertSound, mode: DeliveryMode) = PlannedAlert(
            instant = instant,
            prayer = time.prayer,
            kind = kind,
            sound = sound,
            deliveryMode = mode,
            prayerDay = prayerDay,
            isJumuah = time.isJumuah,
            prayerTime = at,
            playOnSilent = alerts.playOnSilent,
            vibration = alerts.vibration,
        )
        val reminder = if (prayer.reminder) {
            // Always an exact alarm: in alarm-clock mode the lock screen shows the Prayer, not the reminder before it.
            val lead = java.time.Duration.ofMinutes(prayer.reminderMinutes.toLong())
            alert(AlertKind.PRAYER_REMINDER, at.minus(lead), AlertSound.ReminderTone, DeliveryMode.EXACT)
        } else {
            null
        }
        val adhan = if (prayer.adhanAlert) {
            // A Custom Adhan replaces only its own recording: one for the Full Adhan never reaches Fajr's.
            val sound = AdhanRecording.of(time.prayer, prayer.sound)?.let { AlertSound.of(it, alerts) } ?: AlertSound.Silent
            alert(AlertKind.ADHAN_ALERT, at, sound, if (alerts.alarmClockMode) DeliveryMode.ALARM_CLOCK else DeliveryMode.EXACT)
        } else {
            null
        }
        return listOfNotNull(reminder, adhan)
    }

    /** Records the current Prayer Day as the first-use date, once; later calls change nothing. */
    fun recordFirstUse() = log.recordFirstUseIfAbsent(prayerDayDateAt(clock.instant(), places()))

    /**
     * Fixes every Prayer Day that has ended at this core's location, so travel never recomputes it. Call it on the
     * core built with the settings in effect BEFORE the location changes, then save the new location. The Prayer
     * Day in progress and later ones then use the new location. A second change on the same Prayer Day adds
     * nothing: the days before it are already fixed.
     */
    fun recordLocationChange() {
        val places = places()
        val today = prayerDayDateAt(clock.instant(), places)
        if (places.none { it.until >= today }) log.addPastPlace(PastPlace(today, settings.location))
    }

    private fun setPrayed(date: LocalDate, prayer: Prayer, prayed: Boolean): LogCommandResult {
        val day = prayerDay(date)
        return when {
            // A future Prayer Day is outside the window too, but "not yet due" is the truer reason.
            day.stateOf(prayer) == LogState.NOT_YET_DUE -> LogCommandResult.NOT_YET_DUE
            !day.isEditable -> LogCommandResult.OUTSIDE_EDIT_WINDOW
            else -> {
                log.setPrayed(date, prayer, prayed)
                LogCommandResult.DONE
            }
        }
    }

    /** The latest Prayer Day whose Fajr, at the place in effect that day, is at or before [instant]. */
    private fun prayerDayDateAt(instant: Instant, places: List<PastPlace>): LocalDate {
        val date = LocalDate.ofInstant(instant, settings.location.zone)
        // Travel can shift a past day's Fajr by up to about a day relative to the current zone's dates.
        return (1L downTo -2L).map { date.plusDays(it) }.first { day ->
            !calculateDay(settingsOn(day, places), day).prayerTimes.first().time.toInstant().isAfter(instant)
        }
    }

    /** Past Prayer Days, oldest first. */
    private fun places(): List<PastPlace> = log.pastPlaces().sortedBy { it.until }

    /** The settings for the Prayer Day [date]: the location in effect that day (see [PastPlace]). */
    private fun settingsOn(date: LocalDate, places: List<PastPlace>): PrayerSettings =
        places.firstOrNull { date < it.until }?.let { settings.copy(location = it.location) } ?: settings

    private companion object {
        /** Today plus the 6 previous Prayer Days. */
        const val EDIT_WINDOW_DAYS = 7L

        /** A Prayer Week's length. */
        const val WEEK_DAYS = 7L
    }
}
