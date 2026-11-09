package app.mawaqeet.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import app.mawaqeet.R

/**
 * The Date strip Widget Variant (4×1): the Gregorian date, then the Hijri date (which shortens first when space is
 * tight), with the next Prayer and its time on a second line. A thin layer over [DateStripContent].
 */
class DateStripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> DateStripView.of(DateStripContent.of(core.displayDay(), text.formatter), text) },
        layout = { view, rtl -> DateStripLayout(view, rtl) },
    )
}

private data class DateStripView(override val date: WidgetDate, override val next: String) : DateStripText {

    companion object {
        fun of(content: DateStripContent, text: WidgetText) = DateStripView(
            date = content.date,
            next = text.strings.getString(
                R.string.widget_next_line,
                text.strings.getString(content.nextNameRes),
                content.nextTime,
            ),
        )
    }
}

/** Sizes of the 4×1 layout, shared with its render test. */
internal object DateStripMetrics {
    val horizontalPadding = 16.dp
    /** Little vertical padding at the smallest heights, where every dp goes to the two lines. */
    fun verticalPaddingDp(heightDp: Float) = if (heightDp < 56f) 2f else 6f
    /** The gap between the two lines. */
    const val LINE_GAP_DP = 2f
    const val MIN_WIDTH_DP = 250f
    const val MIN_HEIGHT_DP = 40f
    const val MAX_SP = 13f
    const val MIN_SP = 9f

    /**
     * The largest text size from [MAX_SP] down, in half-sp steps, at which both lines fit [heightDp] (the whole
     * widget), measured with the system font as the launcher lays them out: Arabic lines are taller than Latin ones,
     * and at the smallest 4×1 height they don't fit at full size.
     */
    fun fitSp(view: DateStripText, heightDp: Float, fontScale: Float = 1f, maxSp: Float = MAX_SP): Float {
        val room = heightDp - 2 * verticalPaddingDp(heightDp) - LINE_GAP_DP
        var sp = maxSp
        while (sp > MIN_SP) {
            val first = maxOf(lineHeightDp(view.date.gregorian, sp, fontScale, bold = true), lineHeightDp(view.date.hijri, sp, fontScale))
            if (first + lineHeightDp(view.next, sp, fontScale, bold = true) <= room) break
            sp -= 0.5f
        }
        return sp
    }
}

/** The two lines' text, for measuring. */
internal interface DateStripText {
    val date: WidgetDate
    val next: String
}

@Composable
private fun DateStripLayout(view: DateStripView, rtl: Boolean) {
    val height = LocalSize.current.height.value
    val step = textStep(DateStripMetrics.MIN_WIDTH_DP, DateStripMetrics.MIN_HEIGHT_DP)
    // Both lines shrink to fit the height: at the smallest 4×1 size Arabic doesn't fit at full size.
    val sp = DateStripMetrics.fitSp(view, height, LocalContext.current.resources.configuration.fontScale, DateStripMetrics.MAX_SP * step)
    WidgetFrame(rtl, horizontal = DateStripMetrics.horizontalPadding, vertical = DateStripMetrics.verticalPaddingDp(height).dp) {
        DateLine(view.date, sp, boldGregorian = true)
        WidgetLine(view.next, sp, Tone.ACCENT, bold = true, modifier = GlanceModifier.fillMaxWidth().padding(top = DateStripMetrics.LINE_GAP_DP.dp))
    }
}

/** The 4×1 entry in the widget picker. */
class DateStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateStripWidget()
}
