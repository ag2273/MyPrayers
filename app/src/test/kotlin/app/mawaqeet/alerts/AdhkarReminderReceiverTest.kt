package app.mawaqeet.alerts

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MainActivity
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.AdhkarSlot
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.WorshipReminderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Morning and evening adhkar reminders (#75): skipped once the set is complete for its Prayer Day; a tap opens that set. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en")
class AdhkarReminderReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app get() = context as MawaqeetApplication
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val day = LocalDate.parse("2026-11-08")
    private val morning = PlannedWorshipReminder(WorshipReminderKind.MORNING_ADHKAR, Instant.parse("2026-11-08T02:39:00Z"), day)
    private val evening = PlannedWorshipReminder(WorshipReminderKind.EVENING_ADHKAR, Instant.parse("2026-11-08T12:47:00Z"), day)

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun <T> io(block: () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    private fun fire(reminder: PlannedWorshipReminder) {
        WorshipReminderReceiver().onReceive(context, WorshipReminderReceiver.intent(context, reminder))
        runBlocking { WorshipReminderReceiver.idle() }
    }

    private fun posted() = shadowOf(notificationManager).allNotifications

    @Test
    fun `a morning adhkar reminder is posted while the morning set is not complete, and its tap opens the morning set`() {
        fire(morning)

        val notification: Notification = posted().single()
        assertEquals("Morning adhkar", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(WorshipReminderNotification.CHANNEL, notification.channelId)
        val tap = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component!!.className)
        assertEquals(AdhkarSlot.MORNING, MainActivity.adhkarSlot(tap))
    }

    @Test
    fun `the evening reminder opens the evening set`() {
        fire(evening)

        val notification = posted().single()
        assertEquals("Evening adhkar", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(AdhkarSlot.EVENING, MainActivity.adhkarSlot(shadowOf(notification.contentIntent).savedIntent))
    }

    @Test
    fun `no reminder once the set is complete for that Prayer Day`() {
        io { app.adhkar.recordCompleted(day, AdhkarSlot.MORNING.key) }

        fire(morning)

        assertTrue(posted().isEmpty())
    }

    @Test
    fun `the evening reminder still comes when only the morning set is complete, or the morning was completed another day`() {
        io { app.adhkar.recordCompleted(day, AdhkarSlot.MORNING.key) }
        io { app.adhkar.recordCompleted(day.minusDays(1), AdhkarSlot.EVENING.key) }

        fire(evening)

        assertEquals(1, posted().size)
    }
}
