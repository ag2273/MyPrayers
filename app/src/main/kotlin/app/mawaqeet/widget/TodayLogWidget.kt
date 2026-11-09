package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.mawaqeet.R
import app.mawaqeet.core.LogState

/**
 * The Today with Prayer Log Widget Variant (4×3): the date line, five rows (name, time, Prayer Log mark) with the
 * next Prayer highlighted, and Sunrise as a footer. Display only: the whole widget is one tap target that opens
 * today's Prayer Times; nothing on it marks a Prayer. A thin layer over [TodayLogContent].
 */
class TodayLogWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text ->
            val day = core.displayDay()
            TodayLogView.of(TodayLogContent.of(day, core.prayerDay(day.date), text.formatter), text)
        },
        layout = { view, rtl -> TodayLogLayout(view, rtl) },
    )
}

private data class TodayLogView(val date: WidgetDate, val rows: List<Row>, val sunrise: String) {
    data class Row(val name: String, val time: String, val state: LogState, val stateLabel: String, val isNext: Boolean)

    companion object {
        fun of(content: TodayLogContent, text: WidgetText) = TodayLogView(
            date = content.date,
            rows = content.rows.map {
                Row(text.strings.getString(it.nameRes), it.time, it.state, text.strings.getString(stateLabelRes(it.state)), it.isNext)
            },
            sunrise = "${text.strings.getString(R.string.sunrise)} ${content.sunrise}",
        )
    }
}

@Composable
private fun TodayLogLayout(view: TodayLogView, rtl: Boolean) {
    WidgetFrame(rtl, horizontal = 12.dp, vertical = 10.dp) {
        DateLine(view.date)
        view.rows.forEach { LogRow(it) }
        Text(
            view.sunrise,
            style = TextStyle(color = WIDGET_MUTED, fontSize = 11.sp),
            maxLines = 1,
            modifier = GlanceModifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ColumnScope.LogRow(row: TodayLogView.Row) {
    val base = GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 8.dp)
    val color = if (row.state == LogState.NOT_YET_DUE && !row.isNext) WIDGET_MUTED else WIDGET_TEXT
    Row(
        modifier = if (row.isNext) base.background(ImageProvider(R.drawable.widget_row_next)) else base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.name,
            style = TextStyle(color = color, fontSize = 14.sp, fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(row.time, style = TextStyle(color = color, fontSize = 13.sp), maxLines = 1)
        Box(GlanceModifier.padding(start = 10.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(markRes(row.state)), contentDescription = row.stateLabel, modifier = GlanceModifier.size(20.dp))
        }
    }
}

private fun markRes(state: LogState) = when (state) {
    LogState.PRAYED -> R.drawable.widget_mark_prayed
    LogState.NOT_MARKED -> R.drawable.widget_mark_due
    LogState.NOT_YET_DUE -> R.drawable.widget_mark_upcoming
}

internal fun stateLabelRes(state: LogState) = when (state) {
    LogState.PRAYED -> R.string.widget_state_prayed
    LogState.NOT_MARKED -> R.string.widget_state_not_marked
    LogState.NOT_YET_DUE -> R.string.widget_state_not_yet_due
}

/** The 4×3 entry in the widget picker. */
class TodayLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayLogWidget()
}
