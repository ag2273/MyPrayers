package app.mawaqeet.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the player is doing: which [recording], whether it is [playing], where it is, and how many have [finished]. */
data class PlayerState(
    val recording: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    /** Counts every recording that played to its end, so a screen can react once to each (auto-next). */
    val finished: Int = 0,
    val lastFinished: String? = null,
)

/**
 * Plays one dhikr's recording at a time, as speech on the media volume. The recording is opened into memory by
 * [load] and played from there, so no plain file is ever on the phone. It takes audio focus: a call or the adhan
 * pauses it, and so do unplugged headphones.
 */
class AdhkarAudioPlayer(private val context: Context, private val load: (String) -> ByteArray?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change -> if (change < 0) pause() }, Handler(Looper.getMainLooper()))
        .build()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = pause()
    }
    private var noisyRegistered = false

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Plays [recording] from the start, or resumes it when it is the one paused. */
    fun play(recording: String) {
        val current = _state.value
        val mp = player
        if (mp != null && current.recording == recording && !current.playing) {
            if (requestFocus()) {
                mp.start()
                _state.value = current.copy(playing = true)
                tick()
            }
            return
        }
        release()
        _state.value = current.copy(recording = recording, playing = false, positionMs = 0, durationMs = 0)
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { load(recording) } ?: return@launch
            if (_state.value.recording != recording) return@launch
            val next = MediaPlayer()
            next.setAudioAttributes(attributes)
            next.setDataSource(BytesSource(bytes))
            next.setOnCompletionListener {
                val done = _state.value
                abandon()
                _state.value = done.copy(
                    playing = false,
                    positionMs = done.durationMs,
                    finished = done.finished + 1,
                    lastFinished = recording,
                )
            }
            next.setOnPreparedListener {
                if (_state.value.recording != recording || !requestFocus()) return@setOnPreparedListener
                it.start()
                _state.value = _state.value.copy(playing = true, durationMs = it.duration)
                tick()
            }
            player = next
            next.prepareAsync()
        }
    }

    fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) mp.pause()
        abandon()
        _state.value = _state.value.copy(playing = false, positionMs = mp.currentPosition)
    }

    /** Stops and forgets the recording (leaving the reader, deleting the audio). */
    fun stop() {
        release()
        _state.value = _state.value.copy(recording = null, playing = false, positionMs = 0, durationMs = 0)
    }

    private fun release() {
        ticker?.cancel()
        player?.release()
        player = null
        abandon()
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val mp = player ?: break
                if (!_state.value.playing) break
                _state.value = _state.value.copy(positionMs = mp.currentPosition)
                delay(200)
            }
        }
    }

    private fun requestFocus(): Boolean {
        val granted = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted && !noisyRegistered) {
            ContextCompat.registerReceiver(
                context,
                noisy,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
        return granted
    }

    private fun abandon() {
        audio.abandonAudioFocusRequest(focus)
        if (noisyRegistered) {
            context.unregisterReceiver(noisy)
            noisyRegistered = false
        }
    }

    /** A recording held in memory, handed to MediaPlayer without a file. */
    private class BytesSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val n = minOf(size, bytes.size - position.toInt())
            System.arraycopy(bytes, position.toInt(), buffer, offset, n)
            return n
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }
}
