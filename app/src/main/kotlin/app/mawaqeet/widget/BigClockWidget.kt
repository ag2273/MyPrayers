package app.mawaqeet.widget

import android.app.LocaleManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import app.mawaqeet.R
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.Weather
import app.mawaqeet.weather.WeatherStore
import java.text.DecimalFormatSymbols
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The Big Clock Widget Variant «ساعة كبيرة» (4×2, the reference layout): a big thin clock with the app's «ص/م»
 * marker; the previous Stop and its time, a bold countdown and the next Stop and its time; a bar from the previous Stop
 * to the next; and the date line. At 4×1 the clock sits beside the countdown and bar, the Stops where they fit, and the
 * date line goes. On the light surface, or the deep Next Prayer surface with gold and the star when
 * «كل الأدوات بتصميم الصلاة القادمة» is on. A thin layer over [ClockContent]; re-rendered each minute by
 * [ClockTickReceiver]. Tapping opens Today.
 */
class BigClockWidget(private val clock: Clock = Clock.systemDefaultZone()) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        clock = clock,
        build = { core, text -> ClockView.of(clockContent(context, core.displayDay(), text, clock), text, context, clockWeather(context, core, clock)) },
        layout = { view, rtl -> BigClockLayout(view, rtl) },
    )
}

/**
 * The Clock content for [clock]'s now (the system clock outside tests), in the phone's time zone and 12/24-hour
 * setting, arming the next minute's re-render. Every Clock Widget's `build` goes through here.
 */
internal fun clockContent(
    context: Context,
    day: app.mawaqeet.core.DisplayDay,
    text: WidgetText,
    clock: Clock = Clock.systemDefaultZone(),
): ClockContent {
    val now = ZonedDateTime.now(clock.withZone(ZoneId.systemDefault()))
    return ClockContent.of(day, now, text.formatter, DateFormat.is24HourFormat(context))
        .also { ClockTickReceiver.schedule(context, it.nextChange(now.toInstant())) }
}

/** The weather to show beside the clock (fetched within [Weather.SHOW_FOR] for this location), or null. */
internal fun clockWeather(context: Context, core: PrayerCore, clock: Clock = Clock.systemDefaultZone()): Weather? =
    WeatherStore(context).shownFor(core.location, clock.instant())

/** The weather beside the clock, in the user's digits: «٢٩°», «٣٨٪», «٥٪». */
internal data class ClockWeather(val temperature: String, val humidity: String, val rain: String) {
    companion object {
        fun of(weather: Weather, text: WidgetText): ClockWeather {
            val f = text.formatter
            val percent = if (text.rtl) "٪" else "%"
            return ClockWeather("${f.number(weather.temperature)}°", f.number(weather.humidityPercent) + percent, f.number(weather.rainChancePercent) + percent)
        }
    }
}

/** What the Clock layouts draw, already in the user's language and digits. */
internal data class ClockView(
    /** The time now as the clock face shows it, for a clock the launcher can't tick in the user's digits. */
    val clockTime: String,
    /** The widest the clock face reads, for measuring. */
    val clockSample: String,
    /** Whether the launcher ticks the clock: its digits are the user's. Otherwise each minute's render sets it. */
    val ticking: Boolean,
    /** «ص» or «م» in 12-hour time; empty in 24-hour time. */
    val marker: String,
    val previousName: String,
    val previousTime: String,
    val nextName: String,
    val nextTime: String,
    val countdown: String,
    val elapsed: Float,
    val dateLine: String,
    /** The date line's two halves, for layouts that put them on lines of their own (Clock & Prayer). */
    val hijriDate: String,
    val gregorianDate: String,
    /** TalkBack's one sentence: the time, the next Stop and the time left, the dates, and the weather. */
    val description: String,
    /** Beside the clock (temperature only at 4×1); null without a recent fetch. */
    val weather: ClockWeather? = null,
) {
    /** «الشروق ٠٦:٢٧» */
    val previous: String get() = "$previousName $previousTime"
    val next: String get() = "$nextName $nextTime"

    companion object {
        fun of(content: ClockContent, text: WidgetText, context: Context, weather: Weather? = null): ClockView {
            val s = text.strings
            val marker = when (content.meridiem) {
                Meridiem.AM -> s.getString(R.string.clock_am)
                Meridiem.PM -> s.getString(R.string.clock_pm)
                null -> ""
            }
            val nextName = s.getString(content.next.nameRes)
            val time = if (marker.isEmpty()) content.clockTime else "${content.clockTime} $marker"
            val dates = content.hijriDate + (if (text.rtl) "، " else ", ") + content.gregorianDate
            val shownWeather = weather?.let { ClockWeather.of(it, text) }
            val described = s.getString(R.string.clock_description, time, nextName, spoken(content, text), dates) +
                (shownWeather?.let { " " + s.getString(R.string.clock_weather_description, it.temperature, it.humidity, it.rain) } ?: "")
            return ClockView(
                clockTime = content.clockTime,
                clockSample = text.formatter.clockTime(ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneId.of("UTC")), twentyFourHour = true),
                ticking = launcherDigitsMatch(context, text.formatter.number(0)),
                marker = marker,
                previousName = s.getString(content.previous.nameRes),
                previousTime = content.previous.time,
                nextName = nextName,
                nextTime = content.next.time,
                countdown = content.countdown,
                elapsed = content.elapsed,
                dateLine = content.dateLine,
                hijriDate = content.hijriDate,
                gregorianDate = content.gregorianDate,
                description = described,
                weather = shownWeather,
            )
        }

        /** The time left in words, for TalkBack: «ساعة و١٠ دقائق» / `1 hour 10 minutes`. */
        private fun spoken(content: ClockContent, text: WidgetText): String {
            val r = text.strings.resources
            val hours = content.untilNext.toHours().toInt()
            val minutes = (content.untilNext.toMinutes() % 60).toInt()
            val h = r.getQuantityString(R.plurals.clock_spoken_hours, hours, text.formatter.number(hours))
            val m = r.getQuantityString(R.plurals.clock_spoken_minutes, minutes, text.formatter.number(minutes))
            return when {
                hours == 0 -> m
                minutes == 0 -> h
                else -> text.strings.getString(R.string.clock_spoken_hours_minutes, h, m)
            }
        }

        /**
         * Whether a `TextClock` in the launcher writes the user's digits: it formats in the launcher's default locale,
         * the phone's language, whatever the app's language and digit setting.
         */
        private fun launcherDigitsMatch(context: Context, userZero: String): Boolean {
            val phone: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)?.systemLocales?.takeUnless { it.isEmpty }?.get(0)
            } else {
                null
            } ?: Resources.getSystem().configuration.locales[0]
            return DecimalFormatSymbols.getInstance(phone).zeroDigit == userZero.first()
        }
    }
}

/** Sizes of the Big Clock, from the reference layout; it shrinks as a whole when the launcher gives less room. */
internal object BigClockMetrics {
    /** The smallest 4×2, laid out for on Android 8–11 where the launcher's size isn't known. */
    const val MIN_WIDTH = 250f
    const val MIN_HEIGHT = 110f
    val PADDING_H = 16.dp
    val PADDING_V = 8.dp
    const val CLOCK_SP = 44f
    const val MARKER_SP = 14f
    const val STOP_SP = 12f
    const val COUNTDOWN_SP = 15f
    const val DATE_SP = 11f
    const val MARKER_GAP_DP = 4f
    /** Between the stops and the countdown in their row. */
    const val ROW_GAP_DP = 8f
    /** Between the clock, the stops row, the bar and the date line. */
    const val LINE_GAP_DP = 4f
    /** How far it grows past the reference sizes on a roomy 4×2. */
    const val MAX_GROW = 1.6f

    /** The weather beside the clock (design board option «أ»), after a hairline. */
    val WEATHER = WeatherSizes(temperatureSp = 17f, detailSp = 9f, sunDp = 13f, iconDp = 8f, iconGapDp = 3f, itemGapDp = 6f, lineGapDp = 3f)
    /** Either side of the hairline between the clock and the weather. */
    const val WEATHER_GAP_DP = 10f
    /** At 4×1 only the temperature, after the clock. */
    const val COMPACT_TEMPERATURE_SP = 18f
    const val COMPACT_TEMPERATURE_GAP_DP = 8f

    /** The clock's line at 4×2: the clock and marker, then the hairline and the weather when there is one. */
    fun clockRowWidth(view: ClockView, s: Float, fontScale: Float): Float {
        val clock = textWidthDp(view.clockSample, CLOCK_SP * s, fontScale) +
            if (view.marker.isEmpty()) 0f else MARKER_GAP_DP * s + textWidthDp(view.marker, MARKER_SP * s, fontScale)
        val weather = view.weather ?: return clock
        return clock + 2 * WEATHER_GAP_DP * s + 1 + WEATHER.width(weather, s, fontScale)
    }

    fun clockRowHeight(view: ClockView, s: Float, fontScale: Float): Float = maxOf(
        lineHeightDp(view.clockSample, CLOCK_SP * s, fontScale).toFloat(),
        view.weather?.let { WEATHER.height(it, s, fontScale) } ?: 0f,
    )

    fun scale(view: ClockView, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Float {
        val across = widthDp - 2 * PADDING_H.value
        val room = heightDp - 2 * PADDING_V.value - 1
        return fitScale(floor = 0.4f, start = MAX_GROW) { s ->
            val clockWidth = clockRowWidth(view, s, fontScale)
            val rowWidth = textWidthDp(view.previous, STOP_SP * s, fontScale) + textWidthDp(view.countdown, COUNTDOWN_SP * s, fontScale, bold = true) +
                textWidthDp(view.next, STOP_SP * s, fontScale, bold = true) + 2 * ROW_GAP_DP * s
            val height = clockRowHeight(view, s, fontScale) + LINE_GAP_DP * s +
                maxOf(lineHeightDp(view.previous, STOP_SP * s, fontScale), lineHeightDp(view.countdown, COUNTDOWN_SP * s, fontScale, bold = true)) +
                2 * LINE_GAP_DP * s + WidgetArt.LINE_HEIGHT_DP + lineHeightDp(view.dateLine, DATE_SP * s, fontScale)
            clockWidth <= across && rowWidth <= across && textWidthDp(view.dateLine, DATE_SP * s, fontScale) <= across && height <= room
        }
    }

    /** Below this height (dp) the launcher gives a 4×1, laid out by [compact]. */
    const val COMPACT_BELOW = MIN_HEIGHT
    /** At 4×1, less padding above and below, for the room. */
    val COMPACT_PADDING_V = 4.dp
    /** Between the clock and the countdown's side at 4×1. */
    const val SIDE_GAP_DP = 12f
    /** At 4×1, the countdown's side takes at most this share of the width; the clock grows into the rest. */
    const val COMPACT_SIDE_SHARE = 0.62f
    /** At 4×1, the smallest scale at which the Stops are kept beside the countdown rather than dropped for room. */
    const val KEEP_STOPS_SCALE = 0.75f

    /**
     * The 4×1 layout: the scale of the countdown's side and of the clock, whether the Stops fit beside the countdown,
     * and the width of the countdown's side (the bar's length).
     */
    data class Compact(val scale: Float, val clockScale: Float, val stops: Boolean, val sideWidth: Float)

    /**
     * Big Clock at 4×1: the clock on the start side; on the other, the countdown (between the Stops where they fit)
     * over the bar. No date line. The countdown's side is sized first, then the clock takes what is left.
     */
    fun compact(view: ClockView, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Compact {
        val across = widthDp - 2 * PADDING_H.value
        val room = heightDp - 2 * COMPACT_PADDING_V.value - 1
        fun clockWidth(s: Float) = textWidthDp(view.clockSample, CLOCK_SP * s, fontScale) +
            (if (view.marker.isEmpty()) 0f else MARKER_GAP_DP * s + textWidthDp(view.marker, MARKER_SP * s, fontScale)) +
            (view.weather?.let { COMPACT_TEMPERATURE_GAP_DP * s + compactTemperatureWidth(it, COMPACT_TEMPERATURE_SP * s, fontScale) } ?: 0f)
        fun countdownWidth(s: Float) = textWidthDp(view.countdown, COUNTDOWN_SP * s, fontScale, bold = true)
        fun sideWidth(s: Float, stops: Boolean) = if (!stops) countdownWidth(s) else
            textWidthDp(view.previous, STOP_SP * s, fontScale) + countdownWidth(s) + textWidthDp(view.next, STOP_SP * s, fontScale, bold = true) + 2 * ROW_GAP_DP * s
        fun sideHeight(s: Float, stops: Boolean) = WidgetArt.LINE_HEIGHT_DP + 2 * LINE_GAP_DP * s + if (!stops) lineHeightDp(view.countdown, COUNTDOWN_SP * s, fontScale, bold = true) else
            maxOf(lineHeightDp(view.previous, STOP_SP * s, fontScale), lineHeightDp(view.next, STOP_SP * s, fontScale, bold = true), lineHeightDp(view.countdown, COUNTDOWN_SP * s, fontScale, bold = true))
        // The countdown alone never grows past its design size, so it stays smaller than the clock.
        fun sideScale(stops: Boolean) = fitScale(floor = 0.2f, start = if (stops) MAX_GROW else 1f) { s -> sideHeight(s, stops) <= room && sideWidth(s, stops) <= across * COMPACT_SIDE_SHARE }
        val withStops = sideScale(stops = true)
        val stops = withStops >= KEEP_STOPS_SCALE
        val scale = if (stops) withStops else sideScale(stops = false)
        val side = sideWidth(scale, stops)
        val clockScale = fitScale(floor = 0.2f, start = MAX_GROW) { c ->
            lineHeightDp(view.clockSample, CLOCK_SP * c, fontScale) <= room && clockWidth(c) + SIDE_GAP_DP * scale + side <= across
        }
        // A little short of the room, so the bar never outgrows its side if the clock draws a hair wider than measured.
        return Compact(scale, clockScale, stops, across - clockWidth(clockScale) - SIDE_GAP_DP * scale - 2)
    }
}

/** Which style colour each of Big Clock's lines takes; Gold Clock gives its Stops and date line their own. */
internal data class ClockTones(val previous: Tone = Tone.MUTED, val next: Tone = Tone.TEXT, val date: Tone = Tone.MUTED)

/** Big Clock's layout, which Gold Clock shares in its own [ClockTones]. */
@Composable
internal fun BigClockLayout(view: ClockView, rtl: Boolean, tones: ClockTones = ClockTones()) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val size = LocalSize.current
    val width = if (style.responsive) size.width.value else BigClockMetrics.MIN_WIDTH
    val height = if (style.responsive) size.height.value else BigClockMetrics.MIN_HEIGHT
    if (height < BigClockMetrics.COMPACT_BELOW) return BigClockCompactLayout(view, rtl, tones, width, height)
    val scale = BigClockMetrics.scale(view, width, height, context.resources.configuration.fontScale)
    val across = width - 2 * BigClockMetrics.PADDING_H.value
    val gap = (BigClockMetrics.LINE_GAP_DP * scale).dp
    val direction = LocalWidgetDirection.current
    WidgetFrame(rtl, horizontal = BigClockMetrics.PADDING_H, vertical = BigClockMetrics.PADDING_V, description = view.description) {
        Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val weather = view.weather
            ReadingRow(
                verticalAlignment = Alignment.CenterVertically,
                children = listOfNotNull(
                    cell { ClockFace(view, BigClockMetrics.CLOCK_SP * scale) },
                    if (view.marker.isEmpty()) null else cell {
                        WidgetLine(view.marker, BigClockMetrics.MARKER_SP * scale, Tone.MUTED, modifier = direction.paddingStart((BigClockMetrics.MARKER_GAP_DP * scale).dp))
                    },
                    if (weather == null) null else cell {
                        val height = BigClockMetrics.WEATHER.height(weather, scale, context.resources.configuration.fontScale)
                        Hairline(height, GlanceModifier.padding(horizontal = (BigClockMetrics.WEATHER_GAP_DP * scale).dp))
                    },
                    if (weather == null) null else cell { WeatherBlock(weather, BigClockMetrics.WEATHER, scale) },
                ),
            )
        }
        StopsRow(view, scale, tones, GlanceModifier.fillMaxWidth().padding(top = gap)) {
            WidgetLine(view.countdown, BigClockMetrics.COUNTDOWN_SP * scale, Tone.COUNTDOWN, bold = true)
        }
        ClockBar(view, across, rtl, GlanceModifier.padding(top = gap * 2))
        Box(GlanceModifier.fillMaxWidth().padding(top = gap), contentAlignment = Alignment.Center) {
            WidgetLine(view.dateLine, BigClockMetrics.DATE_SP * scale, tones.date)
        }
    }
}

/** Big Clock (and Gold Clock) at 4×1: the clock on the start side, the countdown and the bar on the other; no dates. */
@Composable
private fun BigClockCompactLayout(view: ClockView, rtl: Boolean, tones: ClockTones, width: Float, height: Float) {
    val context = LocalContext.current
    val direction = LocalWidgetDirection.current
    val fit = BigClockMetrics.compact(view, width, height, context.resources.configuration.fontScale)
    val scale = fit.scale
    val gap = (BigClockMetrics.LINE_GAP_DP * scale).dp
    WidgetFrame(rtl, horizontal = BigClockMetrics.PADDING_H, vertical = BigClockMetrics.COMPACT_PADDING_V, description = view.description) {
        ReadingRow(
            GlanceModifier.fillMaxWidth(),
            Alignment.CenterVertically,
            listOfNotNull(
                cell { ClockFace(view, BigClockMetrics.CLOCK_SP * fit.clockScale) },
                if (view.marker.isEmpty()) null else cell {
                    WidgetLine(view.marker, BigClockMetrics.MARKER_SP * fit.clockScale, Tone.MUTED, modifier = direction.paddingStart((BigClockMetrics.MARKER_GAP_DP * fit.clockScale).dp))
                },
                view.weather?.let { weather ->
                    cell {
                        CompactTemperature(weather, BigClockMetrics.COMPACT_TEMPERATURE_SP * fit.clockScale, direction.paddingStart((BigClockMetrics.COMPACT_TEMPERATURE_GAP_DP * fit.clockScale).dp))
                    }
                },
                cell {
                    Column(GlanceModifier.defaultWeight().then(direction.paddingStart((BigClockMetrics.SIDE_GAP_DP * scale).dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                        val countdown = @Composable { WidgetLine(view.countdown, BigClockMetrics.COUNTDOWN_SP * scale, Tone.COUNTDOWN, bold = true) }
                        if (fit.stops) StopsRow(view, scale, tones, GlanceModifier.fillMaxWidth(), countdown) else countdown()
                        ClockBar(view, fit.sideWidth, rtl, GlanceModifier.padding(top = gap * 2))
                    }
                },
            ),
        )
    }
}

/** The previous Stop, [countdown] centred between, and the next Stop (bold): Big Clock's middle line. */
@Composable
private fun StopsRow(view: ClockView, scale: Float, tones: ClockTones, modifier: GlanceModifier, countdown: @Composable () -> Unit) {
    ReadingRow(
        modifier,
        Alignment.CenterVertically,
        listOf(
            cell { WidgetLine(view.previous, BigClockMetrics.STOP_SP * scale, tones.previous) },
            cell {
                Box(GlanceModifier.defaultWeight().padding(horizontal = (BigClockMetrics.ROW_GAP_DP * scale).dp), contentAlignment = Alignment.Center) {
                    countdown()
                }
            },
            cell { WidgetLine(view.next, BigClockMetrics.STOP_SP * scale, tones.next, bold = true) },
        ),
    )
}

/** The bar from the previous Stop to the next, [width] dp wide, in the style's accent; every Clock layout draws it. */
@Composable
internal fun ClockBar(view: ClockView, width: Float, rtl: Boolean, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    Box(modifier) {
        WidgetDrawing(
            WidgetArt.progressLine(context, width, view.elapsed, rtl, context.colorOf(style.accent), context.colorOf(style.accentSoft)),
            width,
            WidgetArt.LINE_HEIGHT_DP,
        )
    }
}

/**
 * The big thin clock: a platform `TextClock` the launcher ticks in the phone's 12/24-hour setting. When the launcher
 * would write other digits than the user's, it shows [ClockView.clockTime] instead (a quoted pattern, so the
 * `TextClock` prints it as is) and each minute's render moves it on.
 */
@Composable
internal fun ClockFace(view: ClockView, sp: Float, tone: Tone = Tone.TEXT) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val clock = RemoteViews(context.packageName, if (style.shadowedText) R.layout.widget_clock_shadow else R.layout.widget_clock).apply {
        val fixed = "'${view.clockTime}'"
        setCharSequence(R.id.clock, "setFormat12Hour", if (view.ticking) "h:mm" else fixed)
        setCharSequence(R.id.clock, "setFormat24Hour", if (view.ticking) "HH:mm" else fixed)
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, sp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorStateList(R.id.clock, "setTextColor", style.colorOf(tone))
        } else {
            setTextColor(R.id.clock, context.getColor(style.colorOf(tone)))
        }
        LocalWidgetDirection.current.applyTo(this, R.id.clock)
    }
    AndroidRemoteViews(clock, GlanceModifier.wrapContentWidth().wrapContentHeight())
}

/** The Big Clock entry in the widget picker (4×2, down to 4×1). */
class BigClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BigClockWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopClockTicksIfNoneLeft(context)
    }
}

/**
 * Every Clock Widget's receiver calls this from `onDisabled`: the minute re-render stops with the last Clock widget.
 * Synchronous (the system's widget list, no coroutine), so it is done before the broadcast ends.
 */
internal fun stopClockTicksIfNoneLeft(context: Context) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    val placed = Widgets.clockReceivers.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    if (!placed) ClockTickReceiver.cancel(context)
}

/** The weather block's sizes at scale 1, per Clock layout. */
internal data class WeatherSizes(
    val temperatureSp: Float,
    val detailSp: Float,
    val sunDp: Float,
    val iconDp: Float,
    val iconGapDp: Float,
    val itemGapDp: Float,
    val lineGapDp: Float,
) {
    fun temperatureWidth(w: ClockWeather, s: Float, fontScale: Float) = sunDp * s + iconGapDp * s + textWidthDp(w.temperature, temperatureSp * s, fontScale)

    fun detailsWidth(w: ClockWeather, s: Float, fontScale: Float) =
        2 * (iconDp * s + iconGapDp * s) + itemGapDp * s + textWidthDp(w.humidity, detailSp * s, fontScale) + textWidthDp(w.rain, detailSp * s, fontScale)

    /** The temperature over the humidity and the chance of rain (Big Clock, Gold Clock). */
    fun width(w: ClockWeather, s: Float, fontScale: Float) = maxOf(temperatureWidth(w, s, fontScale), detailsWidth(w, s, fontScale))

    fun height(w: ClockWeather, s: Float, fontScale: Float) = maxOf(sunDp * s, lineHeightDp(w.temperature, temperatureSp * s, fontScale).toFloat()) +
        lineGapDp * s + maxOf(iconDp * s, lineHeightDp(w.humidity, detailSp * s, fontScale).toFloat())

    /** All three on one line (Clock & Prayer, under the clock). */
    fun lineWidth(w: ClockWeather, s: Float, fontScale: Float) = temperatureWidth(w, s, fontScale) + itemGapDp * s + detailsWidth(w, s, fontScale)

    fun lineHeight(w: ClockWeather, s: Float, fontScale: Float) =
        maxOf(sunDp * s, lineHeightDp(w.temperature, temperatureSp * s, fontScale).toFloat(), lineHeightDp(w.humidity, detailSp * s, fontScale).toFloat())
}

/** The width of [CompactTemperature] at [sp]. */
internal fun compactTemperatureWidth(w: ClockWeather, sp: Float, fontScale: Float) = sp * 0.8f + 2 + textWidthDp(w.temperature, sp, fontScale)

/** A thin vertical line in the style's divider colour. */
@Composable
internal fun Hairline(heightDp: Float, modifier: GlanceModifier = GlanceModifier) {
    val style = LocalWidgetStyle.current
    Box(modifier) {
        Image(ImageProvider(R.drawable.widget_rect), contentDescription = null, modifier = GlanceModifier.width(1.dp).height(heightDp.dp), colorFilter = tint(style.divider))
    }
}

/** The sun in the accent and the temperature: «☀ ٢٩°». */
@Composable
private fun Temperature(w: ClockWeather, sizes: WeatherSizes, s: Float) {
    val style = LocalWidgetStyle.current
    val direction = LocalWidgetDirection.current
    ReadingRow(
        verticalAlignment = Alignment.CenterVertically,
        children = listOf(
            cell { Image(ImageProvider(R.drawable.widget_weather_sun), null, GlanceModifier.size((sizes.sunDp * s).dp), colorFilter = tint(style.accent)) },
            cell { WidgetLine(w.temperature, sizes.temperatureSp * s, Tone.TEXT, modifier = direction.paddingStart((sizes.iconGapDp * s).dp)) },
        ),
    )
}

/** «💧 ٣٨٪  🌧 ٥٪» in the muted colour. */
@Composable
private fun WeatherDetails(w: ClockWeather, sizes: WeatherSizes, s: Float, modifier: GlanceModifier = GlanceModifier) {
    val style = LocalWidgetStyle.current
    val direction = LocalWidgetDirection.current
    val icon = GlanceModifier.size((sizes.iconDp * s).dp)
    val gap = direction.paddingStart((sizes.iconGapDp * s).dp)
    ReadingRow(
        modifier,
        Alignment.CenterVertically,
        listOf(
            cell { Image(ImageProvider(R.drawable.widget_weather_drop), null, icon, colorFilter = tint(style.muted)) },
            cell { WidgetLine(w.humidity, sizes.detailSp * s, Tone.MUTED, modifier = gap) },
            cell { Box(direction.paddingStart((sizes.itemGapDp * s).dp)) { Image(ImageProvider(R.drawable.widget_weather_rain), null, icon, colorFilter = tint(style.muted)) } },
            cell { WidgetLine(w.rain, sizes.detailSp * s, Tone.MUTED, modifier = gap) },
        ),
    )
}

/** Beside the clock: the temperature, and under it the humidity and the chance of rain. */
@Composable
internal fun WeatherBlock(w: ClockWeather, sizes: WeatherSizes, s: Float) {
    Column(horizontalAlignment = LocalWidgetDirection.current.start) {
        Temperature(w, sizes, s)
        WeatherDetails(w, sizes, s, GlanceModifier.padding(top = (sizes.lineGapDp * s).dp))
    }
}

/** Under the clock (Clock & Prayer): the temperature, the humidity and the chance of rain on one line. */
@Composable
internal fun WeatherLine(w: ClockWeather, sizes: WeatherSizes, s: Float, modifier: GlanceModifier = GlanceModifier) {
    val direction = LocalWidgetDirection.current
    ReadingRow(
        modifier,
        Alignment.CenterVertically,
        listOf(
            cell { Temperature(w, sizes, s) },
            cell { WeatherDetails(w, sizes, s, direction.paddingStart((sizes.itemGapDp * s).dp)) },
        ),
    )
}

/** At 4×1, the temperature alone after the clock. */
@Composable
internal fun CompactTemperature(w: ClockWeather, sp: Float, modifier: GlanceModifier = GlanceModifier) {
    val style = LocalWidgetStyle.current
    val direction = LocalWidgetDirection.current
    ReadingRow(
        modifier,
        Alignment.CenterVertically,
        listOf(
            cell { Image(ImageProvider(R.drawable.widget_weather_sun), null, GlanceModifier.size((sp * 0.8f).dp), colorFilter = tint(style.accent)) },
            cell { WidgetLine(w.temperature, sp, Tone.TEXT, modifier = direction.paddingStart(2.dp)) },
        ),
    )
}
