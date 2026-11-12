package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
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

/** Worship Reminders (#64): the Friday Surat Al-Kahf reminder and its «قرأتها» action. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class KahfReminderNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val friday = LocalDate.parse("2026-11-13")
    private val kahf = PlannedWorshipReminder(WorshipReminderKind.KAHF, Instant.parse("2026-11-13T04:30:00Z"), friday)
    private val salawat = PlannedWorshipReminder(WorshipReminderKind.SALAWAT, Instant.parse("2026-11-13T05:30:00Z"), friday, 0)
    private val midnight = Instant.parse("2026-11-13T21:00:00Z")
    private val early = Instant.parse("2026-11-13T03:00:00Z")

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun fire(reminder: PlannedWorshipReminder) {
        val alarm = alarms.scheduledAlarms.first { Instant.ofEpochMilli(it.triggerAtTime) == reminder.instant }
        WorshipReminderReceiver().onReceive(context, shadowOf(alarm.operation).savedIntent)
        kotlinx.coroutines.runBlocking { WorshipReminderReceiver.idle() }
    }

    private fun kahfNotification(): Notification = shadowOf(notificationManager).allNotifications
        .single { it.extras.getCharSequence(Notification.EXTRA_TITLE).toString() in listOf("اقرأ سورة الكهف", "Read Surat Al-Kahf") }

    @Test
    @Config(qualifiers = "ar")
    fun `the Kahf reminder is a gentle notification on the Worship Reminders channel, with a read-it action, that opens the app`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(kahf)), early)

        fire(kahf)

        val notification = kahfNotification()
        assertEquals("اقرأ سورة الكهف", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(WorshipReminderNotification.CHANNEL, notification.channelId)
        assertEquals(listOf("قرأتها"), notification.actions.map { it.title.toString() })
        assertEquals(MainActivity::class.java.name, shadowOf(notification.contentIntent).savedIntent.component!!.className)
    }

    @Test
    fun `in English the action reads I read it`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(kahf)), early)

        fire(kahf)

        val notification = kahfNotification()
        assertEquals("Read Surat Al-Kahf", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(listOf("I read it"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun `with the phone in English and the app in Arabic, the reminder and its channel are in Arabic`() {
        AlertScheduler(context).apply(
            AlertPlan(emptyList(), midnight, worshipReminders = listOf(salawat)),
            early,
            NotificationStyle(app.mawaqeet.core.DigitStyle.AUTOMATIC, "ar"),
        )

        fire(salawat)

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("صلِّ على النبي ﷺ", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("تذكيرات العبادة", notificationManager.getNotificationChannel(WorshipReminderNotification.CHANNEL).name.toString())
    }

    @Test
    fun `I read it dismisses the Kahf reminder and leaves a salawat reminder alone`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(kahf, salawat)), early)
        fire(kahf)
        fire(salawat)
        assertEquals(2, shadowOf(notificationManager).allNotifications.size)

        val readIt = shadowOf(kahfNotification().actions.single().actionIntent).savedIntent
        WorshipReminderReceiver().onReceive(context, readIt)

        val left = shadowOf(notificationManager).allNotifications
        assertEquals(1, left.size)
        assertEquals("Send salawat on the Prophet ﷺ", left.single().extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `a salawat reminder has no read-it action`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(salawat)), early)

        fire(salawat)

        assertTrue(shadowOf(notificationManager).allNotifications.single().actions.isNullOrEmpty())
    }

    @Test
    fun `the Kahf reminder survives the stored plan`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(kahf)), early)

        assertEquals(listOf(kahf), AlertPlanStore(context).read()!!.worshipReminders)
    }
}
