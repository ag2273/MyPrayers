package app.mawaqeet.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/** The one-time download of the Adhkar Audio: checked, sealed to the phone, resumable, never stored in the clear. */
class AdhkarAudioStoreTest {
    private val site = FakeSite(
        mapOf("one" to "first recording".toByteArray(), "two" to "second recording, longer".toByteArray()),
        listOf(Triple("morning", "a", "one"), Triple("evening", "a", "one"), Triple("morning", "b", "two")),
    )
    private val dir = File(tempDir(), "adhkar-audio")

    private fun store(cipher: DeviceCipher = FakeCipher()) = AdhkarAudioStore(site.pack, TEST_KEY, cipher, dir, site.fetcher)

    @Test
    fun `a download saves every recording sealed to the phone, and they open to play`() = runBlocking {
        val store = store()
        assertEquals(0, store.state.value.ready)
        assertFalse(store.state.value.complete)

        store.download()

        val state = store.state.value
        assertTrue(state.complete)
        assertEquals(state.totalBytes, state.doneBytes)
        assertArrayEquals(site.plain["one"], store.load("one"))
        assertArrayEquals(site.plain["two"], store.load("two"))
        // What is on the phone is neither the plain recording nor the file the site serves.
        dir.listFiles()!!.forEach { file ->
            assertTrue(file.name, file.name.endsWith(".sealed"))
            assertFalse(String(file.readBytes()).contains("recording"))
        }
    }

    @Test
    fun `a cut-off download carries on from where each file stopped`() = runBlocking {
        site.cutOff = true
        val store = store()
        val failures = (1..3).count { runCatching { store.download() }.exceptionOrNull() is IOException }
        // Each of the two files was cut off once, and the third run finished both from where they stopped.
        assertEquals(2, failures)
        assertTrue(store.state.value.complete)
        assertEquals(4, site.requests)
        assertArrayEquals(site.plain["two"], store.load("two"))
    }

    @Test
    fun `a file that doesn't match the pack is thrown away and never saved`() = runBlocking {
        site.served["one.bin"] = site.served.getValue("one.bin").copyOf().also { it[20] = (it[20] + 1).toByte() }
        val store = store()
        val failed = runCatching { store.download() }.exceptionOrNull()
        assertTrue(failed is SecurityException)
        assertFalse(store.isReady("one"))
        assertFalse(File(dir, "one.part").exists())
    }

    @Test
    fun `recordings sealed on another phone are dropped and download again`() = runBlocking {
        store(FakeCipher(phone = 1)).download()
        val elsewhere = store(FakeCipher(phone = 2))
        assertTrue(elsewhere.state.value.complete)
        assertNull(elsewhere.load("one"))
        assertFalse(elsewhere.isReady("one"))
        assertEquals(1, elsewhere.state.value.ready)
        elsewhere.download()
        assertArrayEquals(site.plain["one"], elsewhere.load("one"))
    }

    @Test
    fun `one recording reads every dhikr it belongs to, and delete removes them all`() = runBlocking {
        val store = store()
        assertEquals("one", store.recordingFor(app.mawaqeet.core.AdhkarSetId.MORNING, "a"))
        assertEquals("one", store.recordingFor(app.mawaqeet.core.AdhkarSetId.EVENING, "a"))
        store.download()
        store.delete()
        assertEquals(0, store.state.value.ready)
        assertFalse(dir.exists())
    }
}
