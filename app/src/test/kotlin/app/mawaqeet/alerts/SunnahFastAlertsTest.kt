package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.alerts.AlertScheduler.Companion.requestCode
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.SunnahFastDay
import app.mawaqeet.core.HijriDate
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.SunnahFast
import app.mawaqeet.core.WorshipReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate

/**
 * Sunnah Fast reminders (#95): a gentle Worship Reminder notification the night before, naming the day and the fast.
 * Sunday 22 Nov 2026 is the night before Monday 13 Jumada al-Akhirah 1448. A plain Application, so the app's own
 * scheduler adds nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class SunnahFastAlertsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val sunday = LocalDate.parse("2026-11-22")
    private val night = Instant.parse("2026-11-22T16:45:00Z")

    private fun fastReminder(prayerDay: LocalDate, hijri: HijriDate, vararg kinds: SunnahFast) =
        PlannedWorshipReminder(WorshipReminderKind.SUNNAH_FAST, night, prayerDay, fast = SunnahFastDay(hijri, kinds.toSet()))

    private val mondayAndWhite = fastReminder(sunday, HijriDate(1448, 6, 13), SunnahFast.MONDAY_THURSDAY, SunnahFast.WHITE_DAYS)

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
    fun `the reminder names tomorrow and its fast in Arabic`() {
        val thursday = LocalDate.parse("2026-11-18")
        assertEquals("صيام السنة", fire(mondayAndWhite).title())
        assertEquals("غدًا يوم الاثنين، يُسنّ صيامه", fire(fastReminder(LocalDate.parse("2026-11-15"), HijriDate(1448, 6, 6), SunnahFast.MONDAY_THURSDAY)).text())
        assertEquals("غدًا يوم الخميس، يُسنّ صيامه", fire(fastReminder(thursday, HijriDate(1448, 6, 9), SunnahFast.MONDAY_THURSDAY)).text())
        assertEquals("غدًا يوم الاثنين ومن الأيام البيض، يُسنّ صيامه", fire(mondayAndWhite).text())
        assertEquals("غدًا يوم عرفة، يُسنّ صيامه", fire(fastReminder(LocalDate.parse("2027-05-14"), HijriDate(1448, 12, 9), SunnahFast.ARAFAH)).text())
        assertEquals("غدًا يوم تاسوعاء، يُسنّ صيامه", fire(fastReminder(LocalDate.parse("2026-06-23"), HijriDate(1448, 1, 9), SunnahFast.ASHURA)).text())
        assertEquals(
            "غدًا يوم الخميس ويوم عاشوراء، يُسنّ صيامه",
            fire(fastReminder(LocalDate.parse("2026-06-24"), HijriDate(1448, 1, 10), SunnahFast.MONDAY_THURSDAY, SunnahFast.ASHURA)).text(),
        )
        assertEquals(
            "غدًا أول الست من شوال، يُسنّ صيامه",
            fire(fastReminder(LocalDate.parse("2027-03-09"), HijriDate(1448, 10, 2), SunnahFast.SIX_OF_SHAWWAL)).text(),
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English the reminder names tomorrow and its fast in English`() {
        assertEquals("Sunnah fast", fire(mondayAndWhite).title())
        assertEquals("Tomorrow is Monday and one of the white days. Fasting it is Sunnah.", fire(mondayAndWhite).text())
        assertEquals(
            "Tomorrow is the Day of Arafah. Fasting it is Sunnah.",
            fire(fastReminder(LocalDate.parse("2027-05-14"), HijriDate(1448, 12, 9), SunnahFast.ARAFAH)).text(),
        )
        assertEquals(
            "Tomorrow is Thursday and Ashura. Fasting it is Sunnah.",
            fire(fastReminder(LocalDate.parse("2026-06-24"), HijriDate(1448, 1, 10), SunnahFast.MONDAY_THURSDAY, SunnahFast.ASHURA)).text(),
        )
        assertEquals(
            "Tomorrow is the first of the six days of Shawwal. Fasting it is Sunnah.",
            fire(fastReminder(LocalDate.parse("2027-03-09"), HijriDate(1448, 10, 2), SunnahFast.SIX_OF_SHAWWAL)).text(),
        )
    }

    @Test
    fun `the fast survives the stored plan, and a reboot re-arms it`() {
        val early = Instant.parse("2026-11-22T00:00:00Z")
        AlertScheduler(context).apply(AlertPlan(emptyList(), Instant.parse("2026-11-22T21:00:00Z"), worshipReminders = listOf(mondayAndWhite)), early)

        assertEquals(listOf(mondayAndWhite), AlertPlanStore(context).read()!!.worshipReminders)

        alarms.scheduledAlarms.toList().forEach { context.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }
        AlertScheduler(context).rearmStoredPlan(now = early)
        val reminderAlarms = alarms.scheduledAlarms.filter { shadowOf(it.operation).savedIntent.action == WorshipReminderReceiver.ACTION_REMIND }
        assertEquals(listOf(night.toEpochMilli()), reminderAlarms.map { it.triggerAtTime })
    }

    @Test
    fun `each kind's alarm keeps its own request code, apart from the next day's reminders`() {
        val sunday = PlannedWorshipReminder(WorshipReminderKind.SUNNAH_FAST, night, this.sunday, fast = mondayAndWhite.fast)
        val codes = WorshipReminderKind.entries.flatMap { kind ->
            listOf(this.sunday, this.sunday.plusDays(1)).map { day -> PlannedWorshipReminder(kind, night, day).requestCode }
        } + sunday.requestCode
        // SUNNAH_FAST on Sunday is listed twice (with and without its fast): the same alarm.
        assertEquals(WorshipReminderKind.entries.size * 2, codes.toSet().size)
        assertNotEquals(
            PlannedWorshipReminder(WorshipReminderKind.SUHOOR, night, this.sunday).requestCode,
            PlannedWorshipReminder(WorshipReminderKind.SALAWAT, night, this.sunday.plusDays(1)).requestCode,
        )
    }
}
