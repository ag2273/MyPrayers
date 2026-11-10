package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.background
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import app.mawaqeet.MainActivity
import app.mawaqeet.R

/**
 * The Week Prayer Log Widget Variant (widget design board, 2026-11-10), the last 7 Prayer Days oldest to newest in
 * reading direction with today's column on a soft band. At 4×3: a header with the dates and the week's total large, a
 * thin bar, each day's letter and number, a row of dots per Prayer with its own count, and a key. At 4×2: the title and
 * total, the day letters and the dots. Display only: the whole widget is one tap target that opens the Prayer Log
 * screen. A thin layer over [WeekLogContent].
 */
class WeekLogWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> WeekLogView.of(WeekLogContent.of(core.week(), text.formatter), text) },
        layout = { view, rtl -> WeekLogLayout(view, rtl) },
    )
}

private data class WeekLogView(
    val title: String,
    /** «٢٦ من ٣٢» and its two parts. */
    val total: String,
    val prayed: String,
    val outOf: String,
    val range: String,
    val share: Float,
    val letters: List<String>,
    val numbers: List<String>,
    val rows: List<Row>,
    /** Read out by TalkBack instead of the dots: e.g. "Fajr: Prayed, Not marked, …". */
    val rowDescriptions: List<String>,
    val legend: List<String>,
) {
    data class Row(val name: String, val cells: List<WeekLogContent.Cell>, val count: String)

    companion object {
        fun of(content: WeekLogContent, text: WidgetText): WeekLogView {
            val strings = text.strings
            val f = text.formatter
            val rows = content.rows.map { Row(strings.getString(it.nameRes), it.cells, strings.getString(R.string.widget_count, f.number(it.prayed), f.number(it.possible))) }
            val total = strings.getString(R.string.widget_count, content.prayed, content.possible)
            return WeekLogView(
                title = strings.getString(R.string.widget_week_log),
                total = total,
                prayed = content.prayed,
                outOf = total.removePrefix(content.prayed).trim(),
                range = if (content.days.isEmpty()) "" else f.dayRange(content.days.first(), content.days.last()),
                share = content.share,
                letters = content.days.map { f.narrowWeekday(it) },
                numbers = content.days.map { f.number(it.dayOfMonth) },
                rows = rows,
                rowDescriptions = rows.map { row ->
                    row.name + ": " + row.cells.filter { it != WeekLogContent.Cell.BEFORE_FIRST_USE }
                        .joinToString(", ") { strings.getString(cellLabelRes(it)) }
                },
                legend = listOf(R.string.widget_legend_prayed, R.string.widget_legend_missed, R.string.widget_legend_not_yet).map { strings.getString(it) },
            )
        }
    }
}

/** Sizes of the two week grids, from the widget design board; each shrinks as a whole to fit. */
internal object WeekLogMetrics {
    const val MIN_WIDTH_DP = 250f
    const val MIN_HEIGHT_DP = 110f
    /** From this height the full 4×3 layout. */
    const val FULL_FROM_DP = 230f
    val horizontalPadding = 16.dp
    val verticalPadding = 12.dp

    const val TITLE_SP = 13f
    const val COUNT_SP = 12f
    const val LETTER_SP = 10f
    const val NAME_SP = 12f
    const val DOT_DP = 12f
    const val ROW_DP = 21f
    const val LABEL_WIDTH_DP = 50f

    const val FULL_TITLE_SP = 15f
    const val FULL_TOTAL_SP = 24f
    const val FULL_NAME_SP = 13f
    const val FULL_DOT_DP = 16f
    const val FULL_ROW_DP = 28f
    const val FULL_LABEL_WIDTH_DP = 52f
    const val FULL_COUNT_WIDTH_DP = 44f
    const val SMALL_SP = 11f
    const val NOT_YET_DP = 5f

    fun compactScale(view: WeekLogViewText, heightDp: Float, step: Float, fontScale: Float = 1f): Float {
        val room = heightDp - 2 * verticalPadding.value - 1
        return step * fitScale(floor = 0.4f) {
            val s = step * it
            lineHeightDp(view.title, TITLE_SP * s, fontScale, bold = true) + 4 * s +
                lineHeightDp(view.letter, LETTER_SP * s, fontScale) + 5 * maxOf(ROW_DP * s, lineHeightDp(view.name, NAME_SP * s, fontScale, bold = true).toFloat()) <= room
        }
    }

    fun fullScale(view: WeekLogViewText, heightDp: Float, step: Float, fontScale: Float = 1f): Float {
        val room = heightDp - 2 * 14f - 1
        return step * fitScale(floor = 0.4f) {
            val s = step * it
            maxOf(
                lineHeightDp(view.title, FULL_TITLE_SP * s, fontScale, bold = true) + lineHeightDp(view.range, COUNT_SP * s, fontScale),
                lineHeightDp(view.prayed, FULL_TOTAL_SP * s, fontScale, bold = true),
            ) + 6 * s + 3 + 8 * s +
                lineHeightDp(view.letter, LETTER_SP * s, fontScale) + lineHeightDp(view.letter, SMALL_SP * s, fontScale) +
                5 * maxOf(FULL_ROW_DP * s, lineHeightDp(view.name, FULL_NAME_SP * s, fontScale, bold = true).toFloat()) +
                6 * s + lineHeightDp(view.legend, SMALL_SP * s, fontScale) <= room
        }
    }
}

/** The texts the week grids measure. */
internal data class WeekLogViewText(val title: String, val range: String, val prayed: String, val letter: String, val name: String, val legend: String)

@Composable
private fun WeekLogLayout(view: WeekLogView, rtl: Boolean) {
    val style = LocalWidgetStyle.current
    val size = LocalSize.current
    val width = if (style.responsive) size.width.value else WeekLogMetrics.MIN_WIDTH_DP
    val height = if (style.responsive) size.height.value else 180f
    val step = textStep(WeekLogMetrics.MIN_WIDTH_DP, 180f)
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val text = WeekLogViewText(view.title, view.range, view.prayed, view.letters.firstOrNull() ?: "", view.rows.first().name, view.legend.first())
    if (height >= WeekLogMetrics.FULL_FROM_DP) {
        FullWeek(view, rtl, WeekLogMetrics.fullScale(text, height, step, fontScale), width)
    } else {
        CompactWeek(view, rtl, WeekLogMetrics.compactScale(text, height, step, fontScale))
    }
}

@Composable
private fun CompactWeek(view: WeekLogView, rtl: Boolean, scale: Float) {
    val direction = LocalWidgetDirection.current
    WidgetFrame(rtl, horizontal = WeekLogMetrics.horizontalPadding, vertical = WeekLogMetrics.verticalPadding, opens = MainActivity.Opens.PRAYER_LOG, verticalAlignment = Alignment.Top) {
        ReadingRow(
            GlanceModifier.fillMaxWidth(),
            Alignment.CenterVertically,
            listOf(
                cell { WidgetLine(view.title, WeekLogMetrics.TITLE_SP * scale, Tone.TEXT, bold = true, modifier = GlanceModifier.defaultWeight()) },
                cell { WidgetLine(view.prayed, (WeekLogMetrics.COUNT_SP + 1) * scale, Tone.ACCENT, bold = true) },
                cell { WidgetLine(view.outOf, WeekLogMetrics.COUNT_SP * scale, Tone.MUTED, modifier = direction.paddingStart(3.dp)) },
            ),
        )
        DayHeads(view, scale, WeekLogMetrics.LABEL_WIDTH_DP * scale, null, numbers = false, modifier = GlanceModifier.padding(top = (4 * scale).dp))
        view.rows.forEachIndexed { index, row ->
            DotRow(row, view.rowDescriptions[index], scale, last = index == view.rows.lastIndex, full = false)
        }
    }
}

@Composable
private fun FullWeek(view: WeekLogView, rtl: Boolean, scale: Float, widthDp: Float) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val direction = LocalWidgetDirection.current
    val across = widthDp - 2 * WeekLogMetrics.horizontalPadding.value
    WidgetFrame(rtl, horizontal = WeekLogMetrics.horizontalPadding, vertical = 14.dp, opens = MainActivity.Opens.PRAYER_LOG, verticalAlignment = Alignment.Top) {
        ReadingRow(
            GlanceModifier.fillMaxWidth(),
            Alignment.Bottom,
            listOf(
                cell {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = direction.start) {
                        WidgetLine(view.title, WeekLogMetrics.FULL_TITLE_SP * scale, Tone.TEXT, bold = true)
                        WidgetLine(view.range, WeekLogMetrics.COUNT_SP * scale, Tone.MUTED)
                    }
                },
                cell { WidgetLine(view.prayed, WeekLogMetrics.FULL_TOTAL_SP * scale, Tone.ACCENT, bold = true) },
                cell { WidgetLine(view.outOf, WeekLogMetrics.COUNT_SP * scale, Tone.MUTED, modifier = direction.paddingStart(4.dp).then(GlanceModifier.padding(bottom = (4 * scale).dp))) },
            ),
        )
        WidgetDrawing(
            WidgetArt.progressLine(context, across, view.share, rtl, context.colorOf(style.accent), context.colorOf(style.divider), dot = false),
            across,
            3f,
            modifier = GlanceModifier.padding(top = (6 * scale).dp),
        )
        DayHeads(view, scale, WeekLogMetrics.FULL_LABEL_WIDTH_DP * scale, WeekLogMetrics.FULL_COUNT_WIDTH_DP * scale, numbers = true, modifier = GlanceModifier.padding(top = (8 * scale).dp))
        view.rows.forEachIndexed { index, row ->
            DotRow(row, view.rowDescriptions[index], scale, last = index == view.rows.lastIndex, full = true)
        }
        Legend(view, scale)
    }
}

/** Each day's letter (and number), today's on the top of its band. */
@Composable
private fun DayHeads(view: WeekLogView, scale: Float, labelWidth: Float, countWidth: Float?, numbers: Boolean, modifier: GlanceModifier) {
    val style = LocalWidgetStyle.current
    val heads = view.letters.mapIndexed { i, letter ->
        val today = i == view.letters.lastIndex
        cell {
            val band = if (today) GlanceModifier.background(ImageProvider(R.drawable.widget_band_top), colorFilter = tint(style.band)) else GlanceModifier
            Column(GlanceModifier.defaultWeight().then(band).padding(top = (3 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetLine(letter, WeekLogMetrics.LETTER_SP * scale, if (today) Tone.ON_SOFT else Tone.MUTED, bold = today, center = true)
                if (numbers) WidgetLine(view.numbers[i], WeekLogMetrics.SMALL_SP * scale, if (today) Tone.ON_SOFT else Tone.MUTED, bold = today, center = true)
            }
        }
    }
    ReadingRow(
        GlanceModifier.fillMaxWidth().then(modifier),
        Alignment.Bottom,
        listOf(cell { Box(GlanceModifier.width(labelWidth.dp)) {} }) + heads +
            (if (countWidth != null) listOf(cell { Box(GlanceModifier.width(countWidth.dp)) {} }) else emptyList()),
    )
}

@Composable
private fun ColumnScope.DotRow(row: WeekLogView.Row, description: String, scale: Float, last: Boolean, full: Boolean) {
    val labelWidth = (if (full) WeekLogMetrics.FULL_LABEL_WIDTH_DP else WeekLogMetrics.LABEL_WIDTH_DP) * scale
    val label = cell {
        WidgetLine(row.name, (if (full) WeekLogMetrics.FULL_NAME_SP else WeekLogMetrics.NAME_SP) * scale, Tone.TEXT, bold = true, modifier = GlanceModifier.width(labelWidth.dp))
    }
    val dot = (if (full) WeekLogMetrics.FULL_DOT_DP else WeekLogMetrics.DOT_DP) * scale
    val count = if (full) {
        listOf(cell {
            Box(GlanceModifier.width((WeekLogMetrics.FULL_COUNT_WIDTH_DP * scale).dp), contentAlignment = LocalWidgetDirection.current.endAlignment) {
                WidgetLine(row.count, WeekLogMetrics.SMALL_SP * scale, Tone.MUTED)
            }
        })
    } else {
        emptyList()
    }
    // TalkBack reads one line per Prayer instead of 7 unlabelled dots.
    ReadingRow(
        GlanceModifier.fillMaxWidth().defaultWeight().semantics { contentDescription = description },
        Alignment.CenterVertically,
        listOf(label) + row.cells.mapIndexed { i, state -> cell { Dot(state, dot.dp, today = i == row.cells.lastIndex, last = last) } } + count,
    )
}

/** Prayed is filled with the accent, not marked a thin muted ring, not yet due (or before first use) a tiny faint dot. */
@Composable
private fun RowScope.Dot(cell: WeekLogContent.Cell, size: Dp, today: Boolean, last: Boolean) {
    val style = LocalWidgetStyle.current
    val band = if (today) {
        GlanceModifier.background(ImageProvider(if (last) R.drawable.widget_band_bottom else R.drawable.widget_rect), colorFilter = tint(style.band))
    } else {
        GlanceModifier
    }
    Box(GlanceModifier.defaultWeight().fillMaxHeight().then(band), contentAlignment = Alignment.Center) {
        when (cell) {
            WeekLogContent.Cell.PRAYED -> Image(ImageProvider(R.drawable.widget_dot_prayed), null, GlanceModifier.size(size), colorFilter = tint(style.accent))
            WeekLogContent.Cell.NOT_MARKED -> Image(ImageProvider(R.drawable.widget_ring_thin), null, GlanceModifier.size(size), colorFilter = tint(style.ringMuted))
            // Days before the app was first used still get their column, so the week always reads as seven days.
            WeekLogContent.Cell.NOT_YET_DUE, WeekLogContent.Cell.BEFORE_FIRST_USE ->
                Image(ImageProvider(R.drawable.widget_dot_prayed), null, GlanceModifier.size(WeekLogMetrics.NOT_YET_DP.dp), colorFilter = tint(style.faint))
        }
    }
}

/** The 4×3's key: a filled dot «صلّيت», a ring «فاتت», a tiny dot «لم يحن». */
@Composable
private fun Legend(view: WeekLogView, scale: Float) {
    val style = LocalWidgetStyle.current
    val direction = LocalWidgetDirection.current
    val marks = listOf(
        Triple(R.drawable.widget_dot_prayed, style.accent, 9f),
        Triple(R.drawable.widget_ring_thin, style.ringMuted, 9f),
        Triple(R.drawable.widget_dot_prayed, style.faint, WeekLogMetrics.NOT_YET_DP),
    )
    Box(GlanceModifier.fillMaxWidth().padding(top = (6 * scale).dp), contentAlignment = Alignment.Center) {
        ReadingRow(
            verticalAlignment = Alignment.CenterVertically,
            children = marks.zip(view.legend).flatMapIndexed { i, (mark, label) ->
                listOf(
                    cell {
                        Image(ImageProvider(mark.first), null, direction.paddingStart(if (i == 0) 0.dp else 14.dp).then(GlanceModifier.size(mark.third.dp)), colorFilter = tint(mark.second))
                    },
                    cell { WidgetLine(label, WeekLogMetrics.SMALL_SP * scale, Tone.MUTED, modifier = direction.paddingStart(5.dp)) },
                )
            },
        )
    }
}

private fun cellLabelRes(cell: WeekLogContent.Cell) = when (cell) {
    WeekLogContent.Cell.PRAYED -> R.string.widget_state_prayed
    WeekLogContent.Cell.NOT_MARKED -> R.string.widget_state_not_marked
    WeekLogContent.Cell.NOT_YET_DUE, WeekLogContent.Cell.BEFORE_FIRST_USE -> R.string.widget_state_not_yet_due
}

/** The 4×3 entry in the widget picker; resizes down to 4×2. */
class WeekLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekLogWidget()
}
