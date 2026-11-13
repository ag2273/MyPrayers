package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.WorshipReminderKind
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate

/**
 * Ramadan alerts (#91): Maghrib's Adhan Alert on a Fasting Day reads «حان وقت الإفطار», and the suhoor reminder and
 * Imsak alert are gentle Worship Reminder notifications. A plain Application, so the app's own scheduler adds nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class RamadanAlertsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifications get() = shadowOf(notificationManager).allNotifications

    private val day = LocalDate.parse("2027-02-22")
    private val midnight = Instant.parse("2027-02-22T21:00:00Z")
    private val early = Instant.parse("2027-02-22T00:00:00Z")
    private val suhoor = PlannedWorshipReminder(WorshipReminderKind.SUHOOR, Instant.parse("2027-02-22T01:30:00Z"), day)
    private val imsak = PlannedWorshipReminder(WorshipReminderKind.IMSAK, Instant.parse("2027-02-22T02:20:00Z"), day)

    private fun maghrib(isIftar: Boolean, at: Instant = Instant.ofEpochMilli(System.currentTimeMillis())) = PlannedAlert(
        instant = at,
        prayer = Prayer.MAGHRIB,
        kind = AlertKind.ADHAN_ALERT,
        sound = AlertSound.ShortAdhan,
        deliveryMode = DeliveryMode.EXACT,
        prayerDay = day,
        isJumuah = false,
        isIftar = isIftar,
    )

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        // Silent ringer: the notification is posted without starting the adhan player.
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_SILENT
    }

    /** The title of the notification the alarm for [alert] posts, built from the intent the alarm carries. */
    private fun fireAlert(alert: PlannedAlert): String {
        val intent = AlertScheduler(context).alarmIntent(alert)
        val n = AdhanNotification(
            Prayer.MAGHRIB, isJumuah = false, id = 7, isIftar = intent.getBooleanExtra(AlertReceiver.EXTRA_IFTAR, false),
        )
        return n.builder(context).build().title()
    }

    private fun fireReminder(reminder: PlannedWorshipReminder): Notification {
        WorshipReminderReceiver().onReceive(context, WorshipReminderReceiver.intent(context, reminder))
        kotlinx.coroutines.runBlocking { WorshipReminderReceiver.idle() }
        return notifications.single { it.channelId == WorshipReminderNotification.CHANNEL }
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test
    @Config(qualifiers = "ar")
    fun `on a Fasting Day Maghrib's Adhan Alert says it is time to break the fast`() {
        assertEquals("حان وقت الإفطار", fireAlert(maghrib(isIftar = true)))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English it reads Time to break your fast`() {
        assertEquals("Time to break your fast", fireAlert(maghrib(isIftar = true)))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `on any other day Maghrib's Adhan Alert reads as usual`() {
        assertEquals("حان الآن وقت صلاة المغرب", fireAlert(maghrib(isIftar = false)))
    }

    @Test
    fun `the Iftar text travels to the adhan player and the full-screen alarm`() {
        val n = AdhanNotification(Prayer.MAGHRIB, isJumuah = false, id = 7, isIftar = true)

        val play = AdhanPlayerService.playIntent(context, n, AlertSound.ShortAdhan)
        val alarm = shadowOf(AdhanAlarmActivity.pendingIntent(context, n.copy(fullScreen = true))).savedIntent

        assertEquals(true, play.getBooleanExtra(AlertReceiver.EXTRA_IFTAR, false))
        assertEquals(true, alarm.getBooleanExtra(AlertReceiver.EXTRA_IFTAR, false))
    }

    @Test
    fun `the Iftar flag and the Ramadan reminders survive the stored plan, and a reboot re-arms those still ahead`() {
        val iftar = maghrib(isIftar = true, at = Instant.parse("2027-02-22T15:00:00Z"))
        AlertScheduler(context).apply(AlertPlan(listOf(iftar), midnight, worshipReminders = listOf(suhoor, imsak)), early)

        val stored = AlertPlanStore(context).read()!!
        assertEquals(listOf(iftar), stored.alerts)
        assertEquals(listOf(suhoor, imsak), stored.worshipReminders)

        alarms.scheduledAlarms.toList().forEach { context.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }
        AlertScheduler(context).rearmStoredPlan(now = Instant.parse("2027-02-22T02:00:00Z"))

        val reminderAlarms = alarms.scheduledAlarms
            .filter { shadowOf(it.operation).savedIntent.action == WorshipReminderReceiver.ACTION_REMIND }
            .map { Instant.ofEpochMilli(it.triggerAtTime) }
        assertEquals(listOf(imsak.instant), reminderAlarms)
    }

    @Test
    fun `a plan stored before the Iftar flag still loads, with no alert marked Iftar`() {
        val file = java.io.File(context.createDeviceProtectedStorageContext().filesDir, AlertPlanStore.FILE_NAME)
        val at = Instant.parse("2027-02-22T15:00:00Z")
        file.writeText(
            listOf(
                "alert-plan-v7",
                midnight.toEpochMilli().toString(),
                listOf(at.toEpochMilli(), "MAGHRIB", "ADHAN_ALERT", "SHORT_ADHAN", "EXACT", day, false, at.toEpochMilli(), false, true, "ALARM").joinToString("\t"),
                listOf("worship", "KAHF", day, at.toEpochMilli(), 0).joinToString("\t"),
                listOf("style", "AUTOMATIC", "").joinToString("\t"),
            ).joinToString("\n", postfix = "\n"),
        )

        val stored = AlertPlanStore(context).read()!!

        assertEquals(listOf(maghrib(isIftar = false, at = at).copy(prayerTime = at)), stored.alerts)
        assertEquals(WorshipReminderKind.KAHF, stored.worshipReminders.single().kind)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the suhoor reminder and the Imsak alert are gentle notifications on the Worship Reminders channel`() {
        assertEquals("تذكير السحور", fireReminder(suhoor).title())
        notificationManager.cancelAll()
        assertEquals("حان وقت الإمساك", fireReminder(imsak).title())
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English the suhoor reminder and the Imsak alert read in English`() {
        assertEquals("Suhoor reminder", fireReminder(suhoor).title())
        notificationManager.cancelAll()
        assertEquals("Time for Imsak", fireReminder(imsak).title())
    }
}
