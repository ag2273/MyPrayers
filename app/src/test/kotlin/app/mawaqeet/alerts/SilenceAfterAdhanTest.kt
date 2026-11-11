package app.mawaqeet.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Prayer Silence starts once the adhan has stopped, or at the Prayer Time when there is no adhan to play, and a reboot
 * never leaves the phone silenced past the window's planned end (#69).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SilenceAfterAdhanTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val app get() = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
    private val posted: List<Notification> get() = shadowOf(notificationManager).allNotifications

    private val now: Instant = Instant.ofEpochMilli(System.currentTimeMillis())
    private val day = LocalDate.parse("2026-11-08")

    private val window = SilenceWindow(
        prayer = Prayer.DHUHR,
        prayerDay = day,
        start = now,
        end = now.plus(Duration.ofMinutes(20)),
        mode = SilenceMode.SILENT,
        isJumuah = false,
    )

    private fun adhanAlert(sound: AlertSound) = PlannedAlert(
        instant = now,
        prayer = Prayer.DHUHR,
        kind = AlertKind.ADHAN_ALERT,
        sound = sound,
        deliveryMode = DeliveryMode.EXACT,
        prayerDay = day,
        isJumuah = false,
    )

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    /** The alarm for [alert] fires, opening [window]. */
    private fun fire(alert: PlannedAlert) {
        context.sendBroadcast(AlertScheduler(context).alarmIntent(alert, window))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The adhan stops playing: it finished, or the user pressed Stop. */
    private fun adhanStops() {
        Robolectric.buildService(AdhanPlayerService::class.java, Intent(context, AdhanPlayerService::class.java).setAction(AdhanPlayerService.ACTION_STOP))
            .create()
            .startCommand(0, 1)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitForRinger(mode: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (audio.ringerMode != mode && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertEquals(mode, audio.ringerMode)
    }

    @Test
    fun `while the adhan plays the phone keeps its sound, and goes silent once the adhan stops`() {
        fire(adhanAlert(AlertSound.FullAdhan))
        assertEquals(AdhanPlayerService::class.java.name, app.nextStartedService?.component?.className)
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)

        adhanStops()

        waitForRinger(AudioManager.RINGER_MODE_SILENT)
    }

    @Test
    fun `with a silent Adhan Alert there is no adhan to wait for, so the phone goes silent at the Prayer Time`() {
        fire(adhanAlert(AlertSound.Silent))

        waitForRinger(AudioManager.RINGER_MODE_SILENT)
    }

    @Test
    fun `an adhan that stops after the window's planned end silences nothing`() {
        fire(adhanAlert(AlertSound.FullAdhan))
        PrayerSilencer(context).end(window)

        adhanStops()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `after a reboot during the window the phone stays silent with its notification, and the end restores the sound`() {
        PrayerSilencer(context).start(window)
        notificationManager.cancelAll()

        PrayerSilencer(context).catchUp(now.plus(Duration.ofMinutes(5)))

        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(1, posted.size)
        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `a reboot after the window's planned end restores the sound at boot`() {
        PrayerSilencer(context).start(window)

        PrayerSilencer(context).catchUp(window.end.plus(Duration.ofMinutes(30)))

        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `the boot broadcast before the first unlock restores a window that ended while the phone was off`() {
        val over = window.copy(start = now.minus(Duration.ofHours(2)), end = now.minus(Duration.ofHours(1)))
        PrayerSilencer(context).start(over)

        context.sendBroadcast(Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).setPackage(context.packageName))
        waitForRinger(AudioManager.RINGER_MODE_NORMAL)
    }

    @Test
    fun `a reboot while the adhan plays silences the phone at boot, since the adhan never replays`() {
        fire(adhanAlert(AlertSound.FullAdhan))

        PrayerSilencer(context).catchUp(now.plus(Duration.ofMinutes(2)))

        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `the previous ringer mode is kept in device-protected storage`() {
        PrayerSilencer(context).start(window)

        val record = context.createDeviceProtectedStorageContext().getSharedPreferences("prayer_silence", Context.MODE_PRIVATE)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, record.getInt("previous_ringer_mode", -1))
    }
}
