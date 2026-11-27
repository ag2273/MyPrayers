package app.mawaqeet.ui.adhkar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Misbaha's coke-wood beads (1.5), painted pixel by pixel once and kept for the process.
 *
 * Each bead is cut from a log: a 3D field of growth rings round a log axis turned at random per bead, warped by noise,
 * with fine fibres, is sampled on the bead's surface and lit like polished varnish (light from the upper left, a sharp
 * highlight and a soft sheen). The divider is an olive-shaped bead with silver tips. A soft contact shadow is baked
 * under each bead. [WOOD_VARIANTS] cut-outs give the string beads their own grain.
 */
internal object WoodBeadArt {
    const val WOOD_VARIANTS = 10
    private const val DIVIDER_VARIANTS = 3

    /** The shadow's margin round the bead, as a part of the bead's size: draw the image this much larger. */
    const val SHADOW_PAD = .18f

    private val cache = HashMap<String, ImageBitmap>()

    /** A round bead [sizePx] across (without the shadow margin), grain [seed]. */
    fun bead(sizePx: Int, seed: Int, dark: Boolean): ImageBitmap =
        cached("b$sizePx/${seed % WOOD_VARIANTS}/$dark") { paint(sizePx, sizePx, seed % WOOD_VARIANTS, divider = false, dark) }

    /** The divider: [heightPx] tall along the string, two thirds as wide. */
    fun divider(heightPx: Int, seed: Int, dark: Boolean): ImageBitmap {
        val width = (heightPx * DIVIDER_WIDTH).roundToInt()
        return cached("d$heightPx/${seed % DIVIDER_VARIANTS}/$dark") { paint(width, heightPx, 1000 + seed % DIVIDER_VARIANTS, divider = true, dark) }
    }

    /** The divider's width over its length. */
    const val DIVIDER_WIDTH = .62f

    private fun cached(key: String, make: () -> Bitmap): ImageBitmap = synchronized(cache) { cache[key] }
        ?: make().asImageBitmap().also { synchronized(cache) { cache[key] = it } }

    private fun paint(w: Int, h: Int, seed: Int, divider: Boolean, dark: Boolean): Bitmap {
        val px = max(w, h)
        val a = w.toFloat() / px
        val b = h.toFloat() / px
        val c3 = min(a, b)
        val m = rotation(seed + 11)
        val tone = hash(seed, 5, 5, 9)
        val light = floatArrayOf(124 + tone * 26, 64 + tone * 14, 34 + tone * 8)
        val mid = floatArrayOf(96f, 46f, 24f)
        val darkWood = floatArrayOf(52f, 22f, 10f)
        val freq = 3.4f + hash(seed, 2, 2, 2) * 1.6f
        val off = hash(seed, 3, 3, 3) * 3
        val lx = -.45f / 1.0f; val ly = -.62f; val lz = .64f
        val ln = sqrt(lx * lx + ly * ly + lz * lz)
        val l = floatArrayOf(lx / ln, ly / ln, lz / ln)
        val hn = sqrt(l[0] * l[0] + l[1] * l[1] + (l[2] + 1) * (l[2] + 1))
        val hv = floatArrayOf(l[0] / hn, l[1] / hn, (l[2] + 1) / hn)
        val pixels = IntArray(px * px)
        val col = FloatArray(3)
        for (j in 0 until px) for (i in 0 until px) {
            val u = ((i + .5f) / px * 2 - 1) * 1.02f
            val v = ((j + .5f) / px * 2 - 1) * 1.02f
            val d2 = (u / a).pow(2) + (v / b).pow(2)
            if (d2 >= 1) continue
            val z = c3 * sqrt(1 - d2)
            var nx = u / (a * a); var ny = v / (b * b); var nz = z / (c3 * c3)
            val nn = sqrt(nx * nx + ny * ny + nz * nz); nx /= nn; ny /= nn; nz /= nn
            val metal = divider && abs(v / b) > .86f
            if (metal) {
                val s = .55f + .45f * max(0f, nx * l[0] + ny * l[1] + nz * l[2])
                col[0] = 186 * s; col[1] = 190 * s; col[2] = 192 * s
            } else {
                val q0 = m[0] * u + m[1] * v + m[2] * z
                val q1 = m[3] * u + m[4] * v + m[5] * z
                val q2 = m[6] * u + m[7] * v + m[8] * z
                val warp = fbm(q0 * .7f, q1 * 1.1f, q2 * 1.1f, seed, 3) - .5f
                val rr = hypot(q1 + 1.6f, q2 + .4f) * freq + off + warp * 2.2f + (fbm(q0 * 3, q1 * 3, q2 * 3, seed + 41, 2) - .5f) * .22f
                val t = rr - floor(rr)
                // earlywood darkening into latewood, then a sharp edge to the next ring
                val late = smooth(min(1f, t / .82f)) * (1 - smooth(max(0f, (t - .82f) / .18f)))
                val fibre = fbm(q0 * 2, q1 * 34, q2 * 34, seed + 99, 2)
                val g = max(0f, min(1f, late * .58f + (fibre - .5f) * .38f))
                val blotch = fbm(q0 * .8f, q1 * .8f, q2 * .8f, seed + 5, 2)
                for (n in 0..2) {
                    val base = light[n] + (mid[n] - light[n]) * (.35f + blotch * .5f)
                    col[n] = base + (darkWood[n] - base) * g
                }
            }
            val diff = max(0f, nx * l[0] + ny * l[1] + nz * l[2])
            val lit = .3f + .82f * diff + max(0f, ny) * .12f
            val nh = max(0f, nx * hv[0] + ny * hv[1] + nz * hv[2])
            val spec = if (metal) nh.pow(30) * 230 + nh.pow(14) * 34 else nh.pow(90) * 200 + nh.pow(14) * 34 + nh.pow(4) * 10
            val fres = (1 - nz).pow(3) * 26
            val hole = if (divider) 0f else exp(-(u / .16f).pow(2)) * smooth(max(0f, min(1f, (abs(v) - .82f) / .16f)))
            val alpha = max(0f, min(1f, (1 - sqrt(d2)) * px * .5f))
            val rgb = IntArray(3) { n -> max(0f, min(255f, (col[n] * lit + spec + fres) * (1 - .7f * hole))).roundToInt() }
            // premultiplied by Bitmap.setPixels from straight ARGB
            pixels[j * px + i] = ((alpha * 255).roundToInt() shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
        }
        val bead = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        bead.setPixels(pixels, 0, px, 0, 0, px, px)
        // The final image: a soft contact shadow, then the bead, with a margin for the shadow.
        val pad = (px * SHADOW_PAD).roundToInt()
        val out = Bitmap.createBitmap(px + 2 * pad, px + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cx = out.width / 2f
        val cy = out.height / 2f + px * .05f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val edge = if (dark) 0x99000000.toInt() else 0x61281408
            shader = RadialGradient(0f, 0f, 1f, intArrayOf(edge, edge, 0), floatArrayOf(0f, .78f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(px * a / 2 * 1.12f, px * b / 2 * 1.12f)
        canvas.drawCircle(0f, 0f, 1f, shadow)
        canvas.restore()
        canvas.drawBitmap(bead, pad.toFloat(), pad.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        bead.recycle()
        return out
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    private fun hash(x: Int, y: Int, z: Int, s: Int): Float {
        var h = (x * 374761393) xor (y * 668265263) xor (z * 1274126177) xor (s * 1442695041)
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967295f
    }

    private fun noise(x: Float, y: Float, z: Float, s: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val zi = floor(z).toInt()
        val fx = smooth(x - xi); val fy = smooth(y - yi); val fz = smooth(z - zi)
        fun c(i: Int, j: Int, k: Int) = hash(xi + i, yi + j, zi + k, s)
        fun lerp(p: Float, q: Float, t: Float) = p + (q - p) * t
        return lerp(
            lerp(lerp(c(0, 0, 0), c(1, 0, 0), fx), lerp(c(0, 1, 0), c(1, 1, 0), fx), fy),
            lerp(lerp(c(0, 0, 1), c(1, 0, 1), fx), lerp(c(0, 1, 1), c(1, 1, 1), fx), fy),
            fz,
        )
    }

    private fun fbm(x: Float, y: Float, z: Float, s: Int, octaves: Int = 4): Float {
        var v = 0f; var amp = .5f; var f = 1f
        for (i in 0 until octaves) { v += amp * noise(x * f, y * f, z * f, s + i * 17); amp *= .5f; f *= 2.03f }
        return v
    }

    /** A rotation (row-major 3×3) from [s]: the log axis the bead was cut along. */
    private fun rotation(s: Int): FloatArray {
        fun r(k: Int) = hash(s, k, 7, 3) * 2 * PI.toFloat()
        val ca = cos(r(1)); val sa = sin(r(1)); val cb = cos(r(2)); val sb = sin(r(2)); val cc = cos(r(3)); val sc = sin(r(3))
        return floatArrayOf(
            cb * cc, -cb * sc, sb,
            sa * sb * cc + ca * sc, -sa * sb * sc + ca * cc, -sa * cb,
            -ca * sb * cc + sa * sc, ca * sb * sc + sa * cc, ca * cb,
        )
    }
}
