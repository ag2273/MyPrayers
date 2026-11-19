package app.mawaqeet.audio

import app.mawaqeet.core.AdhkarAudioCrypto
import app.mawaqeet.core.AdhkarAudioPack
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.PackFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException

/** Where the one-time download of the Adhkar Audio pack stands. */
data class AudioPackState(
    /** Recordings on the phone, ready to play. */
    val ready: Int,
    val total: Int,
    val phase: Phase,
    /** Bytes of the pack on the phone or downloaded so far, of [totalBytes]. */
    val doneBytes: Long,
    val totalBytes: Long,
) {
    enum class Phase { IDLE, WAITING, DOWNLOADING, FAILED }

    val complete: Boolean get() = ready == total
    val started: Boolean get() = ready > 0
}

/** Seals the recordings on this phone with a key that never leaves it (the Android Keystore), so copies are useless. */
interface DeviceCipher {
    fun seal(plain: ByteArray): ByteArray

    fun open(sealed: ByteArray): ByteArray
}

/** Downloads [url] into [into], appending from its current length when it already holds part of the file. */
fun interface Fetcher {
    @Throws(IOException::class)
    fun fetch(url: String, into: File)
}

/**
 * The Adhkar Audio on this phone. [download] fetches what is missing from the site, file by file: each one is
 * checked against the pack's size and SHA-256, opened with the pack key, sealed again with this phone's own key
 * ([DeviceCipher]) and only then saved, so a half-downloaded or swapped file never plays and no plain recording is
 * ever written. A file cut off half-way carries on from where it stopped. [load] opens a recording into memory to play.
 *
 * Files live in [dir] (app-private, kept out of backups). One instance per process.
 */
class AdhkarAudioStore(
    private val pack: AdhkarAudioPack,
    private val packKey: ByteArray,
    private val cipher: DeviceCipher,
    private val dir: File,
    private val fetcher: Fetcher,
) {
    private val _state = MutableStateFlow(scan(AudioPackState.Phase.IDLE))
    val state: StateFlow<AudioPackState> = _state.asStateFlow()

    private fun saved(recording: String) = File(dir, "$recording.sealed")

    private fun partial(file: PackFile) = File(dir, "${file.recording}.part")

    private fun scan(phase: AudioPackState.Phase): AudioPackState {
        val ready = pack.files.filter { saved(it.recording).isFile }
        return AudioPackState(
            ready = ready.size,
            total = pack.files.size,
            phase = phase,
            doneBytes = ready.sumOf { it.bytes },
            totalBytes = pack.totalBytes,
        )
    }

    fun recordingFor(set: AdhkarSetId, dhikr: String): String? = pack.recordingFor(set, dhikr)

    fun isReady(recording: String): Boolean = saved(recording).isFile

    /** The download is queued and waits for a network. */
    fun markWaiting() = _state.update { if (it.complete) it else it.copy(phase = AudioPackState.Phase.WAITING) }

    /** The download stopped without finishing: paused by the user, or failed for good. */
    fun markStopped(failed: Boolean) = _state.update {
        scan(if (failed && !it.complete) AudioPackState.Phase.FAILED else AudioPackState.Phase.IDLE)
    }

    /**
     * Downloads every recording not yet on the phone. Throws [IOException] when the network fails (call again to carry
     * on) and [SecurityException] when a file doesn't match the pack (it is thrown away and fetched afresh next time).
     */
    suspend fun download() {
        dir.mkdirs()
        _state.value = scan(AudioPackState.Phase.DOWNLOADING)
        for (file in pack.files) {
            if (isReady(file.recording)) continue
            currentCoroutineContext().ensureActive()
            val part = partial(file)
            if (part.length() > file.bytes) part.delete()
            if (part.length() < file.bytes) fetcher.fetch(pack.url(file), part)
            val encrypted = part.readBytes()
            if (encrypted.size.toLong() != file.bytes || AdhkarAudioCrypto.sha256(encrypted) != file.sha256) {
                part.delete()
                throw SecurityException("${file.recording} doesn't match the pack")
            }
            val sealed = cipher.seal(AdhkarAudioCrypto.decrypt(packKey, file.recording, encrypted))
            val tmp = File(dir, "${file.recording}.tmp")
            tmp.writeBytes(sealed)
            if (!tmp.renameTo(saved(file.recording))) throw IOException("Can't save ${file.recording}")
            part.delete()
            _state.value = scan(AudioPackState.Phase.DOWNLOADING)
        }
        _state.value = scan(AudioPackState.Phase.IDLE)
    }

    /**
     * [recording] opened into memory to play, or null when it isn't on the phone. A file this phone can't open (one
     * restored from another phone, say) is deleted, so it shows as missing and the next download fetches it again.
     */
    fun load(recording: String): ByteArray? {
        val file = saved(recording)
        if (!file.isFile) return null
        return try {
            cipher.open(file.readBytes())
        } catch (e: Exception) {
            file.delete()
            _state.value = scan(_state.value.phase)
            null
        }
    }

    /** Removes every recording and half-downloaded file. */
    fun delete() {
        dir.deleteRecursively()
        _state.value = scan(AudioPackState.Phase.IDLE)
    }
}
