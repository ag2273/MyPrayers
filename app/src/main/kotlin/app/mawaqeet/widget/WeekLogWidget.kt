package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
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
 * The Week Prayer Log Widget Variant (4×3): the last 7 Prayer Days × 5 Prayers as dots, oldest to newest in reading
 * direction, today's column highlighted, and the week's total. Display only: the whole widget is one tap target that
 * opens the Prayer Log screen. A thin layer over [WeekLogContent].
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
    val weekdays: List<String>,
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
                total = content.total,
                weekdays = content.weekdays,
                rows = rows,
                rowDescriptions = rows.map { row ->
                    row.name + ": " + row.cells.filter { it != WeekLogContent.Cell.BEFORE_FIRST_USE }
                        .joinToString(", ") { strings.getString(cellLabelRes(it)) }
                },
            )
        }
    }
}

/** Width of the Prayer-name column; the seven day columns share the rest. */
private val LABEL_WIDTH = 52.dp

@Composable
private fun WeekLogLayout(view: WeekLogView, rtl: Boolean) {
    val step = textStep(MIN_WIDTH_DP, MIN_HEIGHT_DP)
    WidgetFrame(rtl, horizontal = 12.dp, vertical = 10.dp, opens = MainActivity.Opens.PRAYER_LOG) {
        Row(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            WidgetLine(view.title, 13f * step, Tone.TEXT, bold = true, modifier = GlanceModifier.defaultWeight())
            WidgetLine(view.total, 13f * step, Tone.MUTED)
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(LABEL_WIDTH * step)) {}
            view.weekdays.forEachIndexed { index, letter ->
                val today = index == view.weekdays.lastIndex
                WidgetLine(
                    letter,
                    11f * step,
                    if (today) Tone.ACCENT else Tone.MUTED,
                    bold = today,
                    center = true,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        view.rows.forEachIndexed { index, row -> DotRow(row, view.rowDescriptions[index], step) }
    }
}

private const val MIN_WIDTH_DP = 250f
private const val MIN_HEIGHT_DP = 180f

@Composable
private fun ColumnScope.DotRow(row: WeekLogView.Row, description: String, step: Float) {
    // TalkBack reads one line per Prayer instead of 7 unlabelled dots.
    Row(
        GlanceModifier.fillMaxWidth().defaultWeight().semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetLine(row.name, 11f * step, Tone.MUTED, modifier = GlanceModifier.width(LABEL_WIDTH * step))
        row.cells.forEach { Dot(it, (14 * step).dp) }
    }
}

@Composable
private fun RowScope.Dot(cell: WeekLogContent.Cell, size: androidx.compose.ui.unit.Dp) {
    val style = LocalWidgetStyle.current
    Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.Center) {
        val res = dotRes(cell)
        val color = when (cell) {
            WeekLogContent.Cell.PRAYED -> style.accent
            WeekLogContent.Cell.NOT_MARKED -> style.muted
            else -> style.faint
        }
        if (res != null) Image(ImageProvider(res), contentDescription = null, modifier = GlanceModifier.size(size), colorFilter = tint(color))
    }
}

private fun dotRes(cell: WeekLogContent.Cell) = when (cell) {
    WeekLogContent.Cell.PRAYED -> R.drawable.widget_dot_prayed
    WeekLogContent.Cell.NOT_MARKED -> R.drawable.widget_dot_missed
    WeekLogContent.Cell.NOT_YET_DUE -> R.drawable.widget_dot_upcoming
    WeekLogContent.Cell.BEFORE_FIRST_USE -> null
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
