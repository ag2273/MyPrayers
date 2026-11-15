package app.mawaqeet.alerts

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Android 17 ignores an app's ringer changes from the background, so there a Silence Window's alarms hand the change to
 * [SilenceService], a foreground service, instead of making it in the receiver.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class SilenceServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val app = shadowOf(context as Application)

    private val window = SilenceWindow(
        prayer = Prayer.ASR,
        prayerDay = LocalDate.parse("2026-11-15"),
        start = Instant.parse("2026-11-15T12:37:00Z"),
        end = Instant.parse("2026-11-15T12:57:00Z"),
        mode = SilenceMode.VIBRATE,
        isJumuah = false,
    )

    @Before
    fun setUp() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        while (app.nextStartedService != null) Unit
    }

    @After
    fun reset() {
        SilenceService.foregroundNeeded = { android.os.Build.VERSION.SDK_INT >= 37 }
    }

    private fun alarm(start: Boolean) {
        SilenceReceiver().onReceive(context, SilenceReceiver.intent(context, window, start))
        shadowOf(Looper.getMainLooper()).idle()
        kotlinx.coroutines.runBlocking { ReceiverWork.idle() }
    }

    /** Runs the service the alarm started, as Android would, and waits (up to 5 s) for the ringer to reach [expected]. */
    private fun runStartedService(expected: Int) {
        val intent = app.nextStartedService!!
        assertEquals(SilenceService::class.java.name, intent.component!!.className)
        Robolectric.buildService(SilenceService::class.java, intent).create().startCommand(0, 1)
        repeat(50) { if (audio.ringerMode != expected) Thread.sleep(100) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `on Android 17 the start and end alarms work through the foreground service and the sound comes back`() {
        SilenceService.foregroundNeeded = { true }

        alarm(start = true)
        assertEquals("the receiver itself changes nothing", AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        runStartedService(AudioManager.RINGER_MODE_VIBRATE)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)

        alarm(start = false)
        runStartedService(AudioManager.RINGER_MODE_NORMAL)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `before Android 17 the alarms change the ringer in the receiver, with no service`() {
        SilenceService.foregroundNeeded = { false }

        alarm(start = true)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
        alarm(start = false)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertNull(app.nextStartedService)
    }
}
