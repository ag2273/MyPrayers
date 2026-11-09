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
import androidx.glance.layout.Row
import app.mawaqeet.R
import java.time.Clock

/**
 * The Next Prayer Widget Variant (2×2, and 2×1 via [NextPrayerCompactWidgetReceiver]): the next Prayer's name, its
 * time and a live `بعد H:MM:SS` countdown. A thin layer over [NextPrayerContent]; it re-renders on
 * [Widgets.refreshAll]. Tapping opens the app on today's Prayer Times. On Android 12+ it has three size steps.
 */
class NextPrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, SQUARE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> NextPrayerView.of(NextPrayerContent.of(core.displayDay(), Clock.systemUTC().instant(), text.formatter), text) },
        layout = { view, rtl ->
            val size = LocalSize.current
            val style = LocalWidgetStyle.current
            NextPrayerLayout(
                view,
                rtl,
                compact = size.height < SQUARE.height,
                // Android 8–11 keeps one layout at the default size.
                large = style.responsive && size.width >= LARGE.width && size.height >= LARGE.height,
            )
        },
    )

    private companion object {
        val COMPACT = DpSize(110.dp, 40.dp)
        val SQUARE = DpSize(110.dp, 100.dp)
        val LARGE = DpSize(180.dp, 180.dp)
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
private fun NextPrayerLayout(view: NextPrayerView, rtl: Boolean, compact: Boolean, large: Boolean) {
    val step = if (large) 1.3f else 1f
    val height = LocalSize.current.height.value
    val vertical = if (compact) NextPrayerMetrics.compactPaddingDp(height) else 12f
    WidgetFrame(rtl, horizontal = 14.dp, vertical = vertical.dp) {
        if (compact) {
            // Two lines in as little as 40dp: both shrink together until they fit, measured as the launcher draws them.
            val fontScale = LocalContext.current.resources.configuration.fontScale
            val scale = NextPrayerMetrics.compactScale(view.name, view.countdownFormat.format(view.time), height, fontScale)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetLine(view.name, NextPrayerMetrics.COMPACT_NAME_SP * scale, Tone.ACCENT, bold = true, modifier = GlanceModifier.defaultWeight())
                WidgetLine(view.time, NextPrayerMetrics.COMPACT_TIME_SP * scale, Tone.MUTED, modifier = GlanceModifier.padding(start = 8.dp))
            }
            Countdown(view, NextPrayerMetrics.COMPACT_COUNTDOWN_SP * scale)
        } else {
            WidgetLine(view.label, 12f * step, Tone.MUTED)
            WidgetLine(view.name, 26f * step, Tone.ACCENT, bold = true)
            WidgetLine(view.time, 15f * step, Tone.TEXT)
            Countdown(view, 15f * step, GlanceModifier.padding(top = 4.dp))
        }
    }
}

/** Sizes of the compact (2×1) layout. */
internal object NextPrayerMetrics {
    const val COMPACT_NAME_SP = 18f
    const val COMPACT_TIME_SP = 13f
    const val COMPACT_COUNTDOWN_SP = 14f

    fun compactPaddingDp(heightDp: Float) = if (heightDp < 56f) 4f else 8f

    /** The largest scale from 1 down, in 5% steps, at which the name line and the countdown fit [heightDp]. */
    fun compactScale(name: String, countdown: String, heightDp: Float, fontScale: Float = 1f): Float {
        // 1dp spare: the Chronometer's ticking text can measure a pixel taller than the sample.
        val room = heightDp - 2 * compactPaddingDp(heightDp) - 1
        var scale = 1f
        while (scale > 0.6f) {
            val first = lineHeightDp(name, COMPACT_NAME_SP * scale, fontScale, bold = true)
            if (first + lineHeightDp(countdown, COMPACT_COUNTDOWN_SP * scale, fontScale, bold = true) <= room) break
            scale -= 0.05f
        }
        return scale
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorStateList(R.id.countdown, "setTextColor", style.text)
        } else {
            setTextColor(R.id.countdown, context.getColor(style.text))
        }
    }
    AndroidRemoteViews(chronometer, modifier)
}

/** The 2×2 entry in the widget picker. */
class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}

/** The compact 2×1 entry in the widget picker; the same widget, laid out for its height. */
class NextPrayerCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}
