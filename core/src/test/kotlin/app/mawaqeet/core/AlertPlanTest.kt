package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertPlanTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun planAt(time: ZonedDateTime, settings: PrayerSettings = PrayerSettings.Default): AlertPlan =
        PrayerCore(settings, Clock.fixed(time.toInstant(), ZoneId.of("UTC")), InMemoryPrayerLog()).alertPlan()

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun AlertPlan.localMinutes(zone: ZoneId) =
        alerts.map { it.instant.atZone(zone).truncatedTo(ChronoUnit.MINUTES).toLocalDateTime().toString() }

    @Test
    fun `before Dhuhr the plan holds the next 48 hours of Adhan Alerts, in order, without Sunrise`() {
        // Makkah, Umm al-Qura, 2026-11-08: Fajr 05:09, Sunrise 06:27, Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11.
        val plan = planAt(makkah("2026-11-08", "12:00"))

        assertEquals(10, plan.alerts.size)
        assertEquals(
            listOf(
                Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                Prayer.FAJR,
            ),
            plan.alerts.map { it.prayer },
        )
        assertEquals(
            listOf("2026-11-08T12:04", "2026-11-08T15:17", "2026-11-08T17:41", "2026-11-08T19:11"),
            plan.localMinutes(riyadh).take(4),
        )
        // The window ends 48 h from now: the Fajr of 10 November is in, its Dhuhr (~12:04) is not.
        assertEquals(LocalDate.parse("2026-11-10"), plan.alerts.last().instant.atZone(riyadh).toLocalDate())
        assertTrue(plan.alerts.none { it.instant.atZone(riyadh).toLocalTime().hour == 6 }, "no alert at Sunrise")
    }

    @Test
    fun `by default every Prayer has a Short Adhan Adhan Alert delivered as an exact alarm`() {
        val plan = planAt(makkah("2026-11-08", "12:00"))

        assertTrue(plan.alerts.all { it.kind == AlertKind.ADHAN_ALERT })
        assertTrue(plan.alerts.all { it.sound == AlertSound.ShortAdhan })
        assertTrue(plan.alerts.all { it.deliveryMode == DeliveryMode.EXACT })
    }

    @Test
    fun `after Isha the plan starts at tomorrow's Fajr and the next midnight is the coming local midnight`() {
        val plan = planAt(makkah("2026-11-08", "20:00"))

        assertEquals(10, plan.alerts.size)
        assertEquals(Prayer.FAJR, plan.alerts.first().prayer)
        assertEquals(LocalDate.parse("2026-11-09"), plan.alerts.first().prayerDay)
        assertEquals(LocalDate.parse("2026-11-10"), plan.alerts.last().prayerDay)
        assertEquals(Prayer.ISHA, plan.alerts.last().prayer)
        assertEquals(makkah("2026-11-09", "00:00").toInstant(), plan.nextLocalMidnight)
    }

    @Test
    fun `an alert due exactly now is not planned again, so a firing alarm moves on to the next one`() {
        val dhuhr = planAt(makkah("2026-11-08", "12:00")).alerts.first()

        val plan = planAt(dhuhr.instant.atZone(riyadh))

        assertEquals(Prayer.ASR, plan.alerts.first().prayer)
        assertTrue(plan.alerts.none { it.instant == dhuhr.instant })
    }

    @Test
    fun `Friday's Dhuhr alert is announced as Jumu'ah`() {
        // 2026-11-13 is a Friday.
        val plan = planAt(makkah("2026-11-12", "20:00"))

        val jumuah = plan.alerts.filter { it.isJumuah }
        assertEquals(1, jumuah.size)
        assertEquals(Prayer.DHUHR, jumuah.single().prayer)
        assertEquals(LocalDate.parse("2026-11-13"), jumuah.single().prayerDay)
    }

    @Test
    fun `across the end of summer time alerts keep following the sun, so their clock times move an hour earlier`() {
        // London leaves BST (UTC+1) for GMT at 01:00 UTC on Sunday 2026-10-25.
        val london = ZoneId.of("Europe/London")
        val settings = PrayerSettings.Default.copy(
            location = Location(51.5074, -0.1278, london),
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE,
        )
        val plan = planAt(ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, london), settings)

        assertEquals(10, plan.alerts.size)
        val saturday = plan.alerts.filter { it.prayerDay == LocalDate.parse("2026-10-24") }.associateBy { it.prayer }
        val sunday = plan.alerts.filter { it.prayerDay == LocalDate.parse("2026-10-25") }.associateBy { it.prayer }
        assertEquals(5, sunday.size)
        for ((prayer, alert) in saturday) {
            val next = sunday.getValue(prayer)
            // The sun moves a few minutes a day; the clocks move 60.
            val clockShift = Duration.between(next.instant.atZone(london).toLocalTime(), alert.instant.atZone(london).toLocalTime())
            assertTrue(clockShift.toMinutes() in 55..65, "$prayer clock time moved by $clockShift")
            val realGap = Duration.between(alert.instant, next.instant)
            assertTrue((realGap.toMinutes() - 24 * 60) in -5..5, "$prayer is $realGap after the previous day's")
        }
        // Midnight is local: 2026-10-25T00:00 BST.
        assertEquals(Instant.parse("2026-10-24T23:00:00Z"), plan.nextLocalMidnight)
    }

    private fun withAlerts(alerts: AlertSettings) = PrayerSettings.Default.copy(alerts = alerts)

    @Test
    fun `per-Prayer sounds, a turned-off Adhan Alert and Prayer Reminders all show in the 48-hour plan`() {
        val alerts = AlertSettings()
            .with(Prayer.FAJR, PrayerAlertSettings(sound = AdhanSoundChoice.FULL))
            .with(Prayer.DHUHR, PrayerAlertSettings(sound = AdhanSoundChoice.FULL, reminder = true))
            .with(Prayer.ASR, PrayerAlertSettings(sound = AdhanSoundChoice.SILENT))
            .with(Prayer.MAGHRIB, PrayerAlertSettings(adhanAlert = false, reminder = true, reminderMinutes = 10))
        // Makkah 2026-11-08 11:00: Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11; Fajr 05:09 next day.
        val plan = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts))

        val today = plan.alerts.filter { it.prayerDay == LocalDate.parse("2026-11-08") }.map {
            Triple(it.instant.atZone(riyadh).truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString(), it.prayer, it.kind)
        }
        assertEquals(
            listOf(
                Triple("11:49", Prayer.DHUHR, AlertKind.PRAYER_REMINDER), // 15 minutes, the default lead time.
                Triple("12:04", Prayer.DHUHR, AlertKind.ADHAN_ALERT),
                Triple("15:17", Prayer.ASR, AlertKind.ADHAN_ALERT),
                Triple("17:31", Prayer.MAGHRIB, AlertKind.PRAYER_REMINDER), // Its Adhan Alert is off.
                Triple("19:11", Prayer.ISHA, AlertKind.ADHAN_ALERT),
            ),
            today,
        )
        assertEquals(
            listOf(Prayer.FAJR to AlertKind.ADHAN_ALERT, Prayer.DHUHR to AlertKind.PRAYER_REMINDER, Prayer.DHUHR to AlertKind.ADHAN_ALERT),
            plan.alerts.drop(5).take(3).map { it.prayer to it.kind },
        )
        // 8 Nov: 5 alerts from 11:49; 9 Nov: Fajr + those 5; 10 Nov: Fajr (the window ends at 11:00).
        assertEquals(12, plan.alerts.size)
        val adhans = plan.alerts.filter { it.kind == AlertKind.ADHAN_ALERT }.associate { it.prayer to it.sound }
        assertEquals(AlertSound.FajrFullAdhan, adhans[Prayer.FAJR])
        assertEquals(AlertSound.FullAdhan, adhans[Prayer.DHUHR])
        assertEquals(AlertSound.Silent, adhans[Prayer.ASR])
        assertEquals(AlertSound.ShortAdhan, adhans[Prayer.ISHA])
        assertTrue(Prayer.MAGHRIB !in adhans)
        val reminders = plan.alerts.filter { it.kind == AlertKind.PRAYER_REMINDER }
        assertTrue(reminders.all { it.sound == AlertSound.ReminderTone }, "reminders use the short channel sound only")
        assertTrue(reminders.all { it.deliveryMode == DeliveryMode.EXACT })
        val dhuhrReminder = reminders.first()
        assertEquals(plan.alerts[1].instant, dhuhrReminder.prayerTime)
        assertEquals(LocalDate.parse("2026-11-08"), dhuhrReminder.prayerDay)
    }

    @Test
    fun `with every alert turned off the plan holds no Adhan Alert and no Prayer Reminder, and the display moves at each Prayer Time`() {
        val alerts = AlertSettings(enabled = false)
            .with(Prayer.DHUHR, PrayerAlertSettings(sound = AdhanSoundChoice.FULL, reminder = true))
        val plan = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts))

        assertTrue(plan.alerts.isEmpty())
        // Dhuhr 12:04 is the next display change: no Adhan Alert announces it any more.
        assertEquals("12:04", plan.nextDisplayChange.atZone(riyadh).truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString())
        // Turning them back on restores each Prayer's own settings.
        val again = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts.copy(enabled = true)))
        assertEquals(AlertKind.PRAYER_REMINDER, again.alerts.first().kind)
    }

    private val everyPrayerFull = Prayer.entries.fold(AlertSettings()) { a, p -> a.with(p, PrayerAlertSettings(sound = AdhanSoundChoice.FULL)) }

    private fun AlertPlan.adhanSounds() = alerts.filter { it.kind == AlertKind.ADHAN_ALERT }.associate { it.prayer to it.sound }

    @Test
    fun `a Custom Full Adhan plays at Dhuhr, Asr, Maghrib and Isha but Fajr keeps its own adhan with «الصلاة خير من النوم»`() {
        val alerts = everyPrayerFull.withCustomAdhan(AdhanRecording.FULL, "content://reciter/full")
        val sounds = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts)).adhanSounds()

        assertEquals(AlertSound.FajrFullAdhan, sounds[Prayer.FAJR])
        for (prayer in listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)) {
            assertEquals(AlertSound.CustomAdhan("content://reciter/full", AdhanRecording.FULL), sounds[prayer])
        }
    }

    @Test
    fun `Fajr's Full Adhan is replaced only by a Custom Adhan chosen for Fajr itself`() {
        val alerts = everyPrayerFull
            .withCustomAdhan(AdhanRecording.FULL, "content://reciter/full")
            .withCustomAdhan(AdhanRecording.FAJR_FULL, "content://reciter/fajr")
        val sounds = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts)).adhanSounds()

        assertEquals(AlertSound.CustomAdhan("content://reciter/fajr", AdhanRecording.FAJR_FULL), sounds[Prayer.FAJR])
        assertEquals(AlertSound.CustomAdhan("content://reciter/full", AdhanRecording.FULL), sounds[Prayer.DHUHR])
    }

    @Test
    fun `a Custom Short Adhan plays only where the Short Adhan is chosen, Fajr included`() {
        val alerts = AlertSettings()
            .with(Prayer.ASR, PrayerAlertSettings(sound = AdhanSoundChoice.FULL))
            .with(Prayer.ISHA, PrayerAlertSettings(sound = AdhanSoundChoice.SILENT))
            .withCustomAdhan(AdhanRecording.SHORT, "content://reciter/short")
        val sounds = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts)).adhanSounds()

        val custom = AlertSound.CustomAdhan("content://reciter/short", AdhanRecording.SHORT)
        assertEquals(custom, sounds[Prayer.FAJR])
        assertEquals(custom, sounds[Prayer.DHUHR])
        assertEquals(custom, sounds[Prayer.MAGHRIB])
        assertEquals(AlertSound.FullAdhan, sounds[Prayer.ASR])
        assertEquals(AlertSound.Silent, sounds[Prayer.ISHA])
    }

    @Test
    fun `clearing a Custom Adhan brings back the bundled recording`() {
        val alerts = everyPrayerFull
            .withCustomAdhan(AdhanRecording.FULL, "content://reciter/full")
            .withCustomAdhan(AdhanRecording.FULL, null)
        val sounds = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts)).adhanSounds()

        assertEquals(AlertSound.FullAdhan, sounds[Prayer.DHUHR])
        assertEquals(AlertSound.FajrFullAdhan, sounds[Prayer.FAJR])
    }

    @Test
    fun `a reminder already past is left out while its Prayer's Adhan Alert is still planned`() {
        val alerts = AlertSettings().with(Prayer.DHUHR, PrayerAlertSettings(reminder = true))
        // 11:55 is after Dhuhr's reminder (11:49) but before Dhuhr (12:04).
        val plan = planAt(makkah("2026-11-08", "11:55"), withAlerts(alerts))

        assertEquals(Prayer.DHUHR, plan.alerts.first().prayer)
        assertEquals(AlertKind.ADHAN_ALERT, plan.alerts.first().kind)
    }

    @Test
    fun `alarm-clock mode delivers Adhan Alerts as alarm clocks`() {
        val alerts = AlertSettings(alarmClockMode = true).with(Prayer.DHUHR, PrayerAlertSettings(reminder = true))
        val plan = planAt(makkah("2026-11-08", "11:00"), withAlerts(alerts))

        assertTrue(plan.alerts.filter { it.kind == AlertKind.ADHAN_ALERT }.all { it.deliveryMode == DeliveryMode.ALARM_CLOCK })
        // The lock screen shows the next Prayer, not a reminder before it.
        assertTrue(plan.alerts.filter { it.kind == AlertKind.PRAYER_REMINDER }.all { it.deliveryMode == DeliveryMode.EXACT })
    }

    @Test
    fun `play-on-silent and vibration travel with every planned alert, for the receiver before the first unlock`() {
        val plan = planAt(makkah("2026-11-08", "11:00"), withAlerts(AlertSettings(playOnSilent = true, vibration = false)))
        assertTrue(plan.alerts.all { it.playOnSilent && !it.vibration })

        val defaults = planAt(makkah("2026-11-08", "11:00"))
        assertTrue(defaults.alerts.all { !it.playOnSilent && it.vibration })
    }

    @Test
    fun `the adhan volume choice travels with every planned alert, alarm volume by default`() {
        val media = planAt(makkah("2026-11-08", "11:00"), withAlerts(AlertSettings(adhanVolume = AdhanVolume.MEDIA)))
        assertTrue(media.alerts.all { it.adhanVolume == AdhanVolume.MEDIA })

        assertTrue(planAt(makkah("2026-11-08", "11:00")).alerts.all { it.adhanVolume == AdhanVolume.ALARM })
    }

    @Test
    fun `with every Adhan Alert on, the display changes next at local midnight, since each Prayer Time already has an alarm`() {
        val plan = planAt(makkah("2026-11-08", "12:00"))

        assertEquals(makkah("2026-11-09", "00:00").toInstant(), plan.nextDisplayChange)
    }

    @Test
    fun `a Prayer Time without an Adhan Alert is the next display change, so widgets still move on to the next Prayer`() {
        val alerts = AlertSettings().with(Prayer.ASR, PrayerAlertSettings(adhanAlert = false, reminder = true))
        // Makkah 2026-11-08: Asr 15:17 (its reminder at 15:02 is not at the Prayer Time).
        val plan = planAt(makkah("2026-11-08", "13:00"), withAlerts(alerts))

        assertEquals(
            makkah("2026-11-08", "15:17"),
            plan.nextDisplayChange.atZone(riyadh).truncatedTo(ChronoUnit.MINUTES),
        )
    }

    @Test
    fun `after the last Prayer of the day without an alarm passes, the next display change is midnight again`() {
        val alerts = AlertSettings().with(Prayer.ASR, PrayerAlertSettings(adhanAlert = false))
        val plan = planAt(makkah("2026-11-08", "16:00"), withAlerts(alerts))

        assertEquals(makkah("2026-11-09", "00:00").toInstant(), plan.nextDisplayChange)
    }
}
