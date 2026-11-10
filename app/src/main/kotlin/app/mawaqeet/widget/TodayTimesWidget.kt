package app.mawaqeet.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.size
import androidx.glance.Image
import androidx.glance.action.clickable
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import app.mawaqeet.R

/**
 * The Today's Prayer Times Widget Variant (widget design board, 2026-11-10): at 4×1 the five Prayers' columns, the
 * passed ones quieter and the next on a soft fill; at 4×2 also a header (the date, both calendars, and the place) and
 * the "day path", a thin curve the sun walks along from Fajr to Isha. No countdown. A thin layer over
 * [TodayTimesContent].
 */
class TodayTimesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> TodayTimesView.of(TodayTimesContent.of(core.displayDay(), text.formatter, java.time.Clock.systemUTC().instant()), text) },
        layout = { view, rtl -> TodayTimesLayout(view, rtl) },
    )
}

/**
 * Sizes of the five-column widgets (Today's Prayer Times, Today with Prayer Log), from the Widgets board: a 12sp
 * header, then columns of a 12sp name over a 16sp bold time (and a 20dp Prayer Log dot). Names and times shrink to
 * fit their column (English "Jumu'ah" is the widest) and everything shrinks together to fit the height, so nothing is
 * clipped down to the smallest 4×2.
 */
internal object TodayTimesMetrics {
    const val MIN_WIDTH_DP = 250
    const val MIN_HEIGHT_DP = 60
    val horizontalPadding = 18.dp
    val verticalPadding = 14.dp
    val cellGap = 4.dp
    val cellPadding = 1.dp
    const val COLUMNS = 5

    const val HEADER_SP = 12f
    const val NAME_SP = 12f
    const val TIME_SP = 16f
    const val DOT_DP = 26f
    /** Between the header and the columns, at full size. */
    const val HEADER_GAP_DP = 8f
    /** Inside a column, above and below its text, at full size. */
    const val CELL_PADDING_V_DP = 6f
    const val NAME_TIME_GAP_DP = 2f
    const val DOT_GAP_DP = 4f
    /** The day path's tallest, and the least room it needs to be drawn at all. */
    const val PATH_MAX_DP = 56f
    const val PATH_MIN_DP = 34f
    const val PATH_GAP_DP = 4f

    /** Text never shrinks below this to fit; past it a launcher font scale may still clip. */
    const val MIN_SP = 8f

    /** Every name a column can show, so the name size doesn't change on Fridays. */
    val NAMES = listOf(
        R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_jumuah,
        R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha,
    )

    /**
     * The largest size from [maxSp] down, in half-sp steps, at which the widest of [texts] fits in [roomDp], measured
     * with the system font the launcher draws in. [fontScale] is the device's font scale, which the launcher applies.
     */
    fun fitSp(texts: List<String>, roomDp: Float, maxSp: Float, bold: Boolean, fontScale: Float = 1f): Float {
        val paint = Paint().apply { typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        var sp = maxSp
        while (sp > MIN_SP) {
            paint.textSize = sp * fontScale
            if (texts.all { paint.measureText(it) <= roomDp }) break
            sp -= 0.5f
        }
        return sp
    }

    /** The width each of the five columns has for its text at a widget width of [widthDp]. */
    fun cellTextWidthDp(widthDp: Float): Float =
        (widthDp - 2 * horizontalPadding.value) / COLUMNS - cellGap.value - 2 * cellPadding.value

    /** The five columns' height at [scale]. */
    fun columnsHeight(name: String, time: String, scale: Float, dots: Boolean, fontScale: Float = 1f, markFirst: Boolean = false): Float =
        2 * CELL_PADDING_V_DP * scale + lineHeightDp(name, NAME_SP * scale, fontScale, bold = dots) + NAME_TIME_GAP_DP * scale +
            (if (markFirst) lineHeightDp(time, NAME_SP * scale, fontScale) else lineHeightDp(time, TIME_SP * scale, fontScale, bold = true)) +
            (if (dots) DOT_GAP_DP * scale + DOT_DP * scale else 0f)

    /**
     * The scale at which the header and the columns (with their dots when [dots]) fit [heightDp], from [step] (the
     * size step for a roomier widget) down.
     */
    fun heightScale(header: String?, name: String, time: String, heightDp: Float, dots: Boolean, step: Float, fontScale: Float = 1f, markFirst: Boolean = false): Float {
        val room = heightDp - 2 * verticalPaddingDp(heightDp) - 1
        return step * fitScale(floor = 0.4f) {
            val s = step * it
            (if (header != null) lineHeightDp(header, HEADER_SP * s, fontScale, bold = true) + HEADER_GAP_DP * s else 0f) +
                columnsHeight(name, time, s, dots, fontScale, markFirst) <= room
        }
    }

    /** One row of cells keeps little padding, so the Prayers keep their size. */
    fun verticalPaddingDp(heightDp: Float): Float = if (heightDp < SHORT_BELOW_DP) 6f else verticalPadding.value

    const val SHORT_BELOW_DP = 100f
}

/**
 * The sizes one five-column layout draws at, worked out by [TodayTimesMetrics]. In a short widget (one row of home
 * screen cells) the header gives way, so the Prayers keep a readable size: [showHeader] is false then.
 */
internal data class CellSizes(val scale: Float, val headerSp: Float, val nameSp: Float, val timeSp: Float, val showHeader: Boolean = true) {
    companion object {
        /** Below this share of its full size the header is dropped rather than shrinking everything further. */
        private const val HEADER_MIN_SCALE = 0.7f

        @Composable
        fun of(header: String, names: List<String>, times: List<String>, dots: Boolean, markFirst: Boolean = false): CellSizes {
            val style = LocalWidgetStyle.current
            val size = LocalSize.current
            // On Android 8–11 one layout at the default size: the smallest, whatever the launcher gives.
            val width = if (style.responsive) size.width.value else TodayTimesMetrics.MIN_WIDTH_DP.toFloat()
            val height = if (style.responsive) size.height.value else TodayTimesMetrics.MIN_HEIGHT_DP.toFloat()
            val step = textStep(TodayTimesMetrics.MIN_WIDTH_DP.toFloat(), TodayTimesMetrics.MIN_HEIGHT_DP.toFloat())
            val fontScale = LocalContext.current.resources.configuration.fontScale
            // One row of cells never has a header: the Prayers take all of it.
            val short = height < TodayTimesMetrics.SHORT_BELOW_DP
            val withHeader = if (short) 0f else TodayTimesMetrics.heightScale(header, names.first(), times.first(), height, dots, step, fontScale)
            val showHeader = withHeader >= step * HEADER_MIN_SCALE
            val scale = if (showHeader) withHeader else TodayTimesMetrics.heightScale(null, names.first(), times.first(), height, dots, step, fontScale, markFirst)
            val room = TodayTimesMetrics.cellTextWidthDp(width)
            return CellSizes(
                showHeader = showHeader,
                scale = scale,
                headerSp = TodayTimesMetrics.HEADER_SP * scale,
                nameSp = TodayTimesMetrics.fitSp(names, room, TodayTimesMetrics.NAME_SP * scale, bold = false, fontScale),
                timeSp = TodayTimesMetrics.fitSp(times, room, TodayTimesMetrics.TIME_SP * scale, bold = true, fontScale),
            )
        }
    }
}

private data class TodayTimesView(
    val date: WidgetDate,
    val place: String,
    val columns: List<Cell>,
    val allNames: List<String>,
    val path: WidgetArt.DayPath,
) {
    data class Cell(val name: String, val time: String, val isNext: Boolean, val isPast: Boolean)

    companion object {
        fun of(content: TodayTimesContent, text: WidgetText) = TodayTimesView(
            date = content.date,
            place = text.place,
            columns = content.columns.map { Cell(text.strings.getString(it.nameRes), it.time, it.isNext, it.isPast) },
            allNames = TodayTimesMetrics.NAMES.map { text.strings.getString(it) },
            path = WidgetArt.DayPath(content.nextIndex, content.fraction),
        )
    }
}

@Composable
private fun TodayTimesLayout(view: TodayTimesView, rtl: Boolean) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val size = LocalSize.current
    val fontScale = context.resources.configuration.fontScale
    val sizes = CellSizes.of(view.date.gregorian, view.allNames, view.columns.map { it.time }, dots = false)
    val width = if (style.responsive) size.width.value else TodayTimesMetrics.MIN_WIDTH_DP.toFloat()
    val height = if (style.responsive) size.height.value else TodayTimesMetrics.MIN_HEIGHT_DP.toFloat()
    val across = width - 2 * TodayTimesMetrics.horizontalPadding.value
    // The day path takes what the header and columns leave, up to its full height.
    val left = height - 2 * TodayTimesMetrics.verticalPaddingDp(height) - 1 -
        (if (sizes.showHeader) lineHeightDp(view.date.gregorian, sizes.headerSp, fontScale, bold = true) + TodayTimesMetrics.HEADER_GAP_DP * sizes.scale else 0f) -
        TodayTimesMetrics.columnsHeight(view.allNames.first(), view.columns.first().time, sizes.scale, dots = false, fontScale)
    val pathHeight = minOf(TodayTimesMetrics.PATH_MAX_DP, left - TodayTimesMetrics.PATH_GAP_DP)
    val showPath = sizes.showHeader && pathHeight >= TodayTimesMetrics.PATH_MIN_DP
    WidgetFrame(rtl, horizontal = TodayTimesMetrics.horizontalPadding, vertical = TodayTimesMetrics.verticalPaddingDp(height).dp) {
        if (sizes.showHeader) DateHeader(view.date, view.place, sizes.headerSp)
        if (showPath) {
            val colors = WidgetArt.DayPathColors(
                past = context.colorOf(style.accent),
                future = context.colorOf(style.faint),
                horizon = context.colorOf(style.divider),
                sun = context.colorOf(if (style.shadowedText) R.color.widget_over_gold else R.color.widget_sun),
                nextFill = context.colorOf(style.background),
            )
            WidgetDrawing(
                WidgetArt.dayPath(context, across, pathHeight, view.path, rtl, colors),
                across,
                pathHeight,
                modifier = GlanceModifier.padding(top = TodayTimesMetrics.PATH_GAP_DP.dp),
            )
        }
        PrayerColumns(
            sizes.copy(showHeader = sizes.showHeader && !showPath),
            view.columns.map { PrayerColumn(it.name, it.time, it.isNext, isPast = it.isPast) },
        )
    }
}

/** Today's Prayer Times header: the Gregorian date, the Hijri day and month quieter after it, and the place at the end. */
@Composable
private fun DateHeader(date: WidgetDate, place: String, sp: Float) {
    val direction = LocalWidgetDirection.current
    val style = LocalWidgetStyle.current
    ReadingRow(
        GlanceModifier.fillMaxWidth().wrapContentHeight(),
        Alignment.CenterVertically,
        listOf(
            cell { WidgetLine(date.gregorian, sp * 1.08f, Tone.TEXT, bold = true) },
            cell {
                Box(GlanceModifier.defaultWeight().then(direction.paddingStart(6.dp)), contentAlignment = direction.startAlignment) {
                    WidgetLine(date.hijriShort, sp, Tone.MUTED)
                }
            },
            cell {
                Image(ImageProvider(R.drawable.ic_pin), contentDescription = null, modifier = GlanceModifier.size((sp + 1).dp), colorFilter = tint(style.accent))
            },
            cell { WidgetLine(place, sp, Tone.MUTED, modifier = direction.paddingStart(3.dp)) },
        ),
    )
}

/** A widget's header: its title at the app language's start, and [end] (a date or a count) at its end. */
@Composable
internal fun WidgetHeader(title: String, end: String, sp: Float, endEllipsizes: Boolean = false) {
    val direction = LocalWidgetDirection.current
    ReadingRow(
        GlanceModifier.fillMaxWidth().wrapContentHeight(),
        Alignment.CenterVertically,
        if (endEllipsizes) {
            // A long Hijri date gives way to the title, and shortens at its end.
            listOf(
                cell { WidgetLine(title, sp, Tone.MUTED, bold = true) },
                cell {
                    Box(GlanceModifier.defaultWeight().then(direction.paddingStart(8.dp)), contentAlignment = direction.endAlignment) {
                        WidgetLine(end, sp, Tone.MUTED, bold = true)
                    }
                },
            )
        } else {
            listOf(
                cell { WidgetLine(title, sp, Tone.MUTED, bold = true, modifier = GlanceModifier.defaultWeight()) },
                cell { WidgetLine(end, sp, Tone.MUTED, bold = true, modifier = direction.paddingStart(8.dp)) },
            )
        },
    )
}

/** One column of a five-column widget: what it shows, and its Prayer Log dot, if any. */
internal data class PrayerColumn(
    val name: String,
    val time: String,
    val isNext: Boolean,
    val dot: (@Composable (sizeDp: Float) -> Unit)? = null,
    /** Its time has passed: the time is drawn quieter. */
    val isPast: Boolean = false,
    /** The mark goes between the name and the time (the 4×1 Today with Prayer Log). */
    val markFirst: Boolean = false,
    /** The column's own tap (marking a Prayer), and what TalkBack reads for it. */
    val onClick: androidx.glance.action.Action? = null,
    val description: String? = null,
)

/**
 * The five columns, oldest first in reading order, right under the header. The frame centres header and columns
 * together, so a taller widget than the content needs has even margins, not a gap between header and columns.
 */
@Composable
internal fun ColumnScope.PrayerColumns(sizes: CellSizes, columns: List<PrayerColumn>) {
    val gap = if (sizes.showHeader) (TodayTimesMetrics.HEADER_GAP_DP * sizes.scale).dp else 0.dp
    Box(
        GlanceModifier.fillMaxWidth().padding(top = gap),
        contentAlignment = Alignment.Center,
    ) {
        Row(GlanceModifier.fillMaxWidth()) {
            // No spacers between cells: Glance keeps at most 10 children in a Row, so the gap is each cell's margin.
            LocalWidgetDirection.current.inReadingOrder(columns).forEach { column -> PrayerCell(column, sizes) }
        }
    }
}

/** One column; the outer box makes the gap to its neighbours, the inner column carries the next Prayer's soft fill. */
@Composable
private fun RowScope.PrayerCell(column: PrayerColumn, sizes: CellSizes) {
    val style = LocalWidgetStyle.current
    val vertical = (TodayTimesMetrics.CELL_PADDING_V_DP * sizes.scale).dp
    var inner = GlanceModifier.fillMaxWidth().padding(horizontal = TodayTimesMetrics.cellPadding, vertical = vertical)
    if (column.isNext) inner = inner.background(ImageProvider(R.drawable.widget_cell_next), colorFilter = tint(style.cellFill))
    column.onClick?.let { inner = inner.clickable(it) }
    column.description?.let { text -> inner = inner.semantics { contentDescription = text } }
    val nameTone = if (column.isNext) Tone.ON_SOFT else Tone.MUTED
    val timeTone = when {
        column.isNext -> Tone.ON_SOFT
        column.isPast -> Tone.MUTED
        else -> Tone.TEXT
    }
    val gap = (TodayTimesMetrics.NAME_TIME_GAP_DP * sizes.scale).dp
    val mark = @Composable {
        column.dot?.let { dot ->
            Box(GlanceModifier.padding(top = (TodayTimesMetrics.DOT_GAP_DP * sizes.scale).dp)) {
                dot(TodayTimesMetrics.DOT_DP * sizes.scale)
            }
        }
    }
    Box(GlanceModifier.defaultWeight().padding(horizontal = TodayTimesMetrics.cellGap / 2)) {
        Column(
            modifier = inner,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetLine(column.name, sizes.nameSp, nameTone, bold = column.isNext || column.dot != null, center = true)
            if (column.markFirst) mark()
            WidgetLine(
                column.time,
                if (column.markFirst) sizes.nameSp else sizes.timeSp,
                timeTone,
                bold = !column.markFirst || column.isNext,
                center = true,
                modifier = GlanceModifier.padding(top = gap),
            )
            if (!column.markFirst) mark()
        }
    }
}

/** The 4×2 entry in the widget picker. */
class TodayTimesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTimesWidget()
}
