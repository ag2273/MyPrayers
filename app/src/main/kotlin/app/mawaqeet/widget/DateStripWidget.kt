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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import app.mawaqeet.R

/**
 * The Date strip Widget Variant (4×1): the Gregorian date over the Hijri date (which shortens first when space is
 * tight) at the start, and «القادمة: العصر ١٤:٤٤» at the end. A thin layer over [DateStripContent].
 */
class DateStripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> DateStripView.of(DateStripContent.of(core.displayDay(), text.formatter), text) },
        layout = { view, rtl -> DateStripLayout(view, rtl) },
    )
}

private data class DateStripView(override val date: WidgetDate, override val nextLabel: String, override val next: String) : DateStripText {

    companion object {
        fun of(content: DateStripContent, text: WidgetText) = DateStripView(
            date = content.date,
            nextLabel = text.strings.getString(R.string.widget_next_label),
            next = "${text.strings.getString(content.nextNameRes)} ${content.nextTime}",
        )
    }
}

/** Sizes of the 4×1 layout, from the Widgets board, shared with its render test. */
internal object DateStripMetrics {
    val horizontalPadding = 18.dp
    /** Little vertical padding at the smallest heights, where every dp goes to the two lines. */
    fun verticalPaddingDp(heightDp: Float) = if (heightDp < 56f) 2f else 8f
    const val MIN_WIDTH_DP = 250f
    const val MIN_HEIGHT_DP = 40f
    const val GREGORIAN_SP = 17f
    const val HIJRI_SP = 12f
    const val NEXT_SP = 14f
    /** Between the dates and the next Prayer, and between "القادمة:" and its Prayer. */
    const val GAP_DP = 12f
    const val WORD_GAP_DP = 4f

    /**
     * The largest scale from 1 down at which the two dates fit [heightDp] (the whole widget), measured with the system
     * font as the launcher lays them out (Arabic lines are taller than Latin ones), and the Gregorian date and the next
     * Prayer fit [widthDp] side by side.
     */
    fun scale(view: DateStripText, widthDp: Float, heightDp: Float, fontScale: Float = 1f, step: Float = 1f): Float {
        val room = heightDp - 2 * verticalPaddingDp(heightDp) - 1
        val across = widthDp - 2 * horizontalPadding.value
        return step * fitScale(floor = 0.5f) {
            val s = step * it
            lineHeightDp(view.date.gregorian, GREGORIAN_SP * s, fontScale, bold = true) +
                lineHeightDp(view.date.hijri, HIJRI_SP * s, fontScale, bold = true) <= room &&
                textWidthDp(view.date.gregorian, GREGORIAN_SP * s, fontScale, bold = true) + GAP_DP +
                textWidthDp(view.nextLabel, NEXT_SP * s, fontScale, bold = true) + WORD_GAP_DP +
                textWidthDp(view.next, NEXT_SP * s, fontScale, bold = true) <= across
        }
    }
}

/** The texts, for measuring. */
internal interface DateStripText {
    val date: WidgetDate
    val nextLabel: String
    val next: String
}

@Composable
private fun DateStripLayout(view: DateStripView, rtl: Boolean) {
    val size = LocalSize.current
    val style = LocalWidgetStyle.current
    val width = if (style.responsive) size.width.value else DateStripMetrics.MIN_WIDTH_DP
    val height = if (style.responsive) size.height.value else DateStripMetrics.MIN_HEIGHT_DP
    val step = textStep(DateStripMetrics.MIN_WIDTH_DP, DateStripMetrics.MIN_HEIGHT_DP)
    val scale = DateStripMetrics.scale(view, width, height, LocalContext.current.resources.configuration.fontScale, step)
    val direction = LocalWidgetDirection.current
    WidgetFrame(rtl, horizontal = DateStripMetrics.horizontalPadding, vertical = DateStripMetrics.verticalPaddingDp(height).dp) {
        ReadingRow(
            GlanceModifier.fillMaxWidth(),
            Alignment.CenterVertically,
            listOf(
                cell {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = direction.start) {
                        WidgetLine(view.date.gregorian, DateStripMetrics.GREGORIAN_SP * scale, Tone.TEXT, bold = true)
                        WidgetLine(view.date.hijri, DateStripMetrics.HIJRI_SP * scale, Tone.MUTED, bold = true)
                    }
                },
                cell {
                    WidgetLine(view.nextLabel, DateStripMetrics.NEXT_SP * scale, Tone.TEXT, bold = true, modifier = direction.paddingStart(DateStripMetrics.GAP_DP.dp))
                },
                cell {
                    WidgetLine(view.next, DateStripMetrics.NEXT_SP * scale, Tone.ACCENT, bold = true, modifier = direction.paddingStart(DateStripMetrics.WORD_GAP_DP.dp))
                },
            ),
        )
    }
}

/** The 4×1 entry in the widget picker. */
class DateStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateStripWidget()
}
