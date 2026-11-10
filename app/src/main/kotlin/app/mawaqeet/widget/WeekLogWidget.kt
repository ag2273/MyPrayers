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
 * The Week Prayer Log Widget Variant (4×3, down to 4×2): a header (title and the week's total), then a row of seven
 * dots per Prayer, the last 7 Prayer Days oldest to newest in reading direction. Display only: the whole widget is one
 * tap target that opens the Prayer Log screen. A thin layer over [WeekLogContent].
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
    val total: String,
    val rows: List<Row>,
    /** Read out by TalkBack instead of the dots: e.g. "Fajr: Prayed, Not marked, …". */
    val rowDescriptions: List<String>,
) {
    data class Row(val name: String, val cells: List<WeekLogContent.Cell>)

    companion object {
        fun of(content: WeekLogContent, text: WidgetText): WeekLogView {
            val strings = text.strings
            val rows = content.rows.map { Row(strings.getString(it.nameRes), it.cells) }
            return WeekLogView(
                title = strings.getString(R.string.widget_week_log),
                total = strings.getString(R.string.widget_week_total, content.prayed, content.possible),
                rows = rows,
                rowDescriptions = rows.map { row ->
                    row.name + ": " + row.cells.filter { it != WeekLogContent.Cell.BEFORE_FIRST_USE }
                        .joinToString(", ") { strings.getString(cellLabelRes(it)) }
                },
            )
        }
    }
}

/** Sizes of the week grid, from the Widgets board; all shrink together to fit down to 4×2. */
internal object WeekLogMetrics {
    const val MIN_WIDTH_DP = 250f
    const val MIN_HEIGHT_DP = 180f
    val horizontalPadding = 18.dp
    val verticalPadding = 14.dp
    const val HEADER_SP = 12f
    const val NAME_SP = 13f
    const val DOT_DP = 20f
    /** Between the header and the rows and between rows, at full size. */
    const val ROW_GAP_DP = 6f
    /** The Prayer-name column; the seven day columns share the rest. */
    const val LABEL_WIDTH_DP = 64f

    fun heightScale(header: String, name: String, heightDp: Float, step: Float, fontScale: Float = 1f): Float {
        val room = heightDp - 2 * verticalPadding.value - 1
        return step * fitScale(floor = 0.4f) {
            val s = step * it
            val row = maxOf(DOT_DP * s, lineHeightDp(name, NAME_SP * s, fontScale, bold = true).toFloat())
            lineHeightDp(header, HEADER_SP * s, fontScale, bold = true) + 5 * (ROW_GAP_DP * s + row) <= room
        }
    }
}

@Composable
private fun WeekLogLayout(view: WeekLogView, rtl: Boolean) {
    val style = LocalWidgetStyle.current
    val height = if (style.responsive) LocalSize.current.height.value else WeekLogMetrics.MIN_HEIGHT_DP
    val step = textStep(WeekLogMetrics.MIN_WIDTH_DP, WeekLogMetrics.MIN_HEIGHT_DP)
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val scale = WeekLogMetrics.heightScale(view.title, view.rows.first().name, height, step, fontScale)
    WidgetFrame(rtl, horizontal = WeekLogMetrics.horizontalPadding, vertical = WeekLogMetrics.verticalPadding, opens = MainActivity.Opens.PRAYER_LOG) {
        WidgetHeader(view.title, view.total, WeekLogMetrics.HEADER_SP * scale)
        view.rows.forEachIndexed { index, row -> DotRow(row, view.rowDescriptions[index], scale) }
    }
}

@Composable
private fun ColumnScope.DotRow(row: WeekLogView.Row, description: String, scale: Float) {
    // TalkBack reads one line per Prayer instead of 7 unlabelled dots.
    val label = cell {
        WidgetLine(row.name, WeekLogMetrics.NAME_SP * scale, Tone.TEXT, bold = true, modifier = GlanceModifier.width((WeekLogMetrics.LABEL_WIDTH_DP * scale).dp))
    }
    ReadingRow(
        GlanceModifier.fillMaxWidth().defaultWeight().padding(top = (WeekLogMetrics.ROW_GAP_DP * scale).dp)
            .semantics { contentDescription = description },
        Alignment.CenterVertically,
        listOf(label) + row.cells.map { state -> cell { Dot(state, (WeekLogMetrics.DOT_DP * scale).dp) } },
    )
}

/** Prayed is filled with the accent, not marked is a muted ring, not yet due a faint ring; before first use, nothing. */
@Composable
private fun RowScope.Dot(cell: WeekLogContent.Cell, size: Dp) {
    val style = LocalWidgetStyle.current
    Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.Center) {
        val (res, color) = when (cell) {
            WeekLogContent.Cell.PRAYED -> R.drawable.widget_dot_prayed to style.accent
            WeekLogContent.Cell.NOT_MARKED -> R.drawable.widget_ring to style.muted
            WeekLogContent.Cell.NOT_YET_DUE -> R.drawable.widget_ring to style.faint
            WeekLogContent.Cell.BEFORE_FIRST_USE -> null to 0
        }
        if (res != null) Image(ImageProvider(res), contentDescription = null, modifier = GlanceModifier.size(size), colorFilter = tint(color))
    }
}

private fun cellLabelRes(cell: WeekLogContent.Cell) = when (cell) {
    WeekLogContent.Cell.PRAYED -> R.string.widget_state_prayed
    WeekLogContent.Cell.NOT_MARKED -> R.string.widget_state_not_marked
    WeekLogContent.Cell.NOT_YET_DUE, WeekLogContent.Cell.BEFORE_FIRST_USE -> R.string.widget_state_not_yet_due
}

/** The 4×3 entry in the widget picker. */
class WeekLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekLogWidget()
}
