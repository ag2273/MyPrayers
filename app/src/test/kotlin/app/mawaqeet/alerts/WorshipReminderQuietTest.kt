package app.mawaqeet.alerts

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import app.mawaqeet.core.WorshipReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Worship Reminders stay quiet during Prayer Silence (#76): posted without sound or vibration inside a Silence Window. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WorshipReminderQuietTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val now = Instant.now()
    private val today = LocalDate.ofInstant(now, ZoneOffset.UTC)

    private fun window(start: Instant, end: Instant) =
        SilenceWindow(Prayer.DHUHR, today, start, end, SilenceMode.SILENT, isJumuah = false)

    private fun reminder(kind: WorshipReminderKind) = PlannedWorshipReminder(kind, now, today, slot = 0)

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    private fun fire(kind: WorshipReminderKind): Notification {
        WorshipReminderReceiver().onReceive(context, WorshipReminderReceiver.intent(context, reminder(kind)))
        // Adhkar reminders post after their completeness check, off the main thread (#75).
        kotlinx.coroutines.runBlocking { WorshipReminderReceiver.idle() }
        return shadowOf(notifications).allNotifications.single { it.channelId == WorshipReminderNotification.CHANNEL }
    }

    /**
     * Android never lets a notification alert (sound or vibration) when it is a child of a group whose alerts go to the
     * summary only, and no summary is posted.
     */
    private fun Notification.isSilent(): Boolean =
        group != null && groupAlertBehavior == Notification.GROUP_ALERT_SUMMARY && flags and Notification.FLAG_GROUP_SUMMARY == 0

    @Test
    fun `during an active Silence Window every Worship Reminder kind is posted silently`() {
        PrayerSilencer(context).start(window(now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(15))))

        for (kind in WorshipReminderKind.entries) {
            assertTrue("$kind should be silent", fire(kind).isSilent())
            notifications.cancelAll()
        }
    }

    @Test
    fun `with no Silence Window every Worship Reminder kind sounds as normal`() {
        for (kind in WorshipReminderKind.entries) {
            val notification = fire(kind)
            assertFalse("$kind should sound", notification.isSilent())
            assertEquals(WorshipReminderNotification.CHANNEL, notification.channelId)
            notifications.cancelAll()
        }
    }

    @Test
    fun `a window whose end has passed no longer quiets reminders`() {
        PrayerSilencer(context).start(window(now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofMinutes(1))))

        for (kind in WorshipReminderKind.entries) {
            assertFalse("$kind should sound", fire(kind).isSilent())
            notifications.cancelAll()
        }
    }

    @Test
    fun `isActive is true only between a started window's start and its end`() {
        val silencer = PrayerSilencer(context)
        assertFalse(silencer.isActive(now))

        silencer.start(window(now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(15))))

        assertTrue(silencer.isActive(now))
        assertFalse(silencer.isActive(now.plus(Duration.ofMinutes(15))))
    }
}
