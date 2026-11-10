package app.mawaqeet.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AlertSettingsTest {
    @Test
    fun `a 5-minute Prayer Reminder is rejected`() {
        assertFailsWith<IllegalArgumentException> { PrayerAlertSettings(reminderMinutes = 5) }
        assertFailsWith<IllegalArgumentException> { PrayerAlertSettings(reminderMinutes = 9) }
    }

    @Test
    fun `a 10-minute Prayer Reminder is accepted`() {
        assertEquals(10, PrayerAlertSettings(reminder = true, reminderMinutes = 10).reminderMinutes)
    }

    @Test
    fun `by default every Prayer has an Adhan Alert with the Short Adhan and no Prayer Reminder, 15 minutes before once enabled`() {
        val defaults = AlertSettings()
        for (prayer in Prayer.entries) {
            assertEquals(PrayerAlertSettings(adhanAlert = true, sound = AdhanSoundChoice.SHORT, reminder = false, reminderMinutes = 15), defaults.of(prayer))
        }
        assertEquals(false, defaults.alarmClockMode)
        assertEquals(false, defaults.playOnSilent)
        assertEquals(true, defaults.vibration)
        assertEquals(AdhanVolume.ALARM, defaults.adhanVolume)
        assertEquals(defaults, PrayerSettings.Default.alerts)
    }

    @Test
    fun `by default no Custom Adhan is chosen, so every recording is the bundled one`() {
        val defaults = AlertSettings()
        for (recording in AdhanRecording.entries) {
            assertEquals(null, defaults.customAdhan(recording))
            assertEquals(AlertSound.bundled(recording), AlertSound.of(recording, defaults))
        }
    }

    @Test
    fun `a Custom Adhan for the Full Adhan leaves the Short Adhan and Fajr's Full Adhan unchanged`() {
        val alerts = AlertSettings().withCustomAdhan(AdhanRecording.FULL, "content://reciter/full")
        assertEquals("content://reciter/full", alerts.customAdhan(AdhanRecording.FULL))
        assertEquals(null, alerts.customAdhan(AdhanRecording.SHORT))
        assertEquals(null, alerts.customAdhan(AdhanRecording.FAJR_FULL))
    }

    @Test
    fun `the Full Adhan choice on Fajr is Fajr's own recording`() {
        assertEquals(AdhanRecording.FAJR_FULL, AdhanRecording.of(Prayer.FAJR, AdhanSoundChoice.FULL))
        assertEquals(AdhanRecording.FULL, AdhanRecording.of(Prayer.DHUHR, AdhanSoundChoice.FULL))
        assertEquals(AdhanRecording.SHORT, AdhanRecording.of(Prayer.FAJR, AdhanSoundChoice.SHORT))
        assertEquals(null, AdhanRecording.of(Prayer.ISHA, AdhanSoundChoice.SILENT))
    }

    @Test
    fun `an unreadable Custom Adhan falls back to the bundled recording it replaces`() {
        assertEquals(AlertSound.ShortAdhan, AlertSound.CustomAdhan("content://gone", AdhanRecording.SHORT).fallback)
        assertEquals(AlertSound.FullAdhan, AlertSound.CustomAdhan("content://gone", AdhanRecording.FULL).fallback)
        assertEquals(AlertSound.FajrFullAdhan, AlertSound.CustomAdhan("content://gone", AdhanRecording.FAJR_FULL).fallback)
    }
}
