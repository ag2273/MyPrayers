package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import java.time.Clock
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
 * time. At 4×1 the dates go, and «الصلاة القادمة» and «من …» where they don't fit. On the light surface, or the deep
 * Next Prayer surface with gold and the star when «كل الأدوات بتصميم الصلاة القادمة» is on. Re-rendered each minute by [ClockTickReceiver]; tapping opens Today.
 */
class ClockPrayerWidget(private val clock: Clock = Clock.systemDefaultZone()) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        clock = clock,
        build = { core, text -> ClockPrayerView(ClockView.of(clockContent(context, core.displayDay(), text, clock), text, context, clockWeather(context, core, clock)), text) },
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

    /** The weather on one line under the clock at 4×2 (design board option «أ»). */
    val WEATHER = WeatherSizes(temperatureSp = 13f, detailSp = 11f, sunDp = 12f, iconDp = 10f, iconGapDp = 3f, itemGapDp = 8f, lineGapDp = 0f)
    /** At 4×1 only the temperature, after the clock. */
    const val COMPACT_TEMPERATURE_SP = 15f
    const val COMPACT_TEMPERATURE_GAP_DP = 6f

    /** Below this height (dp) the launcher gives a 4×1: the dates go, and «الصلاة القادمة» and «من …» where they don't fit. */
    const val COMPACT_BELOW = MIN_HEIGHT
    /** At 4×1, less padding above and below, for the room. */
    val COMPACT_PADDING_V = 4.dp
    /** At 4×1, the prayer side's text takes at most this share of the width; the clock grows into the rest. */
    const val COMPACT_PRAYER_SHARE = 0.62f
    /** At 4×1, the smallest scale at which the optional lines are kept rather than dropped for room. */
    const val KEEP_LINES_SCALE = 0.75f

    /**
     * The scales of the prayer side ([scale]) and the clock side ([clockScale], the same at 4×2), the prayer side's
     * width in dp (the clock side takes the rest), the padding above and below, and which lines are shown: the dates
     * under the clock, «الصلاة القادمة» above the next Stop and «من …» under the bar.
     */
    data class Fit(
        val scale: Float,
        val prayerWidth: Float,
        val dividerHeight: Float,
        val clockScale: Float = scale,
        val paddingV: Dp = PADDING_V,
        val dates: Boolean = true,
        val label: Boolean = true,
        val since: Boolean = true,
    )

    fun fit(view: ClockPrayerView, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Fit {
        if (heightDp >= COMPACT_BELOW) return tryFit(view, widthDp, heightDp, fontScale, compact = false, label = true, since = true).fit
        val tries = listOf(true to true, true to false, false to false).map { (label, since) ->
            tryFit(view, widthDp, heightDp, fontScale, compact = true, label = label, since = since)
        }
        return (tries.firstOrNull { it.fits && it.fit.scale >= KEEP_LINES_SCALE } ?: tries.last()).fit
    }

    private class Tried(val fit: Fit, val fits: Boolean)

    /** At 4×2 one scale for the whole; at 4×1 ([compact], no dates) the prayer side's scale first, then the clock's. */
    private fun tryFit(view: ClockPrayerView, widthDp: Float, heightDp: Float, fontScale: Float, compact: Boolean, label: Boolean, since: Boolean): Tried {
        val clock = view.clock
        val dates = !compact
        val paddingV = if (compact) COMPACT_PADDING_V else PADDING_V
        val across = widthDp - 2 * PADDING_H.value
        val room = heightDp - 2 * paddingV.value - 1
        fun w(text: String, sp: Float, bold: Boolean = false) = textWidthDp(text, sp, fontScale, bold)
        fun h(text: String, sp: Float, bold: Boolean = false) = lineHeightDp(text, sp, fontScale, bold).toFloat()
        fun prayerText(s: Float) = maxOf(
            if (label) w(view.label, LABEL_SP * s) else 0f,
            w(clock.nextName, NAME_SP * s, bold = true) + WORD_GAP_DP * s + w(clock.nextTime, TIME_SP * s),
            w(view.before, BEFORE_SP * s) + WORD_GAP_DP * s + w(clock.countdown, COUNTDOWN_SP * s, bold = true),
            if (since) w(view.since, SINCE_SP * s) else 0f,
        )
        fun prayerWidth(s: Float) = maxOf(prayerText(s), (across - 2 * DIVIDER_GAP_DP * s - 1) * PRAYER_SHARE)
        val weather = clock.weather
        fun clockSide(s: Float) = maxOf(
            w(clock.clockSample, CLOCK_SP * s) + (if (clock.marker.isEmpty()) 0f else MARKER_GAP_DP * s + w(clock.marker, MARKER_SP * s)) +
                if (compact && weather != null) COMPACT_TEMPERATURE_GAP_DP * s + compactTemperatureWidth(weather, COMPACT_TEMPERATURE_SP * s, fontScale) else 0f,
            if (dates) w(clock.gregorianDate, DATE_SP * s) else 0f,
            if (!compact && weather != null) WEATHER.lineWidth(weather, s, fontScale) else 0f,
        )
        fun clockHeight(s: Float) = h(clock.clockSample, CLOCK_SP * s) +
            (if (!compact && weather != null) 2 * LINE_GAP_DP * s + WEATHER.lineHeight(weather, s, fontScale) else 0f) +
            if (dates) 2 * LINE_GAP_DP * s + h(clock.hijriDate, DATE_SP * s) + h(clock.gregorianDate, DATE_SP * s) else 0f
        fun prayerHeight(s: Float) = (if (label) h(view.label, LABEL_SP * s) + LINE_GAP_DP * s else 0f) + LINE_GAP_DP * s +
            maxOf(h(clock.nextName, NAME_SP * s, bold = true), h(clock.nextTime, TIME_SP * s)) +
            maxOf(h(view.before, BEFORE_SP * s), h(clock.countdown, COUNTDOWN_SP * s, bold = true)) +
            (if (since) 2 * BAR_GAP_DP * s + h(view.since, SINCE_SP * s) else BAR_GAP_DP * s) + WidgetArt.LINE_HEIGHT_DP
        fun fits(clockS: Float, s: Float) = clockSide(clockS) + 2 * DIVIDER_GAP_DP * s + 1 + prayerWidth(s) <= across &&
            clockHeight(clockS) <= room && prayerHeight(s) <= room
        val scale: Float
        val clockScale: Float
        if (compact) {
            scale = fitScale(floor = 0.2f, start = MAX_GROW) { s -> prayerHeight(s) <= room && prayerText(s) <= across * COMPACT_PRAYER_SHARE - 2 * DIVIDER_GAP_DP * s - 1 }
            clockScale = fitScale(floor = 0.2f, start = MAX_GROW) { fits(it, scale) }
        } else {
            scale = fitScale(floor = 0.4f, start = MAX_GROW) { fits(it, it) }
            clockScale = scale
        }
        val divider = maxOf(clockHeight(clockScale), prayerHeight(scale)).coerceAtMost(room)
        return Tried(Fit(scale, prayerWidth(scale), divider, clockScale, paddingV, dates, label, since), fits(clockScale, scale))
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
    WidgetFrame(rtl, horizontal = ClockPrayerMetrics.PADDING_H, vertical = fit.paddingV, description = clock.description) {
        ReadingRow(
            GlanceModifier.fillMaxSize(),
            Alignment.CenterVertically,
            listOf(
                cell {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ReadingRow(
                            verticalAlignment = Alignment.CenterVertically,
                            children = listOfNotNull(
                                cell { ClockFace(clock, ClockPrayerMetrics.CLOCK_SP * fit.clockScale) },
                                if (clock.marker.isEmpty()) null else cell {
                                    WidgetLine(clock.marker, ClockPrayerMetrics.MARKER_SP * fit.clockScale, Tone.MUTED, modifier = direction.paddingStart((ClockPrayerMetrics.MARKER_GAP_DP * fit.clockScale).dp))
                                },
                                clock.weather?.takeIf { !fit.dates }?.let { weather ->
                                    cell {
                                        CompactTemperature(
                                            weather,
                                            ClockPrayerMetrics.COMPACT_TEMPERATURE_SP * fit.clockScale,
                                            direction.paddingStart((ClockPrayerMetrics.COMPACT_TEMPERATURE_GAP_DP * fit.clockScale).dp),
                                        )
                                    }
                                },
                            ),
                        )
                        val weather = clock.weather
                        if (fit.dates && weather != null) {
                            WeatherLine(weather, ClockPrayerMetrics.WEATHER, s, GlanceModifier.padding(top = line * 2))
                        }
                        if (fit.dates) {
                            WidgetLine(clock.hijriDate, ClockPrayerMetrics.DATE_SP * s, Tone.MUTED, center = true, modifier = GlanceModifier.padding(top = line))
                            WidgetLine(clock.gregorianDate, ClockPrayerMetrics.DATE_SP * s, Tone.MUTED, center = true, modifier = GlanceModifier.padding(top = line))
                        }
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
                        if (fit.label) WidgetLine(view.label, ClockPrayerMetrics.LABEL_SP * s, Tone.MUTED)
                        ReadingRow(
                            GlanceModifier.padding(top = if (fit.label) line else 0.dp),
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
                        val bar = (ClockPrayerMetrics.BAR_GAP_DP * s).dp
                        ClockBar(clock, fit.prayerWidth, rtl, GlanceModifier.padding(top = bar, bottom = if (fit.since) bar else 0.dp))
                        if (fit.since) WidgetLine(view.since, ClockPrayerMetrics.SINCE_SP * s, Tone.MUTED)
                    }
                },
            ),
        )
    }
}

/** The Clock & Prayer entry in the widget picker (4×2, down to 4×1). */
class ClockPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockPrayerWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopClockTicksIfNoneLeft(context)
    }
}
