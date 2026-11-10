package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.R
import app.mawaqeet.core.LogState
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * The Today with Prayer Log Widget Variant (widget design board, 2026-11-10). At 4×1 five columns (name, mark, time);
 * at 4×2 a header («سجل اليوم», how many are marked and five thin segments), the columns (name, time, mark) and a hint.
 * The next Prayer sits on a soft fill. A Prayer whose time has come is marked, or unmarked, by tapping its column,
 * through the prayer core's rules, as on Home; the rest of the widget opens today's Prayer Times. A thin layer over
 * [TodayLogContent].
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

private data class TodayLogView(
    val title: String,
    val allDone: Boolean,
    val count: String,
    val prayed: Int,
    val hint: String,
    val date: LocalDate,
    val rows: List<Row>,
    val allNames: List<String>,
) {
    data class Row(val prayer: Prayer, val name: String, val time: String, val state: LogState, val description: String, val isNext: Boolean)

    companion object {
        fun of(content: TodayLogContent, text: WidgetText): TodayLogView {
            val s = text.strings
            val allDone = content.prayedCount == content.rows.size
            return TodayLogView(
                title = s.getString(if (allDone) R.string.widget_today_log_done else R.string.widget_today_log_header),
                allDone = allDone,
                count = s.getString(R.string.widget_count, text.formatter.number(content.prayedCount), text.formatter.number(content.rows.size)),
                prayed = content.prayedCount,
                hint = s.getString(if (allDone) R.string.widget_today_log_done_hint else R.string.widget_today_log_hint),
                date = content.logDate,
                rows = content.rows.map {
                    val name = s.getString(it.nameRes)
                    val state = s.getString(stateLabelRes(it.state))
                    Row(
                        prayer = it.prayer,
                        name = name,
                        time = it.time,
                        state = it.state,
                        description = when (it.state) {
                            LogState.PRAYED -> s.getString(R.string.widget_unmark_hint, name, state)
                            LogState.NOT_MARKED -> s.getString(R.string.widget_mark_hint, name, state)
                            LogState.NOT_YET_DUE -> "$name: $state"
                        },
                        isNext = it.isNext,
                    )
                },
                allNames = TodayTimesMetrics.NAMES.map { s.getString(it) },
            )
        }
    }
}

/** The 4×2's hint under the columns, and the 4×1's marks, which sit between name and time. */
internal object TodayLogMetrics {
    const val HINT_SP = 11f
    const val HINT_GAP_DP = 4f
    const val SEGMENT_WIDTH_DP = 14f
    const val SEGMENT_HEIGHT_DP = 4f
}

@Composable
private fun TodayLogLayout(view: TodayLogView, rtl: Boolean) {
    val style = LocalWidgetStyle.current
    val size = LocalSize.current
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val height = if (style.responsive) size.height.value else TodayTimesMetrics.MIN_HEIGHT_DP.toFloat()
    val short = height < TodayTimesMetrics.SHORT_BELOW_DP
    val sizes = CellSizes.of(view.title, view.allNames, view.rows.map { it.time }, dots = true, markFirst = short)
    val tall = sizes.showHeader
    val left = height - 2 * TodayTimesMetrics.verticalPaddingDp(height) - 1 -
        lineHeightDp(view.title, sizes.headerSp, fontScale, bold = true) - TodayTimesMetrics.HEADER_GAP_DP * sizes.scale -
        TodayTimesMetrics.columnsHeight(view.allNames.first(), view.rows.first().time, sizes.scale, dots = true, fontScale)
    val showHint = tall && left >= lineHeightDp(view.hint, TodayLogMetrics.HINT_SP, fontScale) + TodayLogMetrics.HINT_GAP_DP
    WidgetFrame(rtl, horizontal = TodayTimesMetrics.horizontalPadding, vertical = TodayTimesMetrics.verticalPaddingDp(height).dp) {
        if (tall) LogHeader(view, sizes.headerSp)
        PrayerColumns(
            sizes.copy(showHeader = tall),
            view.rows.map { row ->
                val tappable = row.state != LogState.NOT_YET_DUE
                PrayerColumn(
                    name = row.name,
                    time = row.time,
                    isNext = row.isNext,
                    dot = { mark -> Mark(row, mark) },
                    isPast = short && row.state != LogState.NOT_YET_DUE,
                    markFirst = short,
                    onClick = if (tappable) {
                        actionRunCallback<ToggleMarkAction>(
                            actionParametersOf(ToggleMarkAction.PRAYER to row.prayer.name, ToggleMarkAction.DATE to view.date.toString()),
                        )
                    } else {
                        null
                    },
                    description = row.description,
                )
            },
        )
        if (showHint) {
            Box(GlanceModifier.fillMaxWidth().padding(top = TodayLogMetrics.HINT_GAP_DP.dp), contentAlignment = Alignment.Center) {
                WidgetLine(view.hint, TodayLogMetrics.HINT_SP, Tone.MUTED, center = true)
            }
        }
    }
}

/** «سجل اليوم» at the start; at the end how many are marked, and five thin segments, one filled per Prayer marked. */
@Composable
private fun LogHeader(view: TodayLogView, sp: Float) {
    val style = LocalWidgetStyle.current
    val direction = LocalWidgetDirection.current
    val segments = (0 until 5).map { i ->
        cell {
            Image(
                ImageProvider(R.drawable.widget_pill),
                contentDescription = null,
                modifier = direction.paddingStart(if (i == 0) 8.dp else 3.dp).then(
                    GlanceModifier.width(TodayLogMetrics.SEGMENT_WIDTH_DP.dp).height(TodayLogMetrics.SEGMENT_HEIGHT_DP.dp),
                ),
                colorFilter = tint(if (i < view.prayed) style.accent else style.divider),
            )
        }
    }
    ReadingRow(
        GlanceModifier.fillMaxWidth(),
        Alignment.CenterVertically,
        listOf(
            cell { WidgetLine(view.title, sp * 1.08f, if (view.allDone) Tone.ON_SOFT else Tone.TEXT, bold = true, modifier = GlanceModifier.defaultWeight()) },
            cell { WidgetLine(view.count, sp, Tone.MUTED) },
        ) + segments,
    )
}

/**
 * The Prayer Log mark: prayed is a filled circle with a tick; a Prayer whose time has come and isn't marked a ring in
 * the accent; one not yet due a dashed ring.
 */
@Composable
private fun Mark(row: TodayLogView.Row, sizeDp: Float) {
    val style = LocalWidgetStyle.current
    when (row.state) {
        LogState.PRAYED -> Box(
            GlanceModifier.size(sizeDp.dp).background(ImageProvider(R.drawable.widget_dot_prayed), colorFilter = tint(style.accent)),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.widget_check), contentDescription = null, modifier = GlanceModifier.size((sizeDp * 0.5f).dp), colorFilter = tint(style.onAccent))
        }
        LogState.NOT_MARKED -> Image(ImageProvider(R.drawable.widget_ring), null, GlanceModifier.size(sizeDp.dp), colorFilter = tint(style.accent))
        LogState.NOT_YET_DUE -> Image(
            ImageProvider(R.drawable.widget_ring_dashed),
            null,
            GlanceModifier.size(sizeDp.dp),
            colorFilter = tint(if (row.isNext) style.ringMuted else style.faint),
        )
    }
}

internal fun stateLabelRes(state: LogState) = when (state) {
    LogState.PRAYED -> R.string.widget_state_prayed
    LogState.NOT_MARKED -> R.string.widget_state_not_marked
    LogState.NOT_YET_DUE -> R.string.widget_state_not_yet_due
}

/**
 * A tap on a Prayer's column: marks it prayed, or removes the mark, through the prayer core (so a Prayer not yet due,
 * or a day outside the edit window, is left alone), then re-renders the widgets.
 */
class ToggleMarkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prayer = parameters[PRAYER]?.let { name -> Prayer.entries.firstOrNull { it.name == name } } ?: return
        val date = parameters[DATE]?.let(LocalDate::parse) ?: return
        val app = context.applicationContext as MawaqeetApplication
        withContext(Dispatchers.IO) {
            val core = PrayerCore(app.settings.settings.first(), Clock.systemUTC(), app.prayerLog)
            if (core.prayerDay(date).stateOf(prayer) == LogState.PRAYED) core.unmark(date, prayer) else core.mark(date, prayer)
        }
        Widgets.refreshAll(context)
    }

    companion object {
        val PRAYER = ActionParameters.Key<String>("prayer")
        val DATE = ActionParameters.Key<String>("date")
    }
}

/** The 4×1 entry in the widget picker; resizes up to 4×2. */
class TodayLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayLogWidget()
}
