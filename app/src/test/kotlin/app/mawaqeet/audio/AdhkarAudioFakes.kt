package app.mawaqeet.audio

import app.mawaqeet.core.AdhkarAudioCrypto
import app.mawaqeet.core.AdhkarAudioPack
import java.io.File
import java.io.IOException

/** A test pack key; real builds get theirs from a local file outside the repo. */
internal val TEST_KEY = ByteArray(32) { (it * 7).toByte() }

/** "Recordings" of a few bytes each, encrypted the way the site serves them. */
internal class FakeSite(recordings: Map<String, ByteArray>, readers: List<Triple<String, String, String>>) {
    val plain = recordings
    val served: MutableMap<String, ByteArray> = recordings.mapValues { (name, bytes) ->
        AdhkarAudioCrypto.encrypt(TEST_KEY, name, bytes, ByteArray(12) { name.length.toByte() })
    }.mapKeys { "${it.key}.bin" }.toMutableMap()

    val pack: AdhkarAudioPack = AdhkarAudioPack.parse(
        buildString {
            appendLine("# mawaqeet-adhkar-audio-v1")
            appendLine("base\thttps://example.test/a/v1/")
            served.forEach { (file, bytes) ->
                appendLine("file\t${file.removeSuffix(".bin")}\t$file\t${bytes.size}\t${AdhkarAudioCrypto.sha256(bytes)}")
            }
            readers.forEach { (set, dhikr, recording) -> appendLine("dhikr\t$set\t$dhikr\t$recording") }
        },
    )

    var requests = 0

    /** Fails each file's first request half-way when [cutOff] is set, to check the download carries on. */
    var cutOff = false
    private val cut = mutableSetOf<String>()

    val fetcher = Fetcher { url, into ->
        requests++
        val bytes = served.getValue(url.substringAfterLast('/'))
        val from = into.length().toInt()
        if (cutOff && cut.add(url)) {
            into.appendBytes(bytes.copyOfRange(from, bytes.size / 2))
            throw IOException("cut off")
        }
        into.appendBytes(bytes.copyOfRange(from, bytes.size))
    }
}

/** Stands in for the Android Keystore: XOR with a fixed byte plus a marker, so another "phone" can't open it. */
internal class FakeCipher(private val phone: Byte = 1) : DeviceCipher {
    override fun seal(plain: ByteArray) = byteArrayOf(phone) + plain.map { (it.toInt() xor 0x5a).toByte() }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed[0] == phone) { "Sealed on another phone" }
        return sealed.drop(1).map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    }
}

internal fun tempDir(): File = kotlin.io.path.createTempDirectory("adhkar-audio").toFile()
