package app.mawaqeet.alerts

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.mawaqeet.R
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer

/**
 * The adhan player: a `mediaPlayback` foreground service started by [AlertOutput] when the core says an Adhan
 * Alert should sound. It plays on the alarm stream (following the alarm volume) or, when the user chose it, the media
 * stream (following the media volume) — see [AdhanVolume] — at the level chosen in Settings ([AdhanLevel]), fades in over [FADE_IN_MS], holds transient audio
 * focus so other media pauses and resumes, and shows the Adhan Alert notification with Stop.
 *
 * All decisions (silent mode, calls, lateness) are made before it starts, in the core.
 */
class AdhanPlayerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var notification: AdhanNotification? = null
    private var attributes: AudioAttributes = AdhanSounds.attributes(AdhanVolume.ALARM)
    /** The stream level this adhan set ([AdhanLevel]), put back when it stops. */
    private var level: AdhanLevel.Applied? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val prayer = intent?.getStringExtra(AlertReceiver.EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
        val sound = intent?.getStringExtra(AlertReceiver.EXTRA_SOUND)?.let(::decodeAlertSound)
        if (prayer == null || sound == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val next = AdhanNotification(
            prayer,
            intent.getBooleanExtra(AlertReceiver.EXTRA_JUMUAH, false),
            intent.getIntExtra(AlertReceiver.EXTRA_REQUEST_CODE, 1),
            prayerDay = AlertReceiver.prayerDay(intent),
            fullScreen = intent.getBooleanExtra(AlertReceiver.EXTRA_FULL_SCREEN, false),
        )
        stopPlayback() // A new alert replaces one still playing.
        val volume = AlertReceiver.adhanVolume(intent)
        attributes = AdhanSounds.attributes(volume)
        level = AdhanLevel(this).apply(getSystemService(AudioManager::class.java), volume)
        notification?.let { if (it.id != next.id) it.post(this) }
        notification = next
        playingId = next.id
        ServiceCompat.startForeground(this, next.id, playingNotification(next), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        play(sound)
        return START_NOT_STICKY // Never replay after the process is killed or the phone restarts.
    }

    private fun playingNotification(n: AdhanNotification) = n.builder(this)
        .setOngoing(true)
        .setDeleteIntent(stopIntent(this))
        .addAction(NotificationCompat.Action(0, getString(R.string.adhan_stop), stopIntent(this)))
        .build()

    private fun play(sound: AlertSound) {
        val audio = getSystemService(AudioManager::class.java)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS) finish() }
            .build()
        focus = focusRequest
        audio.requestAudioFocus(focusRequest)

        prepare(sound, AdhanSounds.uri(this, sound))
    }

    /**
     * Prepares and starts [source]. A Custom Adhan that can't be opened or decoded (deleted, permission lost, not
     * audio) falls back to its bundled recording once; Settings → Alerts shows a notice for the unreadable file.
     */
    private fun prepare(sound: AlertSound, source: android.net.Uri) {
        val fallback = AdhanSounds.bundled(this, sound)
        val canFallBack = source != fallback
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(attributes)
        try {
            mp.setDataSource(this, source)
        } catch (e: Exception) {
            if (!canFallBack) throw e
            Log.w(TAG, "Adhan source unreadable, using the bundled one", e)
            mp.release()
            prepare(sound, fallback)
            return
        }
        mp.setVolume(0f, 0f)
        var started = false
        mp.setOnPreparedListener {
            started = true
            it.start()
            fadeIn(it, System.currentTimeMillis())
        }
        mp.setOnCompletionListener { finish() }
        mp.setOnErrorListener { failed, what, extra ->
            Log.w(TAG, "Adhan playback error $what/$extra")
            if (canFallBack && player === failed && !started) {
                // A Custom Adhan the player can't decode: play the bundled recording instead of nothing.
                handler.removeCallbacksAndMessages(null)
                failed.release()
                prepare(sound, fallback)
            } else {
                finish()
            }
            true
        }
        mp.prepareAsync()
    }

    /** Raises the player's volume (relative to the chosen stream's volume) from 0 to 1 over [FADE_IN_MS]. */
    private fun fadeIn(mp: MediaPlayer, start: Long) {
        val fraction = ((System.currentTimeMillis() - start).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        if (player !== mp) return
        mp.setVolume(fraction, fraction)
        if (fraction < 1f) handler.postDelayed({ fadeIn(mp, start) }, FADE_STEP_MS)
    }

    private fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        val audio = getSystemService(AudioManager::class.java)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        level?.restore(audio)
        level = null
    }

    /** Stops at once, leaving the Adhan Alert notification in place without Stop. */
    private fun finish() {
        stopPlayback()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notification?.post(this)
        notification = null
        playingId = null
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        playingId = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdhanPlayer"
        const val ACTION_PLAY = "app.mawaqeet.action.PLAY_ADHAN"
        const val ACTION_STOP = "app.mawaqeet.action.STOP_ADHAN"
        const val FADE_IN_MS = 3_000L
        private const val FADE_STEP_MS = 100L

        /** The notification id of the alert now playing, or null: "Mark as prayed" stops only its own adhan. */
        @Volatile
        var playingId: Int? = null
            private set

        fun playIntent(context: Context, n: AdhanNotification, sound: AlertSound, volume: AdhanVolume = AdhanVolume.ALARM): Intent =
            Intent(context, AdhanPlayerService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(AlertReceiver.EXTRA_PRAYER, n.prayer.name)
                .putExtra(AlertReceiver.EXTRA_JUMUAH, n.isJumuah)
                .putExtra(AlertReceiver.EXTRA_REQUEST_CODE, n.id)
                .putExtra(AlertReceiver.EXTRA_SOUND, encodeAlertSound(sound))
                .putExtra(AlertReceiver.EXTRA_PRAYER_DAY, n.prayerDay?.toString())
                .putExtra(AlertReceiver.EXTRA_FULL_SCREEN, n.fullScreen)
                .putExtra(AlertReceiver.EXTRA_ADHAN_VOLUME, volume.name)

        fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, AdhanPlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
