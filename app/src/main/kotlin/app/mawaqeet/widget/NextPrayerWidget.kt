package app.mawaqeet.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import app.mawaqeet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * The Next Prayer Widget Variant on the deep surface (widget design board, 2026-11-10), in two sizes: 4×2 (a label
 * with the place and the alert's kind, the name and time side by side, the gold countdown, and a progress line from
 * the previous Prayer with both ends named) and 2×2 (the name large over its time and countdown, and the line). The
 * 1×1 is [NextPrayerRingWidget]. A thin layer over [NextPrayerContent]; it re-renders on [Widgets.refreshAll] and every
 * half hour, which moves the line. Tapping opens the app on today's Prayer Times.
 */
class NextPrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> NextPrayerView.of(NextPrayerContent.of(core.displayDay(), Clock.systemUTC().instant(), text.formatter, core.alerts()), text) },
        deep = true,
        layout = { view, rtl ->
            // In Exact mode the launcher's size is known on every Android version; when it isn't, 4×2 is the default.
            val width = LocalSize.current.width.value
            val wide = width <= 0f || width >= NextPrayerMetrics.WIDE_FROM_DP
            if (wide) WideLayout(view, rtl) else SquareLayout(view, rtl)
        },
    )
}

/**
 * The 1×1 Next Prayer: a ring that fills up to the next Prayer, its name inside, and the countdown under it. Same
 * content and colours as [NextPrayerWidget].
 */
class NextPrayerRingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> NextPrayerView.of(NextPrayerContent.of(core.displayDay(), Clock.systemUTC().instant(), text.formatter, core.alerts()), text) },
        deep = true,
        layout = { view, rtl -> RingLayout(view, rtl) },
    )
}

/** Everything the layouts draw, already in the user's language and digits. */
internal data class NextPrayerView(
    /** «الصلاة القادمة · القاهرة», or «… · غدًا» after Isha. */
    val label: String,
    /** «القادمة», the 2×2's label. */
    val shortLabel: String,
    val name: String,
    val time: String,
    /** The line's two ends: «الظهر ١١:٣٩» and «العصر ١٤:٤٤». */
    val from: String,
    val to: String,
    val progress: Float,
    val chip: String,
    val audible: Boolean,
    /** `Chronometer` format, e.g. "بعد %s". */
    val countdownFormat: String,
    /** `SystemClock.elapsedRealtime()` at which the countdown reaches zero. */
    val countdownBase: Long,
) {
    /** The longest the countdown reads, for measuring. */
    val countdownSample: String get() = countdownFormat.format(NextPrayerMetrics.COUNTDOWN_SAMPLE)

    companion object {
        fun of(content: NextPrayerContent, text: WidgetText): NextPrayerView {
            val s = text.strings
            val name = s.getString(content.nameRes)
            val where = if (content.isTomorrow) s.getString(R.string.widget_tomorrow) else text.place
            return NextPrayerView(
                label = s.getString(R.string.widget_next_prayer) + if (where.isEmpty()) "" else " · $where",
                shortLabel = s.getString(R.string.widget_next_short),
                name = name,
                time = content.time,
                from = "${s.getString(content.previousNameRes)} ${content.previousTime}",
                to = "$name ${content.time}",
                progress = content.progress,
                chip = s.getString(content.alertRes),
                audible = content.audible,
                countdownFormat = s.getString(R.string.widget_countdown_format),
                countdownBase = SystemClock.elapsedRealtime() + content.untilNext.toMillis(),
            )
        }
    }
}

/** Sizes of the three layouts, from the widget design board; each shrinks as a whole when the launcher gives less room. */
internal object NextPrayerMetrics {
    /** From this width the 4×2 layout; below it the 2×2. */
    const val WIDE_FROM_DP = 220f
    const val WIDE_MIN_WIDTH = 250f
    const val WIDE_MIN_HEIGHT = 110f
    val WIDE_PADDING_H = 20.dp
    val WIDE_PADDING_V = 14.dp
    const val LABEL_SP = 12f
    const val CHIP_SP = 11f
    const val NAME_SP = 32f
    const val TIME_SP = 32f
    const val COUNTDOWN_SP = 17f
    const val ENDS_SP = 11f
    const val NAME_TIME_GAP_DP = 12f
    const val LINE_GAP_DP = 6f

    val SQUARE_PADDING_H = 16.dp
    val SQUARE_PADDING_V = 14.dp
    const val SQUARE_NAME_SP = 30f
    const val SQUARE_TIME_SP = 18f
    const val SQUARE_COUNTDOWN_SP = 14f

    val RING_PADDING = 6.dp
    /** The narrow one-cell the ring is laid out for when the launcher's size isn't known. */
    const val RING_MIN_WIDTH = 57f
    const val RING_MIN_HEIGHT = 60f
    const val RING_NAME_SP = 13f
    const val RING_COUNTDOWN_SP = 11f
    const val RING_MIN_COUNTDOWN_SP = 8f
    /** The ring's share of the cell's smaller side, before the countdown line is taken off. */
    const val RING_MAX_DP = 56f

    /**
     * How far the 4×2 and 2×2 grow past the board's sizes when the launcher gives them more room: phones' 4×2 cells
     * are often half again as tall as the board's.
     */
    const val MAX_GROW = 1.7f
    /** Room kept between the countdown and the progress line, so a grown widget doesn't read crammed. */
    const val BREATH_DP = 10f

    /** The longest a countdown reads. */
    const val COUNTDOWN_SAMPLE = "00:00:00"

    /** The 4×2's scale: the label, name and time, countdown, line and its ends fit the height; name and time the width. */
    fun wideScale(view: NextPrayerView, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Float {
        val across = widthDp - 2 * WIDE_PADDING_H.value
        val room = heightDp - 2 * WIDE_PADDING_V.value - 1
        return fitScale(floor = 0.4f, start = MAX_GROW) {
            textWidthDp(view.label, LABEL_SP * it, fontScale, bold = true) + chipWidthDp(view, it, fontScale) + 8 <= across &&
                textWidthDp(view.name, NAME_SP * it, fontScale, bold = true) + NAME_TIME_GAP_DP + textWidthDp(view.time, TIME_SP * it, fontScale, bold = true) <= across &&
                lineHeightDp(view.label, LABEL_SP * it, fontScale, bold = true) + 4 * it +
                maxOf(lineHeightDp(view.name, NAME_SP * it, fontScale, bold = true), lineHeightDp(view.time, TIME_SP * it, fontScale, bold = true)) +
                lineHeightDp(view.countdownSample, COUNTDOWN_SP * it, fontScale, bold = true) + LINE_GAP_DP * it +
                BREATH_DP * it + WidgetArt.LINE_HEIGHT_DP + 2 + lineHeightDp(view.from, ENDS_SP * it, fontScale) <= room
        }
    }

    /** The alert chip's width: bell, gap, words and the pill's padding. */
    private fun chipWidthDp(view: NextPrayerView, s: Float, fontScale: Float) =
        (12 + 4 + 16) * s + textWidthDp(view.chip, CHIP_SP * s, fontScale, bold = true)

    /** The 2×2's scale: four lines and the progress line, each within the width, all within the height. */
    fun squareScale(view: NextPrayerView, widthDp: Float, heightDp: Float, fontScale: Float = 1f): Float {
        val across = widthDp - 2 * SQUARE_PADDING_H.value
        val room = heightDp - 2 * SQUARE_PADDING_V.value - 1
        val lines = listOf(view.name to SQUARE_NAME_SP, view.time to SQUARE_TIME_SP, view.countdownSample to SQUARE_COUNTDOWN_SP)
        return fitScale(floor = 0.4f, start = MAX_GROW) { s ->
            lines.all { (text, sp) -> textWidthDp(text, sp * s, fontScale, bold = true) <= across } &&
                lineHeightDp(view.shortLabel, LABEL_SP * s, fontScale, bold = true) +
                lines.sumOf { (text, sp) -> lineHeightDp(text, sp * s, fontScale, bold = true) } + (LINE_GAP_DP + BREATH_DP) * s + WidgetArt.LINE_HEIGHT_DP <= room
        }
    }

    /** The 1×1's ring diameter, the name's and the countdown's sizes, and the countdown's format. */
    data class RingSizes(val ringDp: Float, val nameSp: Float, val countdownSp: Float, val countdownFormat: String)

    fun ringSizes(name: String, countdownFormat: String, widthDp: Float, heightDp: Float, fontScale: Float = 1f): RingSizes {
        val across = widthDp - 2 * RING_PADDING.value
        fun countdownSp(format: String) = RING_COUNTDOWN_SP * fitScale(floor = 0.5f) {
            textWidthDp(format.format(COUNTDOWN_SAMPLE), RING_COUNTDOWN_SP * it, fontScale, bold = true) <= across
        }
        // The word «بعد» gives way first when the cell is narrow.
        val format = if (countdownSp(countdownFormat) >= RING_MIN_COUNTDOWN_SP + 1) countdownFormat else "%s"
        val countdownSp = countdownSp(format)
        // The sample's Latin digits are shorter than Arabic-Indic ones: measure with the name's script too.
        val countdownLine = maxOf(
            lineHeightDp(format.format(COUNTDOWN_SAMPLE), countdownSp, fontScale, bold = true),
            lineHeightDp(name, countdownSp, fontScale, bold = true),
        )
        val ring = minOf(RING_MAX_DP, across, heightDp - 2 * RING_PADDING.value - countdownLine - 2).coerceAtLeast(24f)
        val inner = ring - 10
        val nameSp = RING_NAME_SP * fitScale(floor = 0.5f) {
            textWidthDp(name, RING_NAME_SP * it, fontScale, bold = true) <= inner &&
                lineHeightDp(name, RING_NAME_SP * it, fontScale, bold = true) <= inner
        }
        return RingSizes(ring, nameSp, countdownSp, format)
    }
}

@Composable
private fun WideLayout(view: NextPrayerView, rtl: Boolean) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val size = LocalSize.current
    val fontScale = context.resources.configuration.fontScale
    // On Android 8–11 the launcher's size isn't known: lay out for the smallest 4×2.
    val width = if (style.responsive) size.width.value else NextPrayerMetrics.WIDE_MIN_WIDTH
    val height = if (style.responsive) size.height.value else NextPrayerMetrics.WIDE_MIN_HEIGHT
    val scale = NextPrayerMetrics.wideScale(view, width, height, fontScale)
    val across = width - 2 * NextPrayerMetrics.WIDE_PADDING_H.value
    val direction = LocalWidgetDirection.current
    WidgetFrame(
        rtl,
        horizontal = NextPrayerMetrics.WIDE_PADDING_H,
        vertical = NextPrayerMetrics.WIDE_PADDING_V,
        verticalAlignment = Alignment.Top,
        decoration = {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = direction.topEnd) {
                val k = scale.coerceAtLeast(1f)
                WidgetDrawing(WidgetArt.star(context, 150f * k, 120f * k, 210f * k, 41f * k, 35f * k, rtl, context.colorOf(R.color.widget_deep_star)), 150f * k, 120f * k)
            }
        },
    ) {
        ReadingRow(
            GlanceModifier.fillMaxWidth(),
            Alignment.CenterVertically,
            listOf(
                cell { WidgetLine(view.label, NextPrayerMetrics.LABEL_SP * scale, Tone.MUTED, bold = true, modifier = GlanceModifier.defaultWeight()) },
                cell { AlertChip(view, scale) },
            ),
        )
        ReadingRow(
            GlanceModifier.fillMaxWidth().padding(top = (4 * scale).dp),
            Alignment.Bottom,
            listOf(
                cell { WidgetLine(view.name, NextPrayerMetrics.NAME_SP * scale, Tone.TEXT, bold = true, modifier = GlanceModifier.defaultWeight()) },
                cell { WidgetLine(view.time, NextPrayerMetrics.TIME_SP * scale, Tone.TEXT, bold = true) },
            ),
        )
        Countdown(view.countdownFormat, view.countdownBase, NextPrayerMetrics.COUNTDOWN_SP * scale)
        Box(GlanceModifier.defaultWeight()) {}
        ProgressLine(view.progress, across, rtl)
        ReadingRow(
            GlanceModifier.fillMaxWidth().padding(top = 2.dp),
            Alignment.CenterVertically,
            listOf(
                cell { WidgetLine(view.from, NextPrayerMetrics.ENDS_SP * scale, Tone.MUTED, modifier = GlanceModifier.defaultWeight()) },
                cell { WidgetLine(view.to, NextPrayerMetrics.ENDS_SP * scale, Tone.MUTED) },
            ),
        )
    }
}

@Composable
private fun SquareLayout(view: NextPrayerView, rtl: Boolean) {
    val context = LocalContext.current
    val size = LocalSize.current
    val fontScale = context.resources.configuration.fontScale
    val scale = NextPrayerMetrics.squareScale(view, size.width.value, size.height.value, fontScale)
    val across = size.width.value - 2 * NextPrayerMetrics.SQUARE_PADDING_H.value
    val direction = LocalWidgetDirection.current
    val style = LocalWidgetStyle.current
    WidgetFrame(
        rtl,
        horizontal = NextPrayerMetrics.SQUARE_PADDING_H,
        vertical = NextPrayerMetrics.SQUARE_PADDING_V,
        verticalAlignment = Alignment.Top,
        decoration = {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = direction.bottomEnd) {
                val k = scale.coerceAtLeast(1f)
                WidgetDrawing(WidgetArt.star(context, 110f * k, 100f * k, 150f * k, 17f * k, 83f * k, rtl, context.colorOf(R.color.widget_deep_star)), 110f * k, 100f * k)
            }
        },
    ) {
        ReadingRow(
            GlanceModifier.fillMaxWidth(),
            Alignment.CenterVertically,
            listOf(
                cell { WidgetLine(view.shortLabel, NextPrayerMetrics.LABEL_SP * scale, Tone.MUTED, bold = true, modifier = GlanceModifier.defaultWeight()) },
                cell {
                    Image(
                        ImageProvider(if (view.audible) R.drawable.ic_bell else R.drawable.ic_bell_off),
                        contentDescription = view.chip,
                        modifier = GlanceModifier.size((14 * scale).dp),
                        colorFilter = tint(style.muted),
                    )
                },
            ),
        )
        WidgetLine(view.name, NextPrayerMetrics.SQUARE_NAME_SP * scale, Tone.TEXT, bold = true)
        WidgetLine(view.time, NextPrayerMetrics.SQUARE_TIME_SP * scale, Tone.TEXT, bold = true)
        Countdown(view.countdownFormat, view.countdownBase, NextPrayerMetrics.SQUARE_COUNTDOWN_SP * scale)
        Box(GlanceModifier.defaultWeight()) {}
        ProgressLine(view.progress, across, rtl)
    }
}

@Composable
private fun RingLayout(view: NextPrayerView, rtl: Boolean) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val size = LocalSize.current
    val fontScale = context.resources.configuration.fontScale
    // On Android 8–11 the launcher's size isn't known: lay out for a narrow one-cell.
    val width = if (style.responsive) size.width.value else NextPrayerMetrics.RING_MIN_WIDTH
    val height = if (style.responsive) size.height.value else NextPrayerMetrics.RING_MIN_HEIGHT
    val sizes = NextPrayerMetrics.ringSizes(view.name, view.countdownFormat, width, height, fontScale)
    WidgetFrame(rtl, horizontal = NextPrayerMetrics.RING_PADDING, vertical = NextPrayerMetrics.RING_PADDING) {
        Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(GlanceModifier.size(sizes.ringDp.dp), contentAlignment = Alignment.Center) {
                WidgetDrawing(
                    WidgetArt.ring(context, sizes.ringDp, view.progress, context.colorOf(style.accent), context.colorOf(style.accentSoft)),
                    sizes.ringDp,
                    sizes.ringDp,
                )
                WidgetLine(view.name, sizes.nameSp, Tone.TEXT, bold = true, center = true)
            }
        }
        Box(GlanceModifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
            Countdown(sizes.countdownFormat, view.countdownBase, sizes.countdownSp)
        }
    }
}

/** The 4×2's chip: a bell (struck out when silent) and the kind of alert, on a soft pill. */
@Composable
private fun AlertChip(view: NextPrayerView, scale: Float) {
    val style = LocalWidgetStyle.current
    ReadingRow(
        GlanceModifier.wrapContentWidth().wrapContentHeight()
            .background(ImageProvider(R.drawable.widget_chip_deep))
            .padding(horizontal = (8 * scale).dp, vertical = (2 * scale).dp),
        Alignment.CenterVertically,
        listOf(
            cell {
                Image(
                    ImageProvider(if (view.audible) R.drawable.ic_bell else R.drawable.ic_bell_off),
                    contentDescription = null,
                    modifier = GlanceModifier.size((12 * scale).dp),
                    colorFilter = tint(style.text),
                )
            },
            cell {
                WidgetLine(view.chip, NextPrayerMetrics.CHIP_SP * scale, Tone.TEXT, bold = true, modifier = LocalWidgetDirection.current.paddingStart((4 * scale).dp))
            },
        ),
    )
}

/** The gold line from the previous Prayer to the next, [widthDp] across. */
@Composable
private fun ProgressLine(progress: Float, widthDp: Float, rtl: Boolean) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    WidgetDrawing(
        WidgetArt.progressLine(context, widthDp, progress, rtl, context.colorOf(style.accent), context.colorOf(style.accentSoft)),
        widthDp,
        WidgetArt.LINE_HEIGHT_DP,
        modifier = GlanceModifier.padding(top = 0.dp),
    )
}

/** A platform count-down `Chronometer`, ticked by the launcher; it always shows seconds. In the gold accent. */
@Composable
private fun Countdown(format: String, base: Long, sp: Float, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val chronometer = RemoteViews(
        context.packageName,
        if (style.shadowedText) R.layout.widget_countdown_shadow else R.layout.widget_countdown,
    ).apply {
        setChronometer(R.id.countdown, base, format, true)
        setChronometerCountDown(R.id.countdown, true)
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, sp)
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

/** Both Next Prayer picker entries re-render every half hour (their info's `updatePeriodMillis`), moving the line. */
private fun refreshNextPrayer(context: Context) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        Widgets.refresh(context.applicationContext, listOf(::NextPrayerWidget, ::NextPrayerRingWidget))
    }
}

/** The 4×2 entry in the widget picker; resizes down to 2×2. */
class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshNextPrayer(context)
    }
}

/** The 1×1 ring entry in the widget picker. */
class NextPrayerCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerRingWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshNextPrayer(context)
    }
}
