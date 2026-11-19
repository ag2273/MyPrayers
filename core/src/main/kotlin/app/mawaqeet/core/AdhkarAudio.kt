package app.mawaqeet.core

import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * One recording of the Adhkar Audio pack as the site serves it: [recording] names it in the app, [file] on the site
 * (an opaque name, so the site's listing says nothing about what each file holds), with the encrypted file's
 * [bytes] and [sha256] to check a download against.
 */
data class PackFile(val recording: String, val file: String, val bytes: Long, val sha256: String)

/**
 * The Adhkar Audio pack (asset `adhkar-audio.tsv`, written by `tools/adhkar-audio/build_pack.py`): where the encrypted
 * recordings live, and which recording reads which dhikr. Recordings are not bundled in the app: the user downloads
 * the pack once (see the app's AdhkarAudioStore).
 *
 * The file format: a first line starting with `# mawaqeet-adhkar-audio-v1`, a `base` line with the URL the files sit
 * under, then tab-separated rows of two kinds:
 *   `file`  recording, file name on the site, encrypted size in bytes, SHA-256 of the encrypted file (hex)
 *   `dhikr` set key, dhikr id, recording
 * One recording may read several adhkar (the same words in the morning and the evening). Blank lines and lines
 * starting with `#` are skipped, and so are rows of a kind this version doesn't know.
 */
class AdhkarAudioPack(val base: String, val files: List<PackFile>, private val readers: Map<Pair<AdhkarSetId, String>, String>) {
    private val byRecording = files.associateBy { it.recording }

    init {
        require(files.size == byRecording.size) { "Duplicate recording in the Adhkar Audio pack" }
        readers.values.forEach { require(it in byRecording) { "No file for recording $it" } }
    }

    /** The recording that reads [dhikr] of [set], or null when it has none. */
    fun recordingFor(set: AdhkarSetId, dhikr: String): String? = readers[set to dhikr]

    fun file(recording: String): PackFile? = byRecording[recording]

    /** Every file's encrypted size together: what the one-time download costs. */
    val totalBytes: Long get() = files.sumOf { it.bytes }

    /** Where [file] is downloaded from. */
    fun url(file: PackFile): String = base.trimEnd('/') + "/" + file.file

    companion object {
        private const val HEADER = "# mawaqeet-adhkar-audio-v1"

        fun read(input: InputStream): AdhkarAudioPack = parse(input.bufferedReader(Charsets.UTF_8).readText())

        fun parse(text: String): AdhkarAudioPack {
            val lines = text.lines()
            require(lines.firstOrNull()?.startsWith(HEADER) == true) { "Not a $HEADER file" }
            var base: String? = null
            val files = mutableListOf<PackFile>()
            val readers = mutableMapOf<Pair<AdhkarSetId, String>, String>()
            lines.drop(1).filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val c = line.split('\t').map(String::trim)
                when (c[0]) {
                    "base" -> base = c[1]
                    "file" -> {
                        require(c.size == 5) { "Expected 5 columns: $line" }
                        files += PackFile(c[1], c[2], c[3].toLong(), c[4].lowercase())
                    }
                    "dhikr" -> {
                        require(c.size == 4) { "Expected 4 columns: $line" }
                        val set = AdhkarSetId.ofKey(c[1]) ?: return@forEach
                        readers[set to c[2]] = c[3]
                    }
                }
            }
            return AdhkarAudioPack(requireNotNull(base) { "No base URL" }, files, readers)
        }
    }
}

/**
 * The pack's encryption: each file on the site is a 12-byte nonce followed by the recording encrypted with
 * AES-256-GCM under the pack key, with the recording's name as associated data (so files can't be swapped). The key
 * is never in the public repo: release builds get it from a local property, the way signing works.
 */
object AdhkarAudioCrypto {
    private const val NONCE = 12
    private const val TAG_BITS = 128

    /** The recording [encrypted] holds, or an exception when the key, the name or the bytes don't match. */
    fun decrypt(key: ByteArray, recording: String, encrypted: ByteArray): ByteArray {
        require(key.size == 32) { "The pack key is 32 bytes" }
        require(encrypted.size > NONCE) { "Too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, encrypted, 0, NONCE))
        cipher.updateAAD(recording.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted, NONCE, encrypted.size - NONCE)
    }

    /** What the pack builder does; the app only decrypts. Here so the round trip can be tested. */
    fun encrypt(key: ByteArray, recording: String, plain: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == NONCE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(recording.toByteArray(Charsets.UTF_8))
        return nonce + cipher.doFinal(plain)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
