package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.WorshipReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate

/**
 * The Qiyam reminder in alarm style (#97): planned like alarm-clock mode, it rings over the lock screen with the
 * phone's default alarm sound (never an adhan) and Stop, and stops by itself after a minute; a notification where
 * full-screen alarms aren't allowed. A plain Application, so the app's own scheduler adds nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class QiyamAlarmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @get:Rule
    val compose = createEmptyComposeRule()

    private val day = LocalDate.parse("2026-11-15")
    private val at = Instant.parse("2026-11-15T22:32:00Z")
    private val alarm = PlannedWorshipReminder(WorshipReminderKind.QIYAM, at, day, alarm = true)
    private val notification = alarm.copy(alarm = false)
    private val early = Instant.parse("2026-11-15T00:00:00Z")

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        // Android 14+ lets the user withdraw full-screen alarms; allowed unless a test withdraws it.
        fullScreenAllowed(true)
    }

    @After
    fun tearDown() {
        AdhanNotification.fullScreenPermission = AdhanNotification.DEVICE_FULL_SCREEN_PERMISSION
    }

    private fun fullScreenAllowed(allowed: Boolean) {
        AdhanNotification.fullScreenPermission = { allowed }
    }

    private fun reminderAlarms() =
        alarms.scheduledAlarms.filter { shadowOf(it.operation).savedIntent.action == WorshipReminderReceiver.ACTION_REMIND }

    private fun plan(reminder: PlannedWorshipReminder) = AlertPlan(emptyList(), Instant.parse("2026-11-15T21:00:00Z"), worshipReminders = listOf(reminder))

    @Test
    fun `an alarm-style Qiyam reminder is set as an alarm clock, a notification-style one as an exact alarm`() {
        AlertScheduler(context).apply(plan(alarm), early)
        assertNotNull("the alarm style uses setAlarmClock", reminderAlarms().single().alarmClockInfo)

        AlertScheduler(context).apply(plan(notification), early)
        val plain = reminderAlarms().single()
        assertNull(plain.alarmClockInfo)
        assertEquals(at.toEpochMilli(), plain.triggerAtTime)
    }

    @Test
    fun `the alarm style survives the stored plan, and a reboot re-arms it as an alarm clock`() {
        AlertScheduler(context).apply(plan(alarm), early)

        assertEquals(listOf(alarm), AlertPlanStore(context).read()!!.worshipReminders)

        alarms.scheduledAlarms.toList().forEach { context.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }
        AlertScheduler(context).rearmStoredPlan(now = early)
        assertNotNull(reminderAlarms().single().alarmClockInfo)
    }

    @Test
    fun `a plan stored before the Qiyam style still loads, its Qiyam reminder a notification`() {
        val file = java.io.File(context.createDeviceProtectedStorageContext().filesDir, AlertPlanStore.FILE_NAME)
        file.writeText(
            listOf(
                "alert-plan-v9",
                Instant.parse("2026-11-15T21:00:00Z").toEpochMilli().toString(),
                listOf("worship", "QIYAM", day, at.toEpochMilli(), 0, "").joinToString("\t"),
                listOf("style", "AUTOMATIC", "").joinToString("\t"),
            ).joinToString("\n", postfix = "\n"),
        )

        assertEquals(listOf(notification), AlertPlanStore(context).read()!!.worshipReminders)
    }

    private fun fire(reminder: PlannedWorshipReminder): Notification {
        notificationManager.cancelAll()
        WorshipReminderReceiver().onReceive(context, WorshipReminderReceiver.intent(context, reminder))
        kotlinx.coroutines.runBlocking { WorshipReminderReceiver.idle() }
        return shadowOf(notificationManager).allNotifications.single()
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test
    @Config(qualifiers = "ar")
    fun `the alarm style rings over the lock screen with the phone's alarm sound and Stop, for one minute`() {
        val n = fire(alarm)

        assertEquals("قيام الليل", n.title())
        assertNotNull("shown over the lock screen", n.fullScreenIntent)
        assertEquals(
            QiyamAlarmActivity::class.java.name,
            shadowOf(n.fullScreenIntent).savedIntent.component!!.className,
        )
        assertEquals(Notification.CATEGORY_ALARM, n.category)
        assertEquals(listOf("إيقاف"), n.actions.map { it.title.toString() })
        assertTrue("rings until stopped", n.flags and Notification.FLAG_INSISTENT != 0)
        assertEquals(60_000L, n.timeoutAfter)
        // The phone's default alarm sound on the alarm stream: never an adhan.
        val channel = notificationManager.getNotificationChannel(n.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Settings.System.DEFAULT_ALARM_ALERT_URI, channel.sound)
        assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English the alarm's Stop reads Stop`() {
        val n = fire(alarm)
        assertEquals("Night prayer (Qiyam)", n.title())
        assertEquals(listOf("Stop"), n.actions.map { it.title.toString() })
    }

    @Test
    fun `Stop ends the alarm's sound and its notification`() {
        val stop = shadowOf(fire(alarm).actions.single().actionIntent).savedIntent

        WorshipReminderReceiver().onReceive(context, stop)

        // An insistent notification's sound lasts as long as the notification.
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `the notification style posts a gentle Worship Reminder notification`() {
        val n = fire(notification)

        assertEquals(WorshipReminderNotification.CHANNEL, n.channelId)
        assertNull(n.fullScreenIntent)
        assertFalse(n.flags and Notification.FLAG_INSISTENT != 0)
        assertTrue(n.actions.isNullOrEmpty())
    }

    @Test
    fun `where full-screen alarms aren't allowed the alarm style falls back to a notification`() {
        fullScreenAllowed(false)

        val n = fire(alarm)

        assertEquals(WorshipReminderNotification.CHANNEL, n.channelId)
        assertNull(n.fullScreenIntent)
    }

    @Test
    fun `inside a Silence Window the alarm style stays a quiet notification`() {
        val now = Instant.now()
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        PrayerSilencer(context).start(
            app.mawaqeet.core.SilenceWindow(
                app.mawaqeet.core.Prayer.ISHA, day, now.minusSeconds(300), now.plusSeconds(900), app.mawaqeet.core.SilenceMode.SILENT, isJumuah = false,
            ),
        )

        val n = fire(alarm.copy(instant = now))

        assertEquals(WorshipReminderNotification.CHANNEL, n.channelId)
        assertNull(n.fullScreenIntent)
    }

    /** The alarm screen as the alarm's full-screen intent opens it over the lock screen. */
    private fun openAlarmScreen(n: Notification) =
        Robolectric.buildActivity(QiyamAlarmActivity::class.java, shadowOf(n.fullScreenIntent).savedIntent).setup()

    @Test
    @Config(qualifiers = "en")
    fun `the alarm screen's Stop ends the sound and the notification, and closes it`() {
        val screen = openAlarmScreen(fire(alarm))
        compose.onNodeWithText("Night prayer (Qiyam)").assertExists()

        compose.onNodeWithText("Stop").performClick()

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertTrue(screen.get().isFinishing)
    }

    @Test
    fun `the alarm screen closes by itself after a minute, as the alarm stops`() {
        val screen = openAlarmScreen(fire(alarm))

        ShadowLooper.idleMainLooper(58, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse(screen.get().isFinishing)
        ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(screen.get().isFinishing)
    }

    @Test
    @Config(sdk = [33])
    fun `before Android 14 full-screen alarms are always allowed`() {
        assertNotNull(fire(alarm).fullScreenIntent)
    }
}
