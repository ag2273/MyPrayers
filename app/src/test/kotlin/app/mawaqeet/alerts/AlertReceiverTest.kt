package app.mawaqeet.alerts

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AlertReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val app get() = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
    private val vibrator: Vibrator get() = context.getSystemService(VibratorManager::class.java).defaultVibrator

    @Before
    fun allowAlerts() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun alert(
        sound: AlertSound,
        kind: AlertKind = AlertKind.ADHAN_ALERT,
        playOnSilent: Boolean = false,
        vibration: Boolean = true,
    ): PlannedAlert {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        return PlannedAlert(
            instant = now,
            prayer = Prayer.ASR,
            kind = kind,
            sound = sound,
            deliveryMode = DeliveryMode.EXACT,
            prayerDay = LocalDate.parse("2026-11-08"),
            isJumuah = false,
            prayerTime = if (kind == AlertKind.PRAYER_REMINDER) now.plusSeconds(15 * 60) else now,
            playOnSilent = playOnSilent,
            vibration = vibration,
        )
    }

    /**
     * Delivers the very intent the scheduler's alarm for [alert] fires (built directly: the app's own rescheduler
     * runs on another thread here and would race an alarm set by the test).
     */
    private fun fire(alert: PlannedAlert) {
        context.sendBroadcast(AlertScheduler(context).alarmIntent(alert))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `with the ringer on, the Adhan Alert starts the adhan player with the alert's sound`() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        fire(alert(AlertSound.FullAdhan))

        val started = app.nextStartedService
        assertEquals(AdhanPlayerService::class.java.name, started.component?.className)
        assertEquals("FULL_ADHAN", started.getStringExtra(AlertReceiver.EXTRA_SOUND))
    }

    @Test
    fun `in silent mode no player starts but the notification is still posted`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        fire(alert(AlertSound.ShortAdhan))

        assertNull(app.nextStartedService)
        assertTrue(notifications.isNotEmpty())
    }

    @Test
    fun `with play-on-silent the adhan plays in silent mode`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        fire(alert(AlertSound.ShortAdhan, playOnSilent = true))

        assertEquals(AdhanPlayerService::class.java.name, app.nextStartedService?.component?.className)
    }

    @Test
    fun `the vibration toggle decides whether the phone vibrates`() {
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        fire(alert(AlertSound.ShortAdhan, vibration = false))
        assertFalse(shadowOf(vibrator).isVibrating)

        fire(alert(AlertSound.ShortAdhan, vibration = true))
        assertTrue(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun `a Prayer Reminder posts its notification on the reminder channel and never starts the adhan player`() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        fire(alert(AlertSound.ReminderTone, AlertKind.PRAYER_REMINDER))

        assertNull(app.nextStartedService)
        assertEquals(AdhanNotification.CHANNEL_REMINDERS, notifications.single().channelId)
    }
}
