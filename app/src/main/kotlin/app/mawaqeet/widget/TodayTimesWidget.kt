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
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import app.mawaqeet.R

/**
 * The Today's Prayer Times Widget Variant (4×2): the date line, then six columns (five Prayers and Sunrise) with the
 * next Prayer highlighted, and no countdown. A thin layer over [TodayTimesContent].
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
 * Sizes of the 4×2 layout, shared with the test that checks no name or time is clipped at the smallest width. Names
 * and times shrink to fit their column (English "Jumu'ah" is the widest), so nothing is clipped at 4×2 widths.
 */
internal object TodayTimesMetrics {
    const val MIN_WIDTH_DP = 250
    const val MIN_HEIGHT_DP = 110
    val horizontalPadding = 8.dp
    val cellGap = 2.dp
    val cellPadding = 1.dp

    const val NAME_SP = 11f
    const val TIME_SP = 13f
    /** Text never shrinks below this to fit; past it a launcher font scale may still clip. */
    const val MIN_SP = 8f

    /** Every name a column can show, so the name size doesn't change on Fridays. */
    val NAMES = listOf(
        R.string.prayer_fajr, R.string.sunrise, R.string.prayer_dhuhr, R.string.prayer_jumuah,
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

    /** The width each of the six columns has for its text at a widget width of [widthDp]. */
    fun cellTextWidthDp(widthDp: Float): Float =
        (widthDp - 2 * horizontalPadding.value) / 6 - cellGap.value - 2 * cellPadding.value
}

private data class TodayTimesView(val date: WidgetDate, val columns: List<Cell>, val allNames: List<String>) {
    data class Cell(val name: String, val time: String, val isSunrise: Boolean, val isNext: Boolean)

    companion object {
        fun of(content: TodayTimesContent, text: WidgetText) = TodayTimesView(
            date = content.date,
            columns = content.columns.map { Cell(text.strings.getString(it.nameRes), it.time, it.isSunrise, it.isNext) },
            allNames = TodayTimesMetrics.NAMES.map { text.strings.getString(it) },
        )
    }
}

@Composable
private fun TodayTimesLayout(view: TodayTimesView, rtl: Boolean) {
    val style = LocalWidgetStyle.current
    // On Android 8–11 one layout at the default size: the smallest width, whatever the launcher gives.
    val width = if (style.responsive) LocalSize.current.width.value else TodayTimesMetrics.MIN_WIDTH_DP.toFloat()
    val step = textStep(TodayTimesMetrics.MIN_WIDTH_DP.toFloat(), TodayTimesMetrics.MIN_HEIGHT_DP.toFloat())
    val room = TodayTimesMetrics.cellTextWidthDp(width)
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val nameSp = TodayTimesMetrics.fitSp(view.allNames, room, TodayTimesMetrics.NAME_SP * step, bold = false, fontScale)
    val timeSp = TodayTimesMetrics.fitSp(view.columns.map { it.time }, room, TodayTimesMetrics.TIME_SP * step, bold = true, fontScale)
    WidgetFrame(rtl, horizontal = TodayTimesMetrics.horizontalPadding, vertical = 10.dp) {
        DateLine(view.date, sp = 12f * step)
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(top = 6.dp)) {
            // No spacers between cells: Glance keeps at most 10 children in a Row, so the gap is each cell's margin.
            LocalWidgetDirection.current.inReadingOrder(view.columns).forEach { cell -> TimeCell(cell, nameSp, timeSp) }
        }
    }
}

/** One column; the outer box makes the gap to its neighbours, the inner column carries the highlight. */
@Composable
private fun RowScope.TimeCell(cell: TodayTimesView.Cell, nameSp: Float, timeSp: Float) {
    val style = LocalWidgetStyle.current
    val tone = when {
        cell.isNext -> Tone.ON_ACCENT
        cell.isSunrise -> Tone.MUTED
        else -> Tone.TEXT
    }
    val inner = GlanceModifier.fillMaxSize().padding(horizontal = TodayTimesMetrics.cellPadding)
    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = TodayTimesMetrics.cellGap / 2)) {
        Column(
            modifier = if (cell.isNext) {
                inner.background(ImageProvider(R.drawable.widget_cell_next), colorFilter = tint(style.accent))
            } else {
                inner
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetLine(cell.name, nameSp, tone, center = true)
            WidgetLine(cell.time, timeSp, tone, bold = !cell.isSunrise, center = true)
        }
    }
}

/** The 4×2 entry in the widget picker. */
class TodayTimesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTimesWidget()
}
