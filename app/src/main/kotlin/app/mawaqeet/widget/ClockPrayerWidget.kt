package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import app.mawaqeet.R

/**
 * The Clock & Prayer Widget Variant «ساعة وصلاة» (4×2), a split layout over [ClockContent]: on the app language's start
 * side the big thin clock with «ص/م», the Hijri date and the Gregorian date; a hairline; on the other side «الصلاة
 * القادمة», the next Stop's name (large) and time, «بعد» and the countdown, the bar, and «من» the previous Stop and its
 * time. On the light surface, or the deep Next Prayer surface with gold and the star when «كل الأدوات بتصميم الصلاة
 * القادمة» is on. Re-rendered each minute by [ClockTickReceiver]; tapping opens Today.
 */
class ClockPrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> ClockPrayerView(ClockView.of(clockContent(context, core.displayDay(), text), text, context), text) },
        layout = { view, rtl -> ClockPrayerLayout(view, rtl) },
    )
}

/** [ClockView] with the split layout's own words, in the app language. */
internal data class ClockPrayerView(
    val clock: ClockView,
    /** «الصلاة القادمة» / `Next Prayer` */
    val label: String,
    /** «بعد» / `in`, before the countdown. */
    val before: String,
    /** «من الشروق ٠٦:٢٧» / `from Sunrise 06:27` */
    val since: String,
) {
    constructor(clock: ClockView, text: WidgetText) : this(
        clock,
        label = text.strings.getString(R.string.next_prayer),
        before = text.strings.getString(R.string.clock_prayer_in),
        since = text.strings.getString(R.string.clock_prayer_since, clock.previous),
    )
}

/** Sizes of Clock & Prayer at the smallest 4×2; it shrinks or grows as a whole to the launcher's room. */
internal object ClockPrayerMetrics {
    const val MIN_WIDTH = 250f
    const val MIN_HEIGHT = 110f
    val PADDING_H = 14.dp
    val PADDING_V = 8.dp
    const val CLOCK_SP = 40f
    const val MARKER_SP = 13f
    const val DATE_SP = 11f
    const val LABEL_SP = 11f
    const val NAME_SP = 20f
    const val TIME_SP = 13f
    const val BEFORE_SP = 12f
    const val COUNTDOWN_SP = 13f
    const val SINCE_SP = 11f
    const val MARKER_GAP_DP = 3f
    /** Between a name and its time, and «بعد» and the countdown. */
    const val WORD_GAP_DP = 5f
    /** Either side of the hairline. */
    const val DIVIDER_GAP_DP = 12f
    const val LINE_GAP_DP = 2f
    /** Above and below the bar. */
    const val BAR_GAP_DP = 5f
    /** The prayer side takes at least this share of the width, so its bar reads as a bar. */
    const val PRAYER_SHARE = 0.44f
    const val MAX_GROW = 1.6f

    /** The scale and the prayer side's width in dp (the clock side takes the rest). */
    data class Fit(val scale: Float, val prayerWidth: Float, val dividerHeight: Float)

    fun fit(view: ClockPrayerView, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Fit {
        val clock = view.clock
        val across = widthDp - 2 * PADDING_H.value
        val room = heightDp - 2 * PADDING_V.value - 1
        fun w(text: String, sp: Float, bold: Boolean = false) = textWidthDp(text, sp, fontScale, bold)
        fun h(text: String, sp: Float, bold: Boolean = false) = lineHeightDp(text, sp, fontScale, bold).toFloat()
        fun prayerWidth(s: Float) = maxOf(
            w(view.label, LABEL_SP * s),
            w(clock.nextName, NAME_SP * s, bold = true) + WORD_GAP_DP * s + w(clock.nextTime, TIME_SP * s),
            w(view.before, BEFORE_SP * s) + WORD_GAP_DP * s + w(clock.countdown, COUNTDOWN_SP * s, bold = true),
            w(view.since, SINCE_SP * s),
            (across - 2 * DIVIDER_GAP_DP * s - 1) * PRAYER_SHARE,
        )
        fun clockSide(s: Float) = maxOf(
            w(clock.clockSample, CLOCK_SP * s) + if (clock.marker.isEmpty()) 0f else MARKER_GAP_DP * s + w(clock.marker, MARKER_SP * s),
            w(clock.gregorianDate, DATE_SP * s),
        )
        fun clockHeight(s: Float) = h(clock.clockSample, CLOCK_SP * s) + 2 * LINE_GAP_DP * s + h(clock.hijriDate, DATE_SP * s) + h(clock.gregorianDate, DATE_SP * s)
        fun prayerHeight(s: Float) = h(view.label, LABEL_SP * s) + 2 * LINE_GAP_DP * s +
            maxOf(h(clock.nextName, NAME_SP * s, bold = true), h(clock.nextTime, TIME_SP * s)) +
            maxOf(h(view.before, BEFORE_SP * s), h(clock.countdown, COUNTDOWN_SP * s, bold = true)) +
            2 * BAR_GAP_DP * s + WidgetArt.LINE_HEIGHT_DP + h(view.since, SINCE_SP * s)
        val scale = fitScale(floor = 0.4f, start = MAX_GROW) { s ->
            clockSide(s) + 2 * DIVIDER_GAP_DP * s + 1 + prayerWidth(s) <= across &&
                clockHeight(s) <= room && prayerHeight(s) <= room
        }
        return Fit(scale, prayerWidth(scale), maxOf(clockHeight(scale), prayerHeight(scale)).coerceAtMost(room))
    }
}

@Composable
internal fun ClockPrayerLayout(view: ClockPrayerView, rtl: Boolean) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val size = LocalSize.current
    val direction = LocalWidgetDirection.current
    val width = if (style.responsive) size.width.value else ClockPrayerMetrics.MIN_WIDTH
    val height = if (style.responsive) size.height.value else ClockPrayerMetrics.MIN_HEIGHT
    val fit = ClockPrayerMetrics.fit(view, width, height, context.resources.configuration.fontScale)
    val s = fit.scale
    val clock = view.clock
    val line = (ClockPrayerMetrics.LINE_GAP_DP * s).dp
    val word = (ClockPrayerMetrics.WORD_GAP_DP * s).dp
    WidgetFrame(rtl, horizontal = ClockPrayerMetrics.PADDING_H, vertical = ClockPrayerMetrics.PADDING_V, description = clock.description) {
        ReadingRow(
            GlanceModifier.fillMaxSize(),
            Alignment.CenterVertically,
            listOf(
                cell {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ReadingRow(
                            verticalAlignment = Alignment.CenterVertically,
                            children = listOfNotNull(
                                cell { ClockFace(clock, ClockPrayerMetrics.CLOCK_SP * s) },
                                if (clock.marker.isEmpty()) null else cell {
                                    WidgetLine(clock.marker, ClockPrayerMetrics.MARKER_SP * s, Tone.MUTED, modifier = direction.paddingStart((ClockPrayerMetrics.MARKER_GAP_DP * s).dp))
                                },
                            ),
                        )
                        WidgetLine(clock.hijriDate, ClockPrayerMetrics.DATE_SP * s, Tone.MUTED, center = true, modifier = GlanceModifier.padding(top = line))
                        WidgetLine(clock.gregorianDate, ClockPrayerMetrics.DATE_SP * s, Tone.MUTED, center = true, modifier = GlanceModifier.padding(top = line))
                    }
                },
                cell {
                    Box(GlanceModifier.padding(horizontal = (ClockPrayerMetrics.DIVIDER_GAP_DP * s).dp)) {
                        Image(
                            ImageProvider(R.drawable.widget_rect),
                            contentDescription = null,
                            modifier = GlanceModifier.width(1.dp).height(fit.dividerHeight.dp),
                            colorFilter = tint(style.divider),
                        )
                    }
                },
                cell {
                    Column(GlanceModifier.width(fit.prayerWidth.dp), horizontalAlignment = direction.start) {
                        WidgetLine(view.label, ClockPrayerMetrics.LABEL_SP * s, Tone.MUTED)
                        ReadingRow(
                            GlanceModifier.padding(top = line),
                            Alignment.CenterVertically,
                            listOf(
                                cell { WidgetLine(clock.nextName, ClockPrayerMetrics.NAME_SP * s, Tone.TEXT, bold = true) },
                                cell { WidgetLine(clock.nextTime, ClockPrayerMetrics.TIME_SP * s, Tone.TEXT, modifier = direction.paddingStart(word)) },
                            ),
                        )
                        ReadingRow(
                            GlanceModifier.padding(top = line),
                            Alignment.CenterVertically,
                            listOf(
                                cell { WidgetLine(view.before, ClockPrayerMetrics.BEFORE_SP * s, Tone.MUTED) },
                                cell { WidgetLine(clock.countdown, ClockPrayerMetrics.COUNTDOWN_SP * s, Tone.ACCENT, bold = true, modifier = direction.paddingStart(word)) },
                            ),
                        )
                        Box(GlanceModifier.padding(vertical = (ClockPrayerMetrics.BAR_GAP_DP * s).dp)) {
                            WidgetDrawing(
                                WidgetArt.progressLine(context, fit.prayerWidth, clock.elapsed, rtl, context.colorOf(style.accent), context.colorOf(style.accentSoft)),
                                fit.prayerWidth,
                                WidgetArt.LINE_HEIGHT_DP,
                            )
                        }
                        WidgetLine(view.since, ClockPrayerMetrics.SINCE_SP * s, Tone.MUTED)
                    }
                },
            ),
        )
    }
}

/** The Clock & Prayer entry in the widget picker (4×2). */
class ClockPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockPrayerWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopClockTicksIfNoneLeft(context)
    }
}
