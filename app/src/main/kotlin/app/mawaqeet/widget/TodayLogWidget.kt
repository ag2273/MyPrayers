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
import androidx.glance.layout.size
import app.mawaqeet.R
import app.mawaqeet.core.LogState

/**
 * The Today with Prayer Log Widget Variant (4×2): a header (title and how many are marked, e.g. «٢ من ٥»), then five
 * columns (name, time, Prayer Log dot) with the next Prayer filled with the accent. Display only: the whole widget is
 * one tap target that opens today's Prayer Times; nothing on it marks a Prayer. A thin layer over [TodayLogContent].
 */
class TodayLogWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text ->
            val day = core.displayDay()
            TodayLogView.of(TodayLogContent.of(day, core.prayerDay(day.date), text.formatter), text)
        },
        layout = { view, rtl -> TodayLogLayout(view, rtl) },
    )
}

private data class TodayLogView(val title: String, val count: String, val rows: List<Row>, val allNames: List<String>) {
    data class Row(val name: String, val time: String, val state: LogState, val stateLabel: String, val isNext: Boolean)

    companion object {
        fun of(content: TodayLogContent, text: WidgetText) = TodayLogView(
            title = text.strings.getString(R.string.widget_today_log),
            count = text.strings.getString(
                R.string.widget_count,
                text.formatter.number(content.prayedCount),
                text.formatter.number(content.rows.size),
            ),
            rows = content.rows.map {
                Row(text.strings.getString(it.nameRes), it.time, it.state, text.strings.getString(stateLabelRes(it.state)), it.isNext)
            },
            allNames = TodayTimesMetrics.NAMES.map { text.strings.getString(it) },
        )
    }
}

@Composable
private fun TodayLogLayout(view: TodayLogView, rtl: Boolean) {
    val sizes = CellSizes.of(view.title, view.allNames, view.rows.map { it.time }, dots = true)
    WidgetFrame(rtl, horizontal = TodayTimesMetrics.horizontalPadding, vertical = TodayTimesMetrics.verticalPadding) {
        WidgetHeader(view.title, view.count, sizes.headerSp)
        PrayerColumns(
            sizes,
            view.rows.map { row -> PrayerColumn(row.name, row.time, row.isNext) { size -> Mark(row, size) } },
        )
    }
}

/**
 * The Prayer Log dot: filled with the accent once prayed; otherwise a ring, white on the next Prayer's fill, muted
 * when its time has come and it isn't marked, and faint before its time.
 */
@Composable
private fun Mark(row: TodayLogView.Row, sizeDp: Float) {
    val style = LocalWidgetStyle.current
    val (res, color) = when {
        row.state == LogState.PRAYED -> R.drawable.widget_dot_prayed to (if (row.isNext) style.onAccent else style.accent)
        row.isNext -> R.drawable.widget_ring to style.onAccent
        row.state == LogState.NOT_MARKED -> R.drawable.widget_ring to style.muted
        else -> R.drawable.widget_ring to style.faint
    }
    Image(ImageProvider(res), row.stateLabel, GlanceModifier.size(sizeDp.dp), colorFilter = tint(color))
}

internal fun stateLabelRes(state: LogState) = when (state) {
    LogState.PRAYED -> R.string.widget_state_prayed
    LogState.NOT_MARKED -> R.string.widget_state_not_marked
    LogState.NOT_YET_DUE -> R.string.widget_state_not_yet_due
}

/** The 4×2 entry in the widget picker. */
class TodayLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayLogWidget()
}
