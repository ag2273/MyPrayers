package app.mawaqeet.core

import java.io.File
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdhkarAudioTest {
    private val pack = AdhkarAudioPack.parse(
        """
        # mawaqeet-adhkar-audio-v1
        base	https://example.com/a/v1/
        file	one	aaa.bin	100	ABCD
        file	two	bbb.bin	50	ef01
        dhikr	morning	first	one
        dhikr	evening	first	one
        dhikr	after-prayer	tasbih	two
        dhikr	someday	unknown	two
        """.trimIndent(),
    )

    @Test
    fun one_recording_can_read_several_adhkar() {
        assertEquals("one", pack.recordingFor(AdhkarSetId.MORNING, "first"))
        assertEquals("one", pack.recordingFor(AdhkarSetId.EVENING, "first"))
        assertEquals("two", pack.recordingFor(AdhkarSetId.AFTER_PRAYER, "tasbih"))
        assertNull(pack.recordingFor(AdhkarSetId.MORNING, "tasbih"))
    }

    @Test
    fun files_give_their_url_size_and_lowercase_hash() {
        val one = assertNotNull(pack.file("one"))
        assertEquals("https://example.com/a/v1/aaa.bin", pack.url(one))
        assertEquals("abcd", one.sha256)
        assertEquals(150, pack.totalBytes)
    }

    @Test
    fun a_dhikr_row_without_its_file_is_refused() {
        assertFailsWith<IllegalArgumentException> {
            AdhkarAudioPack.parse("# mawaqeet-adhkar-audio-v1\nbase\thttps://x/\ndhikr\tmorning\tfirst\tnone")
        }
    }

    @Test
    fun decrypt_undoes_encrypt_only_with_the_same_key_and_name() {
        val key = ByteArray(32) { it.toByte() }
        val plain = "صوت".toByteArray()
        val blob = AdhkarAudioCrypto.encrypt(key, "one", plain, ByteArray(12) { 7 })
        assertContentEquals(plain, AdhkarAudioCrypto.decrypt(key, "one", blob))
        assertFailsWith<AEADBadTagException> { AdhkarAudioCrypto.decrypt(key, "two", blob) }
        assertFailsWith<AEADBadTagException> { AdhkarAudioCrypto.decrypt(ByteArray(32), "one", blob) }
    }

    /** The shipped asset: every bundled dhikr has a recording, and every recording a file. */
    @Test
    fun the_bundled_pack_reads_every_bundled_dhikr() {
        val assets = File("../app/src/main/assets")
        val shipped = File(assets, "adhkar-audio.tsv").inputStream().use(AdhkarAudioPack::read)
        val catalog = File(assets, "adhkar.tsv").inputStream().use(AdhkarCatalog::read)
        catalog.sets.forEach { set ->
            set.adhkar.forEach { dhikr ->
                assertNotNull(shipped.recordingFor(set.id, dhikr.id), "${set.id.key}/${dhikr.id} has no recording")
            }
        }
        assertEquals("https://mawaqeet.nileorbit.com/a/v1/", shipped.base)
    }
}
