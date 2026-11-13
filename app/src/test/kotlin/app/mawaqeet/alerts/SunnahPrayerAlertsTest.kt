package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.PlannedWorshipReminder
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
 * The Duha and Qiyam reminders (#96): gentle Worship Reminder notifications on the «تذكيرات العبادة» channel, never an
 * adhan, that survive the stored plan. A plain Application, so the app's own scheduler adds nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class SunnahPrayerAlertsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val day = LocalDate.parse("2026-11-15")
    private val duha = PlannedWorshipReminder(WorshipReminderKind.DUHA, Instant.parse("2026-11-15T04:18:00Z"), day)

    // The Last Third of 15 November's night begins after midnight; it still belongs to the 15th.
    private val qiyam = PlannedWorshipReminder(WorshipReminderKind.QIYAM, Instant.parse("2026-11-15T22:32:00Z"), day)

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun fire(reminder: PlannedWorshipReminder): Notification {
        notificationManager.cancelAll()
        WorshipReminderReceiver().onReceive(context, WorshipReminderReceiver.intent(context, reminder))
        kotlinx.coroutines.runBlocking { WorshipReminderReceiver.idle() }
        return shadowOf(notificationManager).allNotifications.single { it.channelId == WorshipReminderNotification.CHANNEL }
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    @Config(qualifiers = "ar")
    fun `each reminder posts its own Arabic text`() {
        assertEquals("صلاة الضحى", fire(duha).title())
        assertEquals("حان وقت صلاة الضحى", fire(duha).text())
        assertEquals("قيام الليل", fire(qiyam).title())
        assertEquals("بدأ الثلث الأخير من الليل، وقت قيام الليل", fire(qiyam).text())
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English each reminder posts its English text`() {
        assertEquals("Duha prayer", fire(duha).title())
        assertEquals("It is time for the Duha prayer", fire(duha).text())
        assertEquals("Night prayer (Qiyam)", fire(qiyam).title())
        assertEquals("The last third of the night has begun: a time for Qiyam", fire(qiyam).text())
    }

    @Test
    fun `both survive the stored plan, and a reboot re-arms them`() {
        val early = Instant.parse("2026-11-15T00:00:00Z")
        val plan = AlertPlan(emptyList(), Instant.parse("2026-11-15T21:00:00Z"), worshipReminders = listOf(duha, qiyam))
        AlertScheduler(context).apply(plan, early)

        assertEquals(listOf(duha, qiyam), AlertPlanStore(context).read()!!.worshipReminders)

        alarms.scheduledAlarms.toList().forEach { context.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }
        AlertScheduler(context).rearmStoredPlan(now = early)
        val reminderAlarms = alarms.scheduledAlarms.filter { shadowOf(it.operation).savedIntent.action == WorshipReminderReceiver.ACTION_REMIND }
        assertEquals(listOf(duha.instant, qiyam.instant).map { it.toEpochMilli() }, reminderAlarms.map { it.triggerAtTime }.sorted())
    }
}
