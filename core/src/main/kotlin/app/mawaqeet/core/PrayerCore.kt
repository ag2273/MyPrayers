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
    /** The location the times are for (the Clock Widgets' weather is fetched for it, rounded). */
    val location: Location get() = settings.location

    /** The alert settings in effect, for showing each Prayer's kind of alert (the Next Prayer widget's chip). */
    fun alerts(): AlertSettings = settings.alerts

    /**
     * The Prayer Day running now (from midnight until Fajr, still the previous one), at the location, with its
     * Prayer Times (after the user's adjustments) and Sunrise, and whether a high-latitude rule was applied to them.
     * It moves on at Fajr, together with [currentPrayerDay].
     */
    fun displayDay(): DisplayDay {
        val now = clock.instant()
        val calendarDate = LocalDate.ofInstant(now, settings.location.zone)
        val calendarDay = calculateDay(settings, calendarDate)
        val beforeFajr = calendarDay.prayerTimes.first().time.toInstant().isAfter(now)
        val date = if (beforeFajr) calendarDate.minusDays(1) else calendarDate
        val day = if (beforeFajr) calculateDay(settings, date) else calendarDay
        val next = day.prayerTimes.firstOrNull { it.time.toInstant().isAfter(now) }
            ?: calculateDay(settings, date.plusDays(1)).prayerTimes.first()
        return DisplayDay(
            date = date, prayerTimes = day.prayerTimes, sunrise = day.sunrise, next = next,
            hijriDate = HijriDate.of(date, settings.hijriOffsetDays),
            highLatitude = day.highLatitude,
            previousIsha = calculateDay(settings, date.minusDays(1)).prayerTimes.last(),
        )
    }

    /**
     * Where [instant] falls in Ramadan Mode: fasting from a Fasting Day's Imsak to its Maghrib, suhoor from Maghrib to
     * the next Imsak when the next day is a Fasting Day (and before Imsak on a Fasting Day), otherwise none.
     */
    fun ramadanState(instant: Instant = clock.instant()): RamadanState {
        val date = LocalDate.ofInstant(instant, settings.location.zone)
        val today = fastingDay(date)
        return when {
            today != null && instant.isBefore(today.imsak.toInstant()) -> today.suhoor()
            today != null && instant.isBefore(today.maghrib.toInstant()) -> today
            !instant.isBefore(calculateDay(settings, date).at(Prayer.MAGHRIB).toInstant()) ->
                fastingDay(date.plusDays(1))?.suhoor() ?: RamadanState.None
            else -> RamadanState.None
        }
    }

    /** [date]'s fast, or null when it is not a Fasting Day. */
    private fun fastingDay(date: LocalDate): RamadanState.Fasting? {
        val hijri = HijriDate.of(date, settings.hijriOffsetDays)
        if (hijri.month != RAMADAN_MONTH) return null
        val day = calculateDay(settings, date)
        return RamadanState.Fasting(
            dayOfRamadan = hijri.day,
            imsak = day.at(Prayer.FAJR).minusMinutes(settings.alerts.ramadan.imsakMinutes.toLong()),
            maghrib = day.at(Prayer.MAGHRIB),
        )
    }

    private fun CalculatedDay.at(prayer: Prayer) = prayerTimes.single { it.prayer == prayer }.time

    private fun RamadanState.Fasting.suhoor() = RamadanState.Suhoor(dayOfRamadan, imsak, maghrib)

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

    /** Whether the Prayer Log holds any mark at all, on any Prayer Day. Today shows its log hint until it does. */
    fun hasAnyMark(): Boolean = log.hasAnyEntry()

    /** Marks [prayer] on the Prayer Day [date] as prayed, unless it isn't markable yet or the day is read-only. */
    fun mark(date: LocalDate, prayer: Prayer): LogCommandResult = setPrayed(date, prayer, true)

    /** Removes the mark from [prayer] on the Prayer Day [date], under the same rules as [mark]. */
    fun unmark(date: LocalDate, prayer: Prayer): LogCommandResult = setPrayed(date, prayer, false)

    /**
     * Every Adhan Alert due after now (exclusive, so an alarm firing now is not planned again) and within the
     * next [AlertPlan.WINDOW]. Sunrise never alerts. Each Prayer follows its [PrayerAlertSettings]; its sound is
     * the chosen recording's Custom Adhan when one is set ([AlertSettings.customAdhans]). With the master switch off
     * ([AlertSettings.enabled]) the plan holds no alerts at all; the display alarm still moves at every Prayer Time.
     * With Prayer Silence on ([AlertSettings.prayerSilence]) it also holds a Silence Window per chosen Prayer, master switch or not.
     * With the master switch on it holds the Worship Reminders switched on ([AlertSettings.worshipReminders]), and on
     * Fasting Days the Ramadan reminders switched on ([RamadanSettings]); Maghrib's Adhan Alert there is [PlannedAlert.isIftar].
     */
    fun alertPlan(): AlertPlan {
        val now = clock.instant()
        val end = now.plus(AlertPlan.WINDOW)
        val zone = settings.location.zone
        val today = LocalDate.ofInstant(now, zone)
        // Yesterday too: a Prayer Day is a calendar date's Fajr onwards, and the window spans at most 3 dates.
        val days = (-1L..2L).map { offset -> today.plusDays(offset).let { it to calculateDay(settings, it) } }
        val alertsOn = settings.alerts.enabled
        val alerts = if (!alertsOn) emptyList() else days.flatMap { (date, day) ->
            val fasting = fastingDay(date) != null
            day.prayerTimes.flatMap { time -> plannedAlerts(time, date, settings.alerts, iftar = fasting && time.prayer == Prayer.MAGHRIB) }
        }.filter { it.instant.isAfter(now) && !it.instant.isAfter(end) }.sortedBy { it.instant }
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        // Prayer Times no Adhan Alert announces still move the display day's next Prayer (widgets).
        val unannounced = days.flatMap { (_, day) -> day.prayerTimes }
            .filter { !alertsOn || !settings.alerts.of(it.prayer).adhanAlert }
            .map { it.time.toInstant() }
            .filter { it.isAfter(now) && it.isBefore(midnight) }
        return AlertPlan(
            alerts,
            midnight,
            unannounced.minOrNull() ?: midnight,
            silenceWindows(days, now, end),
            if (alertsOn) worshipReminders(days, now, end) else emptyList(),
        )
    }

    private fun worshipReminders(days: List<Pair<LocalDate, CalculatedDay>>, now: Instant, end: Instant): List<PlannedWorshipReminder> {
        val reminders = settings.alerts.worshipReminders
        return days.flatMap { (date, day) ->
            fun at(prayer: Prayer) = day.prayerTimes.single { it.prayer == prayer }.time.toInstant()
            salawatReminders(reminders.salawat, date, day.sunrise.toInstant(), at(Prayer.ISHA)) +
                kahfReminders(reminders.kahf, date, day.sunrise.toInstant()) + listOfNotNull(
                adhkarReminder(reminders.morningAdhkar, WorshipReminderKind.MORNING_ADHKAR, date, at(Prayer.FAJR)),
                adhkarReminder(reminders.eveningAdhkar, WorshipReminderKind.EVENING_ADHKAR, date, at(Prayer.ASR)),
            ) + ramadanReminders(date)
        }.filter { it.instant.isAfter(now) && !it.instant.isAfter(end) }.sortedBy { it.instant }
    }

    /** [date]'s suhoor reminder and Imsak alert, when they are on and [date] is a Fasting Day. */
    private fun ramadanReminders(date: LocalDate): List<PlannedWorshipReminder> {
        val ramadan = settings.alerts.ramadan
        if (!ramadan.suhoorReminder.enabled && !ramadan.imsakAlert) return emptyList()
        val fast = fastingDay(date) ?: return emptyList()
        val imsak = fast.imsak.toInstant()
        val fajr = imsak.plus(java.time.Duration.ofMinutes(ramadan.imsakMinutes.toLong()))
        return listOfNotNull(
            if (ramadan.suhoorReminder.enabled) {
                PlannedWorshipReminder(WorshipReminderKind.SUHOOR, fajr.minus(java.time.Duration.ofMinutes(ramadan.suhoorReminder.minutes.toLong())), date)
            } else {
                null
            },
            if (ramadan.imsakAlert) PlannedWorshipReminder(WorshipReminderKind.IMSAK, imsak, date) else null,
        )
    }

    private fun silenceWindows(days: List<Pair<LocalDate, CalculatedDay>>, now: Instant, end: Instant): List<SilenceWindow> {
        val silence = settings.alerts.prayerSilence
        if (!silence.enabled) return emptyList()
        return days.flatMap { (date, day) ->
            day.prayerTimes.filter { silence.isOn(it.prayer) }.map { time ->
                val start = time.time.toInstant()
                SilenceWindow(
                    prayer = time.prayer,
                    prayerDay = date,
                    start = start,
                    end = start.plus(silence.duration(time.isJumuah)),
                    mode = silence.mode,
                    isJumuah = time.isJumuah,
                )
            }
        }.filter { it.end.isAfter(now) && !it.start.isAfter(end) }.sortedBy { it.start }
    }

    private fun plannedAlerts(time: PrayerTime, prayerDay: LocalDate, alerts: AlertSettings, iftar: Boolean): List<PlannedAlert> {
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
            adhanVolume = alerts.adhanVolume,
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
                .copy(isIftar = iftar)
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

    internal companion object {
        /** Today plus the 6 previous Prayer Days. */
        private const val EDIT_WINDOW_DAYS = 7L

        /** A Prayer Week's length (the Adhkar Record week's too). */
        const val WEEK_DAYS = 7L
    }
}
