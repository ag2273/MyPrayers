package app.mawaqeet.alerts

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AlertReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val app get() = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())

    private fun fire(sound: AlertSound) {
        context.sendBroadcast(
            Intent(context, AlertReceiver::class.java)
                .setAction(AlertReceiver.ACTION_ALERT)
                .putExtra(AlertReceiver.EXTRA_PRAYER, Prayer.ASR.name)
                .putExtra(AlertReceiver.EXTRA_REQUEST_CODE, 7)
                .putExtra(AlertReceiver.EXTRA_SOUND, encodeAlertSound(sound))
                .putExtra(AlertReceiver.EXTRA_AT, System.currentTimeMillis()),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `with the ringer on, the Adhan Alert starts the adhan player with the alert's sound`() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        fire(AlertSound.FullAdhan)

        val started = app.nextStartedService
        assertEquals(AdhanPlayerService::class.java.name, started.component?.className)
        assertEquals("FULL_ADHAN", started.getStringExtra(AlertReceiver.EXTRA_SOUND))
    }

    @Test
    fun `in silent mode no player starts but the notification is still posted`() {
        app.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        fire(AlertSound.ShortAdhan)

        assertNull(app.nextStartedService)
        assertTrue(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.isNotEmpty())
    }
}
