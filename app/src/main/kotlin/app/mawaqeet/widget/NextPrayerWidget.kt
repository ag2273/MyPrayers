package app.mawaqeet.widget

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Alignment
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import androidx.glance.layout.fillMaxWidth
import app.mawaqeet.R
import java.time.Clock

/**
 * The Next Prayer Widget Variant (4×2, and 1×1 via [NextPrayerCompactWidgetReceiver]): the next Prayer's name and
 * time, and a live `بعد H:MM:SS` countdown. A thin layer over [NextPrayerContent]; it re-renders on
 * [Widgets.refreshAll]. Tapping opens the app on today's Prayer Times. On Android 12+ it has four layouts: name, time
 * and countdown stacked in one cell, two lines when one row high, name over time when narrow, and name and time side
 * by side from 4 cells wide.
 */
class NextPrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(TINY, COMPACT, SQUARE, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> NextPrayerView.of(NextPrayerContent.of(core.displayDay(), Clock.systemUTC().instant(), text.formatter), text) },
        layout = { view, rtl ->
            val size = LocalSize.current
            val style = LocalWidgetStyle.current
            NextPrayerLayout(
                view,
                rtl,
                tiny = style.responsive && size.width < COMPACT.width,
                compact = size.height < SQUARE.height,
                // Android 8–11 keeps one layout at the default size, which is 4×2.
                wide = !style.responsive || size.width >= WIDE.width,
            )
        },
    )

    private companion object {
        /** One home-screen cell. */
        val TINY = DpSize(40.dp, 40.dp)
        val COMPACT = DpSize(110.dp, 40.dp)
        val SQUARE = DpSize(110.dp, 100.dp)
        val WIDE = DpSize(220.dp, 100.dp)
    }
}

/** Everything the layout draws, already in the user's language and digits. */
private data class NextPrayerView(
    val label: String,
    val name: String,
    val time: String,
    /** `Chronometer` format, e.g. "بعد %s". */
    val countdownFormat: String,
    /** `SystemClock.elapsedRealtime()` at which the countdown reaches zero. */
    val countdownBase: Long,
) {
    /** The longest the countdown reads, for measuring. */
    val countdownSample: String get() = countdownFormat.format("00:00:00")

    companion object {
        fun of(content: NextPrayerContent, text: WidgetText) = NextPrayerView(
            label = text.strings.getString(R.string.widget_next_prayer),
            name = text.strings.getString(content.nameRes),
            time = content.time,
            countdownFormat = text.strings.getString(R.string.widget_countdown_format),
            countdownBase = SystemClock.elapsedRealtime() + content.untilNext.toMillis(),
        )
    }
}

@Composable
private fun NextPrayerLayout(view: NextPrayerView, rtl: Boolean, tiny: Boolean, compact: Boolean, wide: Boolean) {
    val size = LocalSize.current
    val fontScale = LocalContext.current.resources.configuration.fontScale
    when {
        tiny -> {
            // One cell: the name, its time and the countdown, no label, as large as the cell allows.
            val scale = NextPrayerMetrics.tinyScale(view.name, view.time, view.countdownSample, size.width.value, size.height.value, fontScale)
            WidgetFrame(rtl, horizontal = NextPrayerMetrics.TINY_PADDING, vertical = NextPrayerMetrics.TINY_PADDING) {
                WidgetLine(view.name, NextPrayerMetrics.TINY_NAME_SP * scale, Tone.TEXT, bold = true)
                WidgetLine(view.time, NextPrayerMetrics.TINY_TIME_SP * scale, Tone.TEXT, bold = true)
                Countdown(view, NextPrayerMetrics.TINY_COUNTDOWN_SP * scale)
            }
        }
        compact -> {
            val vertical = NextPrayerMetrics.compactPaddingDp(size.height.value)
            WidgetFrame(rtl, horizontal = NextPrayerMetrics.COMPACT_PADDING, vertical = vertical.dp) {
                // Two lines in as little as 40dp: both shrink together until they fit, measured as the launcher draws them.
                val line = NextPrayerMetrics.compactLine(view.name, view.time)
                val scale = NextPrayerMetrics.compactScale(line, view.countdownSample, size.width.value, size.height.value, fontScale)
                WidgetLine(line, NextPrayerMetrics.COMPACT_LINE_SP * scale, Tone.TEXT, bold = true)
                Countdown(view, NextPrayerMetrics.COMPACT_COUNTDOWN_SP * scale)
            }
        }
        wide -> {
            // On Android 8–11 the launcher's size isn't known: lay out for the smallest 4×2.
            val width = if (LocalWidgetStyle.current.responsive) size.width.value else NextPrayerMetrics.WIDE_MIN_WIDTH
            val height = if (LocalWidgetStyle.current.responsive) size.height.value else NextPrayerMetrics.WIDE_MIN_HEIGHT
            val scale = NextPrayerMetrics.wideScale(view.label, view.name, view.time, view.countdownSample, width, height, fontScale)
            WidgetFrame(rtl, horizontal = NextPrayerMetrics.PADDING_H, vertical = NextPrayerMetrics.PADDING_V) {
                WidgetLine(view.label, NextPrayerMetrics.LABEL_SP * scale, Tone.MUTED, bold = true)
                // The name at the app language's start, the time at its end.
                ReadingRow(
                    GlanceModifier.fillMaxWidth().padding(top = (NextPrayerMetrics.ROW_GAP_DP * scale).dp),
                    Alignment.Bottom,
                    listOf(
                        cell { WidgetLine(view.name, NextPrayerMetrics.NAME_SP * scale, Tone.TEXT, bold = true, modifier = GlanceModifier.defaultWeight()) },
                        cell { WidgetLine(view.time, NextPrayerMetrics.TIME_SP * scale, Tone.TEXT, bold = true) },
                    ),
                )
                Countdown(view, NextPrayerMetrics.COUNTDOWN_SP * scale)
            }
        }
        else -> {
            val scale = NextPrayerMetrics.squareScale(view.label, view.name, view.time, view.countdownSample, size.width.value, size.height.value, fontScale)
            WidgetFrame(rtl, horizontal = NextPrayerMetrics.COMPACT_PADDING, vertical = NextPrayerMetrics.PADDING_V) {
                WidgetLine(view.label, NextPrayerMetrics.LABEL_SP * scale, Tone.MUTED, bold = true)
                WidgetLine(view.name, NextPrayerMetrics.SQUARE_NAME_SP * scale, Tone.TEXT, bold = true)
                WidgetLine(view.time, NextPrayerMetrics.SQUARE_TIME_SP * scale, Tone.TEXT, bold = true)
                Countdown(view, NextPrayerMetrics.SQUARE_COUNTDOWN_SP * scale)
            }
        }
    }
}

/**
 * Sizes of the three layouts, from the Widgets board: the 4×2 shows the name (34sp) and time (30sp) side by side
 * under a 12sp label, with an 18sp countdown; everything shrinks together when the launcher gives less room.
 */
internal object NextPrayerMetrics {
    val PADDING_H = 18.dp
    val PADDING_V = 14.dp
    val COMPACT_PADDING = 14.dp
    const val WIDE_MIN_WIDTH = 250f
    const val WIDE_MIN_HEIGHT = 110f
    const val LABEL_SP = 12f
    const val NAME_SP = 34f
    const val TIME_SP = 30f
    const val COUNTDOWN_SP = 18f
    const val ROW_GAP_DP = 6f
    const val NAME_TIME_GAP_DP = 12f
    const val SQUARE_NAME_SP = 26f
    const val SQUARE_TIME_SP = 18f
    const val SQUARE_COUNTDOWN_SP = 15f
    val TINY_PADDING = 8.dp
    const val TINY_NAME_SP = 20f
    const val TINY_TIME_SP = 16f
    const val TINY_COUNTDOWN_SP = 13f
    const val COMPACT_LINE_SP = 18f
    const val COMPACT_COUNTDOWN_SP = 14f

    /** The 2×1's first line: "العصر ٢:٤٤". */
    fun compactLine(name: String, time: String) = "$name $time"

    fun compactPaddingDp(heightDp: Float) = if (heightDp < 56f) 4f else 8f

    /** The 2×1's scale: its line and the countdown fit [widthDp] across and [heightDp] down. */
    fun compactScale(line: String, countdown: String, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Float {
        // 1dp spare: the Chronometer's ticking text can measure a pixel taller than the sample.
        val room = heightDp - 2 * compactPaddingDp(heightDp) - 1
        val across = widthDp - 2 * COMPACT_PADDING.value
        return fitScale {
            lineHeightDp(line, COMPACT_LINE_SP * it, fontScale, bold = true) +
                lineHeightDp(countdown, COMPACT_COUNTDOWN_SP * it, fontScale, bold = true) <= room &&
                textWidthDp(line, COMPACT_LINE_SP * it, fontScale, bold = true) <= across &&
                textWidthDp(countdown, COMPACT_COUNTDOWN_SP * it, fontScale, bold = true) <= across
        }
    }

    /** The 1×1's scale: name, time and countdown, each within the width, all within the height. */
    fun tinyScale(name: String, time: String, countdown: String, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Float {
        val across = widthDp - 2 * TINY_PADDING.value
        val room = heightDp - 2 * TINY_PADDING.value - 1
        val lines = listOf(name to TINY_NAME_SP, time to TINY_TIME_SP, countdown to TINY_COUNTDOWN_SP)
        return fitScale(floor = 0.4f) { s ->
            lines.all { (text, sp) -> textWidthDp(text, sp * s, fontScale, bold = true) <= across } &&
                lines.sumOf { (text, sp) -> lineHeightDp(text, sp * s, fontScale, bold = true) } <= room
        }
    }

    /** The 4×2's scale: name and time side by side fit the width, and the three lines fit the height. */
    fun wideScale(label: String, name: String, time: String, countdown: String, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Float {
        val across = widthDp - 2 * PADDING_H.value
        val room = heightDp - 2 * PADDING_V.value - 1
        return fitScale(floor = 0.4f) {
            textWidthDp(name, NAME_SP * it, fontScale, bold = true) + NAME_TIME_GAP_DP + textWidthDp(time, TIME_SP * it, fontScale, bold = true) <= across &&
                lineHeightDp(label, LABEL_SP * it, fontScale, bold = true) + ROW_GAP_DP * it +
                maxOf(lineHeightDp(name, NAME_SP * it, fontScale, bold = true), lineHeightDp(time, TIME_SP * it, fontScale, bold = true)) +
                lineHeightDp(countdown, COUNTDOWN_SP * it, fontScale, bold = true) <= room
        }
    }

    /** The narrow layout's scale: four lines, each within the width, all within the height. */
    fun squareScale(label: String, name: String, time: String, countdown: String, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Float {
        val across = widthDp - 2 * COMPACT_PADDING.value
        val room = heightDp - 2 * PADDING_V.value - 1
        val lines = listOf(name to SQUARE_NAME_SP, time to SQUARE_TIME_SP, countdown to SQUARE_COUNTDOWN_SP)
        return fitScale(floor = 0.4f) { s ->
            lines.all { (text, sp) -> textWidthDp(text, sp * s, fontScale, bold = true) <= across } &&
                lineHeightDp(label, LABEL_SP * s, fontScale, bold = true) + lines.sumOf { (text, sp) -> lineHeightDp(text, sp * s, fontScale, bold = true) } <= room
        }
    }
}

/** A platform count-down `Chronometer`, ticked by the launcher; it always shows seconds. */
@Composable
private fun Countdown(view: NextPrayerView, sp: Float, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val chronometer = RemoteViews(
        context.packageName,
        if (style.shadowedText) R.layout.widget_countdown_shadow else R.layout.widget_countdown,
    ).apply {
        setChronometer(R.id.countdown, view.countdownBase, view.countdownFormat, true)
        setChronometerCountDown(R.id.countdown, true)
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, sp)
        // The countdown is the accent line (Widgets board).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorStateList(R.id.countdown, "setTextColor", style.accent)
        } else {
            setTextColor(R.id.countdown, context.getColor(style.accent))
        }
        LocalWidgetDirection.current.applyTo(this, R.id.countdown)
    }
    // Wrapped, so the frame's Column puts it at the app language's start (an AndroidRemoteViews otherwise fills it).
    AndroidRemoteViews(chronometer, GlanceModifier.wrapContentWidth().wrapContentHeight().then(modifier))
}

/** The 4×2 entry in the widget picker. */
class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}

/** The compact 1×1 entry in the widget picker; the same widget, laid out for its size. */
class NextPrayerCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}
