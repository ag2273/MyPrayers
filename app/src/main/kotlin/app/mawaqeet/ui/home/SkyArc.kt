package app.mawaqeet.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

/**
 * The day's sky from Fajr to Isha (unslop pass, 2026-11-10): the sun's path drawn from today's own times, rising at
 * sunrise, highest at Dhuhr and setting at Maghrib, a little below the horizon at either end. It is the Next Prayer
 * card's one rich moment and replaces its plain progress bar.
 *
 * Positions are fractions: [x] runs 0 (Fajr) to 1 (Isha) along the day, [y] 0 (top) to 1 (bottom).
 */
internal class SkyPath(
    private val fajr: Instant,
    private val sunrise: Instant,
    private val maghrib: Instant,
    private val isha: Instant,
) {
    private val span = (isha.epochSecond - fajr.epochSecond).coerceAtLeast(1).toFloat()

    /** Where [time] falls along the day, 0 at Fajr and 1 at Isha, or null outside it. */
    fun x(time: Instant): Float? {
        val f = (time.epochSecond - fajr.epochSecond) / span
        return f.takeIf { it in 0f..1f }
    }

    /** How much of the day has passed at [time], 0 before Fajr to 1 after Isha. */
    fun passed(time: Instant): Float = ((time.epochSecond - fajr.epochSecond) / span).coerceIn(0f, 1f)

    /** The height of the path at [time]: [HORIZON] at sunrise and Maghrib, [TOP] at the middle of the day. */
    fun y(time: Instant): Float {
        val t = time.epochSecond
        val rise = sunrise.epochSecond
        val set = maghrib.epochSecond
        return when {
            t in rise..set -> HORIZON - (HORIZON - TOP) * sin(PI * (t - rise) / (set - rise).coerceAtLeast(1)).toFloat()
            t < rise -> HORIZON + DIP * sin(PI / 2 * (rise - t) / (rise - fajr.epochSecond).coerceAtLeast(1)).toFloat()
            else -> HORIZON + DIP * sin(PI / 2 * (t - set) / (isha.epochSecond - set).coerceAtLeast(1)).toFloat()
        }
    }

    /** The point at fraction [f] of the day. */
    fun at(f: Float): Offset {
        val time = Instant.ofEpochSecond(fajr.epochSecond + (span * f.coerceIn(0f, 1f)).toLong())
        return Offset(f.coerceIn(0f, 1f), y(time))
    }

    companion object {
        const val TOP = 0.14f
        const val HORIZON = 0.74f
        const val DIP = 0.18f
    }
}

/**
 * Draws [sky] on the deep card in [ink]: the hairline horizon, the whole path dashed and faint, the part already
 * passed solid, the next Prayer's point in [gold] and "now" as a ring on the [card] colour. Laid out in the reading direction: Fajr at the
 * start (the right in Arabic).
 */
@Composable
internal fun SkyArc(sky: SkyPath, now: Instant, next: Instant?, ink: Color, gold: Color, card: Color, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val pad = 8.dp.toPx()
        val w = size.width - 2 * pad
        fun point(f: Float): Offset {
            val p = sky.at(f)
            val x = pad + w * (if (rtl) 1f - p.x else p.x)
            return Offset(x, size.height * p.y)
        }
        fun path(to: Float): Path = Path().apply {
            val steps = 64
            for (i in 0..steps) {
                val o = point(to * i / steps)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        val horizon = size.height * SkyPath.HORIZON
        drawLine(ink.copy(alpha = 0.16f), Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 1.dp.toPx())
        drawPath(
            path(1f),
            ink.copy(alpha = 0.24f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))),
        )
        val nowAt = sky.x(now)
        val passed = sky.passed(now)
        if (passed > 0f) drawPath(path(passed), ink.copy(alpha = 0.72f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        next?.let(sky::x)?.let { drawCircle(gold, radius = 4.dp.toPx(), center = point(it)) }
        nowAt?.let {
            drawCircle(card, radius = 6.dp.toPx(), center = point(it))
            drawCircle(ink, radius = 6.dp.toPx(), center = point(it), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/**
 * The hairline eight-point star of the Next Prayer surface: two squares turned 45° apart and a small ring, faint, cut by
 * the card's corner. Only the Next Prayer card and widgets carry it.
 */
internal fun DrawScope.hairlineStar(center: Offset, side: Float, color: Color) {
    val stroke = Stroke(width = 1.dp.toPx())
    val square = Path().apply {
        moveTo(center.x - side / 2, center.y - side / 2)
        lineTo(center.x + side / 2, center.y - side / 2)
        lineTo(center.x + side / 2, center.y + side / 2)
        lineTo(center.x - side / 2, center.y + side / 2)
        close()
    }
    drawPath(square, color, style = stroke)
    rotate(45f, center) { drawPath(square, color, style = stroke) }
    drawCircle(color, radius = side * 0.3f, center = center, style = stroke)
}
