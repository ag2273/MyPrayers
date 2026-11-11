package app.mawaqeet.alerts

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * The adhan plays at the level chosen in Settings → Alerts, not at whatever the phone's alarm (or media) volume holds
 * when it fires: another alarm app or a Bedtime routine may have raised it meanwhile.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AdhanPlayerServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val level = AdhanLevel(context)
    private val notification = AdhanNotification(Prayer.FAJR, isJumuah = false, id = 7)

    @Before
    fun knownMedia() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        for (sound in listOf(AlertSound.FajrFullAdhan, AlertSound.ShortAdhan)) {
            ShadowMediaPlayer.addMediaInfo(
                DataSource.toDataSource(context, AdhanSounds.bundled(context, sound)),
                ShadowMediaPlayer.MediaInfo(60_000, 0),
            )
        }
    }

    private fun start(volume: AdhanVolume): ServiceController<AdhanPlayerService> {
        val intent = AdhanPlayerService.playIntent(context, notification, AlertSound.FajrFullAdhan, volume)
        return Robolectric.buildService(AdhanPlayerService::class.java, intent).create().startCommand(0, 1)
    }

    private fun stop(service: ServiceController<AdhanPlayerService>) {
        service.withIntent(Intent(context, AdhanPlayerService::class.java).setAction(AdhanPlayerService.ACTION_STOP))
            .startCommand(0, 2)
    }

    private fun alarm() = audio.getStreamVolume(AudioManager.STREAM_ALARM)

    @Test
    fun `the adhan plays at the chosen level although the alarm volume was raised since`() {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0)
        level.setIndex(audio, AdhanVolume.ALARM, 2) // The slider in Settings.
        val loud = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, loud, 0) // Another alarm app, or a routine.

        val service = start(AdhanVolume.ALARM)
        assertEquals(2, alarm())

        stop(service)
        assertEquals("the phone's own alarm volume comes back", loud, alarm())
    }

    @Test
    fun `the volume keys during the adhan win over restoring`() {
        level.setIndex(audio, AdhanVolume.ALARM, 2)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 6, 0)
        val service = start(AdhanVolume.ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0) // Turned down by hand while it plays.

        stop(service)
        assertEquals(1, alarm())
    }

    @Test
    fun `a new adhan replacing one still playing restores before applying again`() {
        level.setIndex(audio, AdhanVolume.ALARM, 2)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 6, 0)
        val service = start(AdhanVolume.ALARM)
        service.withIntent(AdhanPlayerService.playIntent(context, notification, AlertSound.ShortAdhan, AdhanVolume.ALARM))
            .startCommand(0, 2)
        assertEquals(2, alarm())

        stop(service)
        assertEquals(6, alarm())
    }

    @Test
    fun `no chosen level leaves the alarm volume alone`() {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 5, 0)
        val service = start(AdhanVolume.ALARM)
        assertEquals(5, alarm())
        stop(service)
        assertEquals(5, alarm())
    }

    @Test
    fun `the media stream gets its own chosen level`() {
        level.setIndex(audio, AdhanVolume.MEDIA, 4)
        level.setIndex(audio, AdhanVolume.ALARM, 1)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 12, 0)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 6, 0)

        val service = start(AdhanVolume.MEDIA)
        assertEquals(4, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals("the alarm stream isn't touched", 6, alarm())

        stop(service)
        assertEquals(12, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
}
