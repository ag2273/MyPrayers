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
        assertEquals(defaults, PrayerSettings.Default.alerts)
    }
}
