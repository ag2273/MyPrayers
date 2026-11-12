package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

/** The quiet notification while a Silence Window is active, and its «استعادة الصوت» action (#67). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class SilenceNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val posted: List<Notification> get() = shadowOf(notificationManager).allNotifications
    private val zone = TimeZone.getDefault()

    private val window = SilenceWindow(
        prayer = Prayer.ASR,
        prayerDay = LocalDate.parse("2026-11-08"),
        start = Instant.parse("2026-11-08T15:04:00Z"),
        end = Instant.parse("2026-11-08T15:24:00Z"),
        mode = SilenceMode.SILENT,
        isJumuah = false,
    )

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    @After
    fun restoreZone() = TimeZone.setDefault(zone)

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.actionTitles() = actions.orEmpty().map { it.title.toString() }

    @Test
    fun `while silenced, a quiet notification says until when in Arabic, with Restore sound`() {
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        val notification = posted.single()
        assertEquals("الهاتف صامت حتى ٣:٢٤", notification.text())
        assertEquals(listOf("استعادة الصوت"), notification.actionTitles())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            notificationManager.getNotificationChannel(notification.channelId).importance,
        )
    }

    @Test
    fun `in English with Western digits and a 24-hour clock it reads Phone silenced until, with Restore sound`() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")

        PrayerSilencer(context, DigitStyle.WESTERN, "en").start(window)

        val notification = posted.single()
        assertEquals("Phone silenced until 15:24", notification.text())
        assertEquals(listOf("Restore sound"), notification.actionTitles())
    }

    @Test
    fun `the app's digits and language travel in the stored plan, so a window started by an alarm uses them`() {
        AlertScheduler(context).apply(
            AlertPlan(emptyList(), Instant.parse("2026-11-08T21:00:00Z"), silenceWindows = listOf(window)),
            early,
            NotificationStyle(DigitStyle.ARABIC_INDIC, "ar"),
        )

        PrayerSilencer(context).start(window)

        assertEquals("الهاتف صامت حتى ٣:٢٤", posted.single().text())
    }

    @Test
    fun `Restore sound brings the sound back at once, removes the notification and cancels the end alarm`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        AlertScheduler(context).apply(AlertPlan(emptyList(), Instant.parse("2026-11-08T21:00:00Z"), silenceWindows = listOf(window)), early)
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        posted.single().actions.single().actionIntent.send()
        idle()

        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertTrue(posted.isEmpty())
        assertEquals(listOf(SilenceReceiver.ACTION_START), silenceAlarmActions())
    }

    @Test
    fun `if the user turns the sound back on during the window, the notification goes and the end leaves their choice`() {
        PrayerSilencer.watchRinger(context)
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))

        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        context.sendBroadcast(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION))
        idle()
        assertTrue(posted.isEmpty())

        // Even silent again, the window is theirs now: its end does not turn the sound on over them.
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `a phone that moves vibrate to mute keeps the notification, and the end brings the sound back`() {
        // A Samsung S25+ with vibrate chosen: the sound never came back after Isha.
        PrayerSilencer.watchRinger(context)
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window.copy(mode = SilenceMode.VIBRATE))

        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        context.sendBroadcast(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION))
        idle()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        context.sendBroadcast(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION))
        idle()
        assertEquals(1, posted.size)

        PrayerSilencer(context).end(window.copy(mode = SilenceMode.VIBRATE))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `a moment on sound right after the start is the phone settling, not the user`() {
        PrayerSilencer.watchRinger(context)
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        context.sendBroadcast(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION))
        idle()
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        context.sendBroadcast(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION))
        idle()
        assertEquals(1, posted.size)

        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `a window whose end alarm never came ends at the next check after its end`() {
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        PrayerSilencer(context).endIfOverdue(window.end.minusSeconds(60))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)

        PrayerSilencer(context).endIfOverdue(window.end.plusSeconds(60))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `what Prayer Silence did is logged for a report`() {
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)
        PrayerSilencer(context).end(window)

        val log = SilenceLog.read(context)
        assertTrue(log, "start ${window.prayer} ringer=silent" in log)
        assertTrue(log, "restored ringer=normal" in log)
    }

    @Test
    fun `the app's own silencing does not count as the user's change`() {
        PrayerSilencer.watchRinger(context)
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        context.sendBroadcast(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION))
        idle()

        assertEquals(1, posted.size)
        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `a phone left alone gets no notification`() {
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        assertTrue(posted.isEmpty())
    }

    @Test
    fun `the window's end removes the notification`() {
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        context.sendBroadcast(SilenceReceiver.intent(context, window, start = false))
        idle()

        assertTrue(posted.isEmpty())
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `a window that starts while the phone is still held silent extends the silence to its own end`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val overlapping = window.copy(
            prayer = Prayer.MAGHRIB,
            start = Instant.parse("2026-11-08T15:20:00Z"),
            end = Instant.parse("2026-11-08T15:40:00Z"),
        )
        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(window)

        PrayerSilencer(context, DigitStyle.ARABIC_INDIC, "ar").start(overlapping)
        assertEquals("الهاتف صامت حتى ٣:٤٠", posted.single().text())
        assertEquals(
            listOf(SilenceReceiver.ACTION_END to overlapping.end.toEpochMilli()),
            shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
                .map { shadowOf(it.operation).savedIntent.action to it.triggerAtTime },
        )

        // The first window's own end no longer brings the sound back early.
        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(1, posted.size)

        PrayerSilencer(context).end(overlapping)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertTrue(posted.isEmpty())
    }

    private val early = Instant.parse("2026-11-08T14:00:00Z")

    private fun silenceAlarmActions() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        .map { shadowOf(it.operation).savedIntent.action }
        .filter { it == SilenceReceiver.ACTION_START || it == SilenceReceiver.ACTION_END }

    /** Runs the main looper, then waits for the receivers' background work. */
    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        kotlinx.coroutines.runBlocking { ReceiverWork.idle() }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
