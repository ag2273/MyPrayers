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
import java.time.Clock
import java.util.concurrent.Executors
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerSettings

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
        prayer: Prayer = Prayer.ASR,
        prayerDay: LocalDate = LocalDate.parse("2026-11-08"),
    ): PlannedAlert {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        return PlannedAlert(
            instant = now,
            prayer = prayer,
            kind = kind,
            sound = sound,
            deliveryMode = DeliveryMode.EXACT,
            prayerDay = prayerDay,
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

    private val prayerLog get() = (context.applicationContext as MawaqeetApplication).prayerLog

    private fun <T> io(block: () -> T): T = Executors.newSingleThreadExecutor().let { e ->
        try { e.submit<T> { block() }.get() } finally { e.shutdown() }
    }

    /** The current Prayer Day in Makkah (the default location) by the real clock, with its Fajr already due. */
    private fun today(): LocalDate = io { PrayerCore(PrayerSettings.Default, Clock.systemUTC(), prayerLog).currentPrayerDay().date }

    private fun markAction(n: android.app.Notification) = n.actions.single { it.title.toString() == "Mark as prayed" }

    /** Taps [action] and waits for the receiver's background mark to land. */
    private fun tap(action: android.app.Notification.Action, until: () -> Boolean) {
        action.actionIntent.send()
        val deadline = System.currentTimeMillis() + 5_000
        while (!until() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `an Adhan Alert offers Mark as prayed, which marks its Prayer Day and dismisses the notification`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        val day = today()
        fire(alert(AlertSound.ShortAdhan, prayer = Prayer.FAJR, prayerDay = day))
        val posted = notifications.single()

        tap(markAction(posted)) { io { Prayer.FAJR in prayerLog.prayedOn(day) } }

        assertEquals(setOf(Prayer.FAJR), io { prayerLog.prayedOn(day) })
        assertTrue(notifications.isEmpty())
    }

    @Test
    @Config(qualifiers = "en")
    fun `Mark as prayed acted on hours later marks the alert's own Prayer Day, such as last night's Isha`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        val yesterday = today().minusDays(1)
        fire(alert(AlertSound.ShortAdhan, prayer = Prayer.ISHA, prayerDay = yesterday))

        tap(markAction(notifications.single())) { io { Prayer.ISHA in prayerLog.prayedOn(yesterday) } }

        assertEquals(setOf(Prayer.ISHA), io { prayerLog.prayedOn(yesterday) })
        assertEquals(emptySet<Prayer>(), io { prayerLog.prayedOn(today()) })
    }

    @Test
    @Config(qualifiers = "en")
    fun `outside the edit window Mark as prayed changes nothing but still dismisses`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        val old = today().minusDays(10)
        fire(alert(AlertSound.ShortAdhan, prayer = Prayer.DHUHR, prayerDay = old))

        tap(markAction(notifications.single())) { notifications.isEmpty() }
        Thread.sleep(200)

        assertTrue(notifications.isEmpty())
        assertEquals(emptySet<Prayer>(), io { prayerLog.prayedOn(old) })
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the action reads in Arabic too, and the adhan player's notification has it next to Stop`() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val n = AdhanNotification(Prayer.MAGHRIB, isJumuah = false, id = 7, prayerDay = today())

        val titles = n.builder(context).addAction(0, "إيقاف", null).build().actions.map { it.title.toString() }

        assertEquals(listOf("تمّت الصلاة", "إيقاف"), titles)
    }

    @Test
    fun `a Prayer Reminder has no Mark as prayed`() {
        val n = AdhanNotification(Prayer.MAGHRIB, isJumuah = false, id = 7, kind = AlertKind.PRAYER_REMINDER, prayerDay = today())

        assertNull(n.builder(context).build().actions)
    }
}
