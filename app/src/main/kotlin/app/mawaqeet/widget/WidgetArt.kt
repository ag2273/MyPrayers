package app.mawaqeet.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.annotation.ColorInt
import kotlin.math.ceil
import kotlin.math.max

/**
 * The fine drawings of the widget design board (2026-11-10) that shapes and text can't make: the progress line and
 * ring of the Next Prayer widget, its hairline eight-point star, and the "day path" of Today's Prayer Times. Each is a
 * small bitmap drawn at the screen's density, so hairlines stay sharp, and laid out in the app language's direction
 * ([rtl]: the start is on the right).
 */
internal object WidgetArt {
    private fun bitmap(context: Context, widthDp: Float, heightDp: Float, draw: Canvas.(density: Float) -> Unit): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(
            max(1, ceil(widthDp * density).toInt()),
            max(1, ceil(heightDp * density).toInt()),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(bitmap).draw(density)
        return bitmap
    }

    private fun stroke(@ColorInt color: Int, widthPx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = widthPx
        strokeCap = Paint.Cap.ROUND
    }

    private fun fill(@ColorInt color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** The height of the progress line, with its end dot. */
    const val LINE_HEIGHT_DP = 7f

    /**
     * A 3dp line filled from the start to [fraction] (0–1) with a 7dp dot at its end (when [dot]), the rest a track:
     * how far it is from the previous Prayer to the next.
     */
    fun progressLine(context: Context, widthDp: Float, fraction: Float, rtl: Boolean, @ColorInt fillColor: Int, @ColorInt track: Int, dot: Boolean = true): Bitmap =
        bitmap(context, widthDp, LINE_HEIGHT_DP) { d ->
            val f = fraction.coerceIn(0f, 1f)
            val w = width.toFloat()
            val y = height / 2f
            val line = 3f * d
            val r = if (dot) 3.5f * d else 0f
            val inset = max(r, line / 2)
            val at = inset + (w - 2 * inset) * f
            fun x(p: Float) = if (rtl) w - p else p
            drawLine(x(inset), y, x(w - inset), y, stroke(track, line))
            if (f > 0f) drawLine(x(inset), y, x(at), y, stroke(fillColor, line))
            if (dot) drawCircle(x(at), y, r, fill(fillColor))
        }

    /** A ring that fills clockwise from the top to [fraction], over a track (the 1×1 Next Prayer). */
    fun ring(context: Context, sizeDp: Float, fraction: Float, @ColorInt fillColor: Int, @ColorInt track: Int): Bitmap =
        bitmap(context, sizeDp, sizeDp) { d ->
            val line = 2.5f * d
            val box = RectF(line / 2, line / 2, width - line / 2, height - line / 2)
            drawArc(box, 0f, 360f, false, stroke(track, line))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(box, -90f, sweep, false, stroke(fillColor, line))
        }

    /**
     * A hairline eight-point star (two squares, one turned 45°, inside a circle around a small one), drawn
     * [sizeDp] across with its centre [centerX] dp in from the bitmap's end edge and [centerY] dp down; the bitmap
     * crops it, so it sits in a widget's end corner. [color] carries its own soft alpha.
     */
    fun star(context: Context, widthDp: Float, heightDp: Float, sizeDp: Float, centerX: Float, centerY: Float, rtl: Boolean, @ColorInt color: Int): Bitmap =
        bitmap(context, widthDp, heightDp) { d ->
            val paint = stroke(color, 0.8f * d * (sizeDp / 100f).coerceAtLeast(1f))
            val cx = (if (rtl) centerX else widthDp - centerX) * d
            val cy = centerY * d
            val unit = sizeDp * d / 100f
            val half = 28f * unit
            save()
            translate(cx, cy)
            drawRect(-half, -half, half, half, paint)
            rotate(45f)
            drawRect(-half, -half, half, half, paint)
            restore()
            drawCircle(cx, cy, 14f * unit, paint)
            drawCircle(cx, cy, 40f * unit, paint)
        }

    /** What the day path shows: where each of the five Prayers is in the day, and where the sun is now. */
    data class DayPath(
        /** The next Prayer's index among the five today, or 5 when they have all passed (after Isha). */
        val nextIndex: Int,
        /** How far it is from the previous Prayer to the next (0–1). */
        val fraction: Float,
    )

    /** The day path's colours. */
    data class DayPathColors(
        @ColorInt val past: Int,
        @ColorInt val future: Int,
        @ColorInt val horizon: Int,
        @ColorInt val sun: Int,
        @ColorInt val nextFill: Int,
    )

    /** Each Prayer's height on the path, as a share of its height: Fajr and Isha below the horizon, Dhuhr at the top. */
    private val HEIGHTS = floatArrayOf(46f / 56, 8f / 56, 20f / 56, 41f / 56, 51f / 56)
    private const val HORIZON = 41f / 56

    /**
     * The "day path" of Today's Prayer Times 4×2: a thin curve through the five Prayers, each above its column (five
     * equal columns across [widthDp]), a dotted horizon, solid up to the sun and dotted after it.
     */
    fun dayPath(context: Context, widthDp: Float, heightDp: Float, path: DayPath, rtl: Boolean, colors: DayPathColors): Bitmap =
        bitmap(context, widthDp, heightDp) { d ->
            val w = width.toFloat()
            val h = height.toFloat()
            val top = 5f * d
            val usable = h - top - 4f * d
            val points = (0 until 5).map { i ->
                val along = (i + 0.5f) * w / 5
                floatArrayOf(if (rtl) w - along else along, top + HEIGHTS[i] * usable)
            }
            val horizonY = top + HORIZON * usable
            drawLine(0f, horizonY, w, horizonY, stroke(colors.horizon, 1f * d).apply {
                strokeCap = Paint.Cap.BUTT
                pathEffect = DashPathEffect(floatArrayOf(2f * d, 3f * d), 0f)
            })
            val segments = (0 until 4).map { i ->
                val (x0, y0) = points[i].let { it[0] to it[1] }
                val (x1, y1) = points[i + 1].let { it[0] to it[1] }
                Path().apply {
                    moveTo(x0, y0)
                    cubicTo((x0 + x1) / 2, y0, (x0 + x1) / 2, y1, x1, y1)
                }
            }
            val dashed = stroke(colors.future, 1.2f * d).apply { pathEffect = DashPathEffect(floatArrayOf(3f * d, 3f * d), 0f) }
            segments.forEach { drawPath(it, dashed) }
            val solid = stroke(colors.past, 1.6f * d)
            // The sun sits on the segment that ends at the next Prayer; before Fajr at Fajr, after Isha at Isha.
            val sun = FloatArray(2)
            when {
                path.nextIndex <= 0 -> points[0].copyInto(sun)
                path.nextIndex >= 5 -> {
                    segments.forEach { drawPath(it, solid) }
                    points[4].copyInto(sun)
                }
                else -> {
                    segments.take(path.nextIndex - 1).forEach { drawPath(it, solid) }
                    val measure = PathMeasure(segments[path.nextIndex - 1], false)
                    val partial = Path()
                    val length = measure.length * path.fraction.coerceIn(0f, 1f)
                    measure.getSegment(0f, length, partial, true)
                    drawPath(partial, solid)
                    measure.getPosTan(length, sun, null)
                }
            }
            points.forEachIndexed { i, (x, y) ->
                when {
                    i < path.nextIndex -> drawCircle(x, y, 3.5f * d, fill(colors.past))
                    i == path.nextIndex -> {
                        drawCircle(x, y, 4.5f * d, fill(colors.nextFill))
                        drawCircle(x, y, 4.5f * d, stroke(colors.past, 2f * d))
                    }
                    else -> drawCircle(x, y, 3f * d, fill(colors.future))
                }
            }
            drawCircle(sun[0], sun[1], 9f * d, stroke((colors.sun and 0x00FFFFFF) or 0x73000000, 1f * d))
            drawCircle(sun[0], sun[1], 5f * d, fill(colors.sun))
        }
}
