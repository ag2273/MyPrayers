package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import app.mawaqeet.R
import app.mawaqeet.core.LogState

/**
 * The Today with Prayer Log Widget Variant (4×3): the date line, five rows (name, time, Prayer Log mark) with the
 * next Prayer highlighted, and Sunrise as a footer. Display only: the whole widget is one tap target that opens
 * today's Prayer Times; nothing on it marks a Prayer. A thin layer over [TodayLogContent].
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
    val step = textStep(MIN_WIDTH_DP, MIN_HEIGHT_DP)
    WidgetFrame(rtl, horizontal = 12.dp, vertical = 10.dp) {
        DateLine(view.date, sp = 12f * step)
        view.rows.forEach { LogRow(it, step) }
        WidgetLine(view.sunrise, 11f * step, Tone.MUTED, modifier = GlanceModifier.padding(horizontal = 8.dp))
    }
}

private const val MIN_WIDTH_DP = 250f
private const val MIN_HEIGHT_DP = 180f

@Composable
private fun ColumnScope.LogRow(row: TodayLogView.Row, step: Float) {
    val style = LocalWidgetStyle.current
    val base = GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 8.dp)
    val tone = if (row.state == LogState.NOT_YET_DUE && !row.isNext) Tone.MUTED else Tone.TEXT
    val direction = LocalWidgetDirection.current
    ReadingRow(
        modifier = if (row.isNext) {
            base.background(ImageProvider(R.drawable.widget_row_next), colorFilter = tint(style.accentSoft))
        } else {
            base
        },
        verticalAlignment = Alignment.CenterVertically,
        children = listOf(
            { WidgetLine(row.name, 14f * step, tone, bold = row.isNext, modifier = GlanceModifier.defaultWeight()) },
            { WidgetLine(row.time, 13f * step, tone) },
            {
                Box(direction.paddingStart(10.dp), contentAlignment = Alignment.Center) {
                    Mark(row.state, row.stateLabel, (20 * step).dp)
                }
            },
        ),
    )
}

/** The Prayer Log mark: a filled circle with a check (prayed), a solid ring (due), or a dashed ring (not yet due). */
@Composable
private fun Mark(state: LogState, label: String, size: Dp) {
    val style = LocalWidgetStyle.current
    val modifier = GlanceModifier.size(size)
    when (state) {
        LogState.PRAYED -> Box(contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.widget_mark_prayed), label, modifier, colorFilter = tint(style.accent))
            Image(ImageProvider(R.drawable.widget_mark_check), null, modifier, colorFilter = tint(style.onAccent))
        }
        LogState.NOT_MARKED -> Image(ImageProvider(R.drawable.widget_mark_due), label, modifier, colorFilter = tint(style.accent))
        LogState.NOT_YET_DUE -> Image(ImageProvider(R.drawable.widget_mark_upcoming), label, modifier, colorFilter = tint(style.muted))
    }
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
