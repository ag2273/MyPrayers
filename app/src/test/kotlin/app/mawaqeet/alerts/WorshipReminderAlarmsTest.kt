package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MainActivity
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.WorshipReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate

/** Worship Reminders (#63): their alarms, the stored plan, and the gentle notification on «تذكيرات العبادة». */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WorshipReminderAlarmsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val day = LocalDate.parse("2026-11-08")
    private fun salawat(at: String, slot: Int) = PlannedWorshipReminder(WorshipReminderKind.SALAWAT, Instant.parse(at), day, slot)
    private val first = salawat("2026-11-08T05:27:00Z", 0)
    private val second = salawat("2026-11-08T07:27:00Z", 1)
    private val midnight = Instant.parse("2026-11-08T21:00:00Z")
    private val early = Instant.parse("2026-11-08T03:00:00Z")

    private fun reminderAlarms() = alarms.scheduledAlarms
        .filter { shadowOf(it.operation).savedIntent.action == WorshipReminderReceiver.ACTION_REMIND }
        .map { Instant.ofEpochMilli(it.triggerAtTime) }
        .sorted()

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `each Worship Reminder gets its own exact alarm, even with no alert planned`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first, second)), early)

        assertEquals(listOf(first.instant, second.instant), reminderAlarms())
        assertTrue(alarms.scheduledAlarms.all { it.allowWhileIdle && it.alarmClockInfo == null })
    }

    @Test
    fun `turning salawat off cancels its alarms`() {
        val scheduler = AlertScheduler(context)
        scheduler.apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first, second)), early)

        scheduler.apply(AlertPlan(emptyList(), midnight), early)

        assertEquals(emptyList<Instant>(), reminderAlarms())
    }

    @Test
    fun `Worship Reminders survive the stored plan, so a reboot re-arms the ones still ahead`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first, second)), early)
        assertEquals(listOf(first, second), AlertPlanStore(context).read()!!.worshipReminders)
        alarms.scheduledAlarms.toList().forEach { context.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }

        AlertScheduler(context).rearmStoredPlan(now = Instant.parse("2026-11-08T06:00:00Z"))

        assertEquals(listOf(second.instant), reminderAlarms())
    }

    private fun fire(reminder: PlannedWorshipReminder) {
        val alarm = alarms.scheduledAlarms.first { Instant.ofEpochMilli(it.triggerAtTime) == reminder.instant }
        val intent: Intent = shadowOf(alarm.operation).savedIntent
        WorshipReminderReceiver().onReceive(context, intent)
        kotlinx.coroutines.runBlocking { WorshipReminderReceiver.idle() }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `a salawat reminder is a gentle notification on the Worship Reminders channel, with the default sound, that opens the app`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first)), early)

        fire(first)

        val notification: Notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("صلِّ على النبي ﷺ", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(WorshipReminderNotification.CHANNEL, notification.channelId)
        val channel = notificationManager.getNotificationChannel(WorshipReminderNotification.CHANNEL)
        assertEquals("تذكيرات العبادة", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, channel.sound)
        val tap = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component!!.className)
    }

    @Test
    fun `in English the reminder and its channel read in English`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first)), early)

        fire(first)

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Send salawat on the Prophet ﷺ", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Worship reminders", notificationManager.getNotificationChannel(WorshipReminderNotification.CHANNEL).name.toString())
    }

    @Test
    fun `a new salawat reminder replaces the last one, so they never pile up`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first, second)), early)

        fire(first)
        fire(second)

        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }
}
