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
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.Image
import androidx.glance.ImageProvider
import app.mawaqeet.R

/**
 * The Date strip Widget Variant (4×1, widget design board 2026-11-10): three parts split by hairlines, the weekday over
 * the day and month, a small crescent with the Hijri day and month over its year, and «القادمة» over the next Prayer
 * and its time. A thin layer over [DateStripContent].
 */
class DateStripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        build = { core, text -> DateStripView.of(DateStripContent.of(core.displayDay(), text.formatter), text) },
        layout = { view, rtl -> DateStripLayout(view, rtl) },
    )
}

private data class DateStripView(
    override val date: WidgetDate,
    override val nextLabel: String,
    override val next: String,
    val weekday: String,
    val dayMonth: String,
    val hijriDayMonth: String,
    val hijriYear: String,
    val isFriday: Boolean,
) : DateStripText {
    override val parts: List<Pair<String, String>> get() = listOf(weekday to dayMonth, hijriDayMonth to hijriYear, next to nextLabel)

    companion object {
        fun of(content: DateStripContent, text: WidgetText) = DateStripView(
            date = content.date,
            nextLabel = text.strings.getString(R.string.widget_next_short),
            next = "${text.strings.getString(content.nextNameRes)} ${content.nextTime}",
            weekday = content.weekday,
            dayMonth = content.dayMonth,
            hijriDayMonth = content.hijriDayMonth,
            hijriYear = content.hijriYear,
            isFriday = content.isFriday,
        )
    }
}

/** Sizes of the 4×1 layout, from the widget design board, shared with its render test. */
internal object DateStripMetrics {
    val horizontalPadding = 16.dp
    /** Little vertical padding at the smallest heights, where every dp goes to the two lines. */
    fun verticalPaddingDp(heightDp: Float) = if (heightDp < 56f) 2f else 8f
    const val MIN_WIDTH_DP = 250f
    const val MIN_HEIGHT_DP = 40f
    const val WEEKDAY_SP = 18f
    const val HIJRI_SP = 13f
    const val NEXT_SP = 14f
    const val SMALL_SP = 12f
    const val LABEL_SP = 11f
    const val CRESCENT_DP = 13f
    /** Each hairline divider takes this much across, its margins included. */
    const val DIVIDER_DP = 25f
    /** Never shrink below this share of the board's sizes. */
    const val MIN_SCALE = 0.5f

    /**
     * The largest scale from [step] down at which each part's two lines fit [heightDp] (the whole widget), measured
     * with the system font as the launcher lays them out, and the three parts and two hairlines fit [widthDp].
     */
    fun scale(view: DateStripText, widthDp: Float, heightDp: Float, fontScale: Float = 1f, step: Float = 1f): Float {
        val room = heightDp - 2 * verticalPaddingDp(heightDp) - 1
        val across = widthDp - 2 * horizontalPadding.value
        val (day, hijri, next) = view.parts
        return step * fitScale(floor = MIN_SCALE) {
            val s = step * it
            maxOf(
                lineHeightDp(day.first, WEEKDAY_SP * s, fontScale, bold = true) + lineHeightDp(day.second, SMALL_SP * s, fontScale),
                lineHeightDp(next.second, LABEL_SP * s, fontScale) + lineHeightDp(next.first, NEXT_SP * s, fontScale, bold = true),
            ) <= room &&
                maxOf(textWidthDp(day.first, WEEKDAY_SP * s, fontScale, bold = true), textWidthDp(day.second, SMALL_SP * s, fontScale)) +
                maxOf(CRESCENT_DP * s + 5 + textWidthDp(hijri.first, HIJRI_SP * s, fontScale, bold = true), textWidthDp(hijri.second, SMALL_SP * s, fontScale)) +
                maxOf(textWidthDp(next.first, NEXT_SP * s, fontScale, bold = true), textWidthDp(next.second, LABEL_SP * s, fontScale)) +
                2 * DIVIDER_DP <= across
        }
    }
}

/** The texts, for measuring: each part's top line and the line under it. */
internal interface DateStripText {
    val date: WidgetDate
    val nextLabel: String
    val next: String
    val parts: List<Pair<String, String>>
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
    val divider = cell {
        Box(GlanceModifier.padding(horizontal = 12.dp)) {
            Image(ImageProvider(R.drawable.widget_rect), contentDescription = null, modifier = GlanceModifier.width(1.dp).height((36 * scale).dp), colorFilter = tint(style.divider))
        }
    }
    WidgetFrame(rtl, horizontal = DateStripMetrics.horizontalPadding, vertical = DateStripMetrics.verticalPaddingDp(height).dp) {
        ReadingRow(
            GlanceModifier.fillMaxWidth(),
            Alignment.CenterVertically,
            listOf(
                cell {
                    Column(horizontalAlignment = direction.start) {
                        WidgetLine(view.weekday, DateStripMetrics.WEEKDAY_SP * scale, if (view.isFriday) Tone.ON_SOFT else Tone.TEXT, bold = true)
                        WidgetLine(view.dayMonth, DateStripMetrics.SMALL_SP * scale, Tone.MUTED)
                    }
                },
                divider,
                cell {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = direction.start) {
                        ReadingRow(
                            verticalAlignment = Alignment.CenterVertically,
                            children = listOf(
                                cell {
                                    Image(ImageProvider(R.drawable.widget_crescent), contentDescription = null, modifier = GlanceModifier.size((DateStripMetrics.CRESCENT_DP * scale).dp), colorFilter = tint(style.accent))
                                },
                                cell { WidgetLine(view.hijriDayMonth, DateStripMetrics.HIJRI_SP * scale, Tone.TEXT, bold = true, modifier = direction.paddingStart(5.dp)) },
                            ),
                        )
                        WidgetLine(view.hijriYear, DateStripMetrics.SMALL_SP * scale, Tone.MUTED)
                    }
                },
                divider,
                cell {
                    Column(horizontalAlignment = direction.start) {
                        WidgetLine(view.nextLabel, DateStripMetrics.LABEL_SP * scale, Tone.MUTED)
                        WidgetLine(view.next, DateStripMetrics.NEXT_SP * scale, Tone.ACCENT, bold = true)
                    }
                },
            ),
        )
    }
}

/** The 4×1 entry in the widget picker. */
class DateStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateStripWidget()
}
