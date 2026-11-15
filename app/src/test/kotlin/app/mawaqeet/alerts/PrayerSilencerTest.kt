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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Prayer Silence's ringer adapter (#61): what the phone's ringer does at a Silence Window's start and end. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class PrayerSilencerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private val window = SilenceWindow(
        prayer = Prayer.DHUHR,
        prayerDay = LocalDate.parse("2026-11-08"),
        start = Instant.parse("2026-11-08T09:04:00Z"),
        end = Instant.parse("2026-11-08T09:24:00Z"),
        mode = SilenceMode.SILENT,
        isJumuah = false,
    )

    @Before
    fun grantDoNotDisturbAccess() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    @Test
    fun `a phone on normal goes silent at the start and gets its sound back at the end`() {
        PrayerSilencer(context).start(window)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)

        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `with vibrate chosen, a phone on normal vibrates during the window and gets its sound back at the end`() {
        val vibrate = window.copy(mode = SilenceMode.VIBRATE)

        context.sendBroadcast(SilenceReceiver.intent(context, vibrate, start = true))
        idle()
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)

        context.sendBroadcast(SilenceReceiver.intent(context, vibrate, start = false))
        idle()
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `with vibrate chosen, a phone already on vibrate is left alone and not turned up at the end`() {
        val vibrate = window.copy(mode = SilenceMode.VIBRATE)
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        PrayerSilencer(context).start(vibrate)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
        PrayerSilencer(context).end(vibrate)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
    }

    @Test
    fun `with vibrate chosen, a phone already silent stays silent and is not made to vibrate`() {
        val vibrate = window.copy(mode = SilenceMode.VIBRATE)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT

        PrayerSilencer(context).start(vibrate)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        PrayerSilencer(context).end(vibrate)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `the Silence Window's start and end alarms silence the phone and restore it`() {
        context.sendBroadcast(SilenceReceiver.intent(context, window, start = true))
        idle()
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)

        context.sendBroadcast(SilenceReceiver.intent(context, window, start = false))
        idle()
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `a phone already on vibrate is left alone and not turned up at the end`() {
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        PrayerSilencer(context).start(window)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
    }

    @Test
    fun `a phone already silent stays silent after the window`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT

        PrayerSilencer(context).start(window)
        PrayerSilencer(context).end(window)

        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `a phone on Do Not Disturb is left alone, and its ringer is not touched at the end`() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        PrayerSilencer(context).start(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        // Even if the user then silences it themselves, the window's end restores nothing.
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        PrayerSilencer(context).end(window)

        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notifications.currentInterruptionFilter)
    }

    @Test
    fun `without Do Not Disturb access nothing changes`() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(false)

        PrayerSilencer(context).start(window)
        PrayerSilencer(context).end(window)

        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `an end with no start before it changes nothing`() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT

        PrayerSilencer(context).end(window)

        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `if the user turns the sound back on during the window, the end leaves their choice alone`() {
        PrayerSilencer(context).start(window)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        PrayerSilencer(context).ringerChanged()
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        PrayerSilencer(context).end(window)

        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
    }

    @Test
    fun `if the user turns Do Not Disturb on during the window, the end brings the sound back and keeps it on`() {
        PrayerSilencer(context).start(window)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        PrayerSilencer(context).end(window)

        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notifications.currentInterruptionFilter)
    }

    @Test
    fun `Do Not Disturb that the phone turns on along with the silence is turned off again at the end`() {
        PrayerSilencer(context).start(window)
        // Samsung (and AOSP's ringer delegate) turn Do Not Disturb on a moment after an app silences the ringer.
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        PrayerSilencer(context).interruptionFilterChanged()

        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, notifications.currentInterruptionFilter)
    }

    @Test
    fun `Do Not Disturb turned on later in the window is the user's, and the end keeps it`() {
        PrayerSilencer(context).start(window)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        PrayerSilencer(context).interruptionFilterChanged()

        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notifications.currentInterruptionFilter)
    }

    @Test
    fun `a phone that refuses the sound at the end gets it back on a later try`() {
        PrayerSilencer(context).start(window)
        val refuseSound: (AudioManager, Int) -> Unit = { audio, mode -> if (mode != AudioManager.RINGER_MODE_NORMAL) audio.ringerMode = mode }
        PrayerSilencer.ringerSetter = refuseSound
        try {
            PrayerSilencer(context).end(window)
            assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
            // Still refusing: the next try fails too, and the app keeps at it.
            PrayerSilencer(context).endIfOverdue()
            assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        } finally {
            PrayerSilencer.ringerSetter = { audio, mode -> audio.ringerMode = mode }
        }

        // The retry alarm (the window's end alarm again) brings the sound back once the phone allows it.
        PrayerSilencer(context).end(window)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test
    fun `the retries stop once the user turns the sound on, and never mute again`() {
        PrayerSilencer(context).start(window)
        PrayerSilencer.ringerSetter = { audio, mode -> if (mode != AudioManager.RINGER_MODE_NORMAL) audio.ringerMode = mode }
        try {
            PrayerSilencer(context).end(window)
        } finally {
            PrayerSilencer.ringerSetter = { audio, mode -> audio.ringerMode = mode }
        }
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        PrayerSilencer(context).ringerChanged()
        // Later the user silences the phone themselves: no retry turns it back up.
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        PrayerSilencer(context).ringerChanged()
        PrayerSilencer(context).endIfOverdue()
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `after half an hour of refusals it stops trying`() {
        PrayerSilencer(context).start(window)
        PrayerSilencer.ringerSetter = { audio, mode -> if (mode != AudioManager.RINGER_MODE_NORMAL) audio.ringerMode = mode }
        try {
            PrayerSilencer(context).end(window)
        } finally {
            PrayerSilencer.ringerSetter = { audio, mode -> audio.ringerMode = mode }
        }
        PrayerSilencer(context).retryRestore(Instant.now().plusMillis(PrayerSilencer.RETRY_FOR_MILLIS + 60_000))
        PrayerSilencer(context).retryRestore()
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `a phone that ignores the ringer back to sound gets it back through the ring volume`() {
        audio.setStreamVolume(AudioManager.STREAM_RING, 5, 0)
        // Like the Samsung on Android 17: vibrate drops the ring volume to 0, an app's change back to sound is ignored,
        // and setting the ring volume above 0 turns the sound on.
        PrayerSilencer.ringerSetter = { audio, mode ->
            if (mode != AudioManager.RINGER_MODE_NORMAL) {
                audio.ringerMode = mode
                audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            }
        }
        PrayerSilencer.volumeSetter = { audio, stream, index ->
            audio.setStreamVolume(stream, index, 0)
            if (stream == AudioManager.STREAM_RING && index > 0) audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        try {
            PrayerSilencer(context).start(window.copy(mode = SilenceMode.VIBRATE))
            assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)

            PrayerSilencer(context).end(window.copy(mode = SilenceMode.VIBRATE))
            assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
            assertEquals(5, audio.getStreamVolume(AudioManager.STREAM_RING))
        } finally {
            PrayerSilencer.ringerSetter = { audio, mode -> audio.ringerMode = mode }
            PrayerSilencer.volumeSetter = { audio, stream, index -> audio.setStreamVolume(stream, index, 0) }
        }
    }

    /** Runs the main looper, then waits for the receivers' background work. */
    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        kotlinx.coroutines.runBlocking { ReceiverWork.idle() }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
