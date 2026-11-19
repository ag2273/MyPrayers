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
import app.mawaqeet.R
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
    private fun dhikr(at: String, slot: Int, sound: Boolean = true) =
        PlannedWorshipReminder(WorshipReminderKind.DHIKR, Instant.parse(at), day, slot, sound = sound)
    private val first = dhikr("2026-11-08T05:27:00Z", 0)
    private val second = dhikr("2026-11-08T07:27:00Z", 1)
    private val quiet = dhikr("2026-11-08T09:27:00Z", 2, sound = false)

    /** The recordings [DhikrVoice] played, in order. */
    private val said = mutableListOf<Int>()
    private val audio get() = context.getSystemService(android.media.AudioManager::class.java)
    private val midnight = Instant.parse("2026-11-08T21:00:00Z")
    private val early = Instant.parse("2026-11-08T03:00:00Z")

    private fun reminderAlarms() = alarms.scheduledAlarms
        .filter { shadowOf(it.operation).savedIntent.action == WorshipReminderReceiver.ACTION_REMIND }
        .map { Instant.ofEpochMilli(it.triggerAtTime) }
        .sorted()

    @Before
    fun setUp() {
        DhikrVoice.player = { _, recording -> said += recording }
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
    fun `turning the dhikr reminder off cancels its alarms`() {
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
    fun `a dhikr reminder shows one of the seven adhkar on its own soundless channel, says it aloud, and opens the app`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first)), early)

        fire(first)

        val notification: Notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("ذكر الله والتسبيح", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        val adhkar = listOf(
            "سبحان الله", "أستغفر الله", "الحمد لله", "الله أكبر", "اللهم صلِّ على محمد", "سبحان الله وبحمده", "لا إله إلا الله",
        )
        assertTrue(text, text in adhkar)
        assertEquals(WorshipReminderNotification.DHIKR_CHANNEL, notification.channelId)
        val channel = notificationManager.getNotificationChannel(WorshipReminderNotification.DHIKR_CHANNEL)
        assertEquals("ذكر الله والتسبيح", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(null, channel.sound)
        // The recording said is the one of the dhikr shown.
        val recordings = mapOf(
            "سبحان الله" to R.raw.dhikr_subhan_allah, "أستغفر الله" to R.raw.dhikr_astaghfirullah,
            "الحمد لله" to R.raw.dhikr_alhamdulillah, "الله أكبر" to R.raw.dhikr_allahu_akbar,
            "اللهم صلِّ على محمد" to R.raw.dhikr_salat_ala_muhammad, "سبحان الله وبحمده" to R.raw.dhikr_subhan_allah_wa_bihamdihi,
            "لا إله إلا الله" to R.raw.dhikr_la_ilaha_illallah,
        )
        assertEquals(listOf(recordings.getValue(text)), said)
        val tap = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component!!.className)
    }

    @Test
    fun `with the dhikr sound off the reminder comes without saying anything`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(quiet)), early)

        fire(quiet)

        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
        assertEquals(emptyList<Int>(), said)
    }

    @Test
    fun `on a phone set to silent or vibrate the dhikr is not said aloud`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first, second)), early)

        audio.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
        fire(first)
        audio.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
        fire(second)

        assertEquals(emptyList<Int>(), said)
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `the sound choice survives the stored plan`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first, quiet)), early)

        assertEquals(listOf(first, quiet), AlertPlanStore(context).read()!!.worshipReminders)
    }

    @Test
    fun `an alarm set by an older version for a salawat reminder still posts the dhikr reminder`() {
        val old = WorshipReminderReceiver.intent(context, first).putExtra("kind", "SALAWAT").apply { removeExtra("sound") }

        WorshipReminderReceiver().onReceive(context, old)
        kotlinx.coroutines.runBlocking { WorshipReminderReceiver.idle() }

        assertEquals(WorshipReminderNotification.DHIKR_CHANNEL, shadowOf(notificationManager).allNotifications.single().channelId)
        assertEquals(1, said.size)
    }

    @Test
    fun `the other Worship Reminders keep the default notification sound on their own channel`() {
        val kahf = PlannedWorshipReminder(WorshipReminderKind.KAHF, Instant.parse("2026-11-13T05:30:00Z"), LocalDate.parse("2026-11-13"))
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(kahf)), early)

        fire(kahf)

        assertEquals(WorshipReminderNotification.CHANNEL, shadowOf(notificationManager).allNotifications.single().channelId)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, notificationManager.getNotificationChannel(WorshipReminderNotification.CHANNEL).sound)
        assertEquals(emptyList<Int>(), said)
    }

    @Test
    fun `in English the reminder and its channel read in English`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first)), early)

        fire(first)

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Remember Allah", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Dhikr and tasbeeh", notificationManager.getNotificationChannel(WorshipReminderNotification.DHIKR_CHANNEL).name.toString())
    }

    @Test
    fun `a new dhikr reminder replaces the last one, so they never pile up`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, worshipReminders = listOf(first, second)), early)

        fire(first)
        fire(second)

        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }
}
