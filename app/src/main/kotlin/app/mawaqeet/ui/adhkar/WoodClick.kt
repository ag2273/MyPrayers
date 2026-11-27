package app.mawaqeet.ui.adhkar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The soft wooden «تك» when a bead strikes the pile (1.5); the divider knocks lower. Both sounds are made here (a
 * short noise burst through a band-pass, over a quick low tone), written once to the cache as WAV and played by a
 * SoundPool so fast counting can overlap. Silent while the phone is on silent or vibrate.
 */
internal class WoodClick(private val context: Context) : AutoCloseable {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val bead = pool.load(wav("misbaha-bead-v1.wav", divider = false).path, 1)
    private val divider = pool.load(wav("misbaha-divider-v1.wav", divider = true).path, 1)
    private val audio = context.getSystemService(AudioManager::class.java)

    fun play(isDivider: Boolean) {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(if (isDivider) divider else bead, .9f, .9f, 1, 0, 1f + (Random.nextFloat() - .5f) * .08f)
    }

    override fun close() = pool.release()

    private fun wav(name: String, divider: Boolean): File {
        val file = File(context.cacheDir, name)
        if (file.length() > 44) return file
        val pcm = synth(divider)
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun int(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff); out.write(v shr 16 and 0xff); out.write(v shr 24 and 0xff) }
            fun short(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff) }
            out.writeBytes("RIFF"); int(36 + pcm.size * 2); out.writeBytes("WAVE")
            out.writeBytes("fmt "); int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
            out.writeBytes("data"); int(pcm.size * 2)
            val bytes = ByteArray(pcm.size * 2)
            pcm.forEachIndexed { i, s -> bytes[2 * i] = (s.toInt() and 0xff).toByte(); bytes[2 * i + 1] = (s.toInt() shr 8).toByte() }
            out.write(bytes)
        }
        return file
    }

    companion object {
        private const val RATE = 44_100

        /** The click as 16-bit mono samples. */
        internal fun synth(divider: Boolean): ShortArray {
            val n = (RATE * .07).toInt()
            val random = Random(if (divider) 7 else 3)
            // RBJ band-pass (constant peak gain) round the wood's ring
            val f0 = if (divider) 900.0 else 1750.0
            val q = 6.0
            val w0 = 2 * PI * f0 / RATE
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            val b0 = alpha / a0; val b2 = -alpha / a0
            val a1 = -2 * kotlin.math.cos(w0) / a0; val a2 = (1 - alpha) / a0
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
            val tone = if (divider) 320.0 else 540.0
            return ShortArray(n) { i ->
                val t = i.toDouble() / RATE
                val x = (random.nextDouble() * 2 - 1) * exp(-t / .006)
                val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x; y2 = y1; y1 = y
                val knock = sin(2 * PI * tone * t) * .12 * exp(-t / .012)
                val s = (y * 1.6 + knock).coerceIn(-1.0, 1.0)
                (s * 32_000).toInt().toShort()
            }
        }
    }
}
