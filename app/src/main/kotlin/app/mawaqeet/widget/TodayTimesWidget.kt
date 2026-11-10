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
import app.mawaqeet.R

/**
 * The Today's Prayer Times Widget Variant (4×2): a header (title and Hijri date), then the five Prayers' columns with
 * the next Prayer filled with the accent, and no countdown. A thin layer over [TodayTimesContent].
 */
class TodayTimesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> TodayTimesView.of(TodayTimesContent.of(core.displayDay(), text.formatter), text) },
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
    const val DOT_DP = 20f
    /** Between the header and the columns, at full size. */
    const val HEADER_GAP_DP = 8f
    /** Inside a column, above and below its text, at full size. */
    const val CELL_PADDING_V_DP = 6f
    const val NAME_TIME_GAP_DP = 2f
    const val DOT_GAP_DP = 4f
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

    /**
     * The scale at which the header and the columns (with their dots when [dots]) fit [heightDp], from [step] (the
     * size step for a roomier widget) down.
     */
    fun heightScale(header: String?, name: String, time: String, heightDp: Float, dots: Boolean, step: Float, fontScale: Float = 1f): Float {
        val room = heightDp - 2 * verticalPadding.value - 1
        return step * fitScale(floor = 0.4f) {
            val s = step * it
            (if (header != null) lineHeightDp(header, HEADER_SP * s, fontScale, bold = true) + HEADER_GAP_DP * s else 0f) +
                2 * CELL_PADDING_V_DP * s + lineHeightDp(name, NAME_SP * s, fontScale) + NAME_TIME_GAP_DP * s +
                lineHeightDp(time, TIME_SP * s, fontScale, bold = true) +
                (if (dots) DOT_GAP_DP * s + DOT_DP * s else 0f) <= room
        }
    }
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
        fun of(header: String, names: List<String>, times: List<String>, dots: Boolean): CellSizes {
            val style = LocalWidgetStyle.current
            val size = LocalSize.current
            // On Android 8–11 one layout at the default size: the smallest, whatever the launcher gives.
            val width = if (style.responsive) size.width.value else TodayTimesMetrics.MIN_WIDTH_DP.toFloat()
            val height = if (style.responsive) size.height.value else TodayTimesMetrics.MIN_HEIGHT_DP.toFloat()
            val step = textStep(TodayTimesMetrics.MIN_WIDTH_DP.toFloat(), TodayTimesMetrics.MIN_HEIGHT_DP.toFloat())
            val fontScale = LocalContext.current.resources.configuration.fontScale
            val withHeader = TodayTimesMetrics.heightScale(header, names.first(), times.first(), height, dots, step, fontScale)
            val showHeader = withHeader >= step * HEADER_MIN_SCALE
            val scale = if (showHeader) withHeader else TodayTimesMetrics.heightScale(null, names.first(), times.first(), height, dots, step, fontScale)
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

private data class TodayTimesView(val title: String, val date: WidgetDate, val columns: List<Cell>, val allNames: List<String>) {
    data class Cell(val name: String, val time: String, val isNext: Boolean)

    companion object {
        fun of(content: TodayTimesContent, text: WidgetText) = TodayTimesView(
            title = text.strings.getString(R.string.widget_today_times),
            date = content.date,
            columns = content.columns.map { Cell(text.strings.getString(it.nameRes), it.time, it.isNext) },
            allNames = TodayTimesMetrics.NAMES.map { text.strings.getString(it) },
        )
    }
}

@Composable
private fun TodayTimesLayout(view: TodayTimesView, rtl: Boolean) {
    val sizes = CellSizes.of(view.title, view.allNames, view.columns.map { it.time }, dots = false)
    WidgetFrame(rtl, horizontal = TodayTimesMetrics.horizontalPadding, vertical = TodayTimesMetrics.verticalPadding) {
        if (sizes.showHeader) WidgetHeader(view.title, view.date.hijri, sizes.headerSp, endEllipsizes = true)
        PrayerColumns(sizes, view.columns.map { PrayerColumn(it.name, it.time, it.isNext) })
    }
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

/** One column; the outer box makes the gap to its neighbours, the inner column carries the next Prayer's fill. */
@Composable
private fun RowScope.PrayerCell(column: PrayerColumn, sizes: CellSizes) {
    val style = LocalWidgetStyle.current
    val vertical = (TodayTimesMetrics.CELL_PADDING_V_DP * sizes.scale).dp
    val inner = GlanceModifier.fillMaxWidth().padding(horizontal = TodayTimesMetrics.cellPadding, vertical = vertical)
    Box(GlanceModifier.defaultWeight().padding(horizontal = TodayTimesMetrics.cellGap / 2)) {
        Column(
            modifier = if (column.isNext) {
                inner.background(ImageProvider(R.drawable.widget_cell_next), colorFilter = tint(style.accent))
            } else {
                inner
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetLine(column.name, sizes.nameSp, if (column.isNext) Tone.ON_ACCENT else Tone.MUTED, center = true)
            WidgetLine(
                column.time,
                sizes.timeSp,
                if (column.isNext) Tone.ON_ACCENT else Tone.TEXT,
                bold = true,
                center = true,
                modifier = GlanceModifier.padding(top = (TodayTimesMetrics.NAME_TIME_GAP_DP * sizes.scale).dp),
            )
            column.dot?.let { dot ->
                Box(GlanceModifier.padding(top = (TodayTimesMetrics.DOT_GAP_DP * sizes.scale).dp)) {
                    dot(TodayTimesMetrics.DOT_DP * sizes.scale)
                }
            }
        }
    }
}

/** The 4×2 entry in the widget picker. */
class TodayTimesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTimesWidget()
}
