package app.mawaqeet.widget

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.wrapContentHeight
import app.mawaqeet.R

/**
 * The Date strip Widget Variant (4×1): the Gregorian date, then the Hijri date (which shortens first when space is
 * tight), with the next Prayer and its time on a second line. A thin layer over [DateStripContent].
 */
class DateStripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideDisplayDay(
        context,
        build = { day, text -> DateStripView.of(DateStripContent.of(day, text.formatter), text) },
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
    /** `widget_date_strip`'s gap between its two lines. */
    const val LINE_GAP_DP = 2f
    const val MAX_SP = 13f
    const val MIN_SP = 9f

    /**
     * The largest text size from [MAX_SP] down, in half-sp steps, at which both lines fit [heightDp] (the whole
     * widget), measured with the system font as the launcher lays them out: Arabic lines are taller than Latin ones,
     * and at the smallest 4×1 height they don't fit at full size.
     */
    fun fitSp(view: DateStripText, heightDp: Float, fontScale: Float = 1f): Float {
        val room = heightDp - 2 * verticalPaddingDp(heightDp) - LINE_GAP_DP
        val paint = TextPaint()
        fun lineHeight(text: String, bold: Boolean) = StaticLayout.Builder
            .obtain(text, 0, text.length, paint.apply { typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }, 10_000)
            .setIncludePad(false)
            // TextView uses the (taller) Arabic fallback font's line spacing from Android 9 on.
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUseLineSpacingFromFallbacks(true) }
            .build().height
        var sp = MAX_SP
        while (sp > MIN_SP) {
            paint.textSize = sp * fontScale
            val first = maxOf(lineHeight(view.date.gregorian, bold = true), lineHeight(view.date.hijri, bold = false))
            if (first + lineHeight(view.next, bold = true) <= room) break
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
    val sp = DateStripMetrics.fitSp(view, height, LocalContext.current.resources.configuration.fontScale)
    // Platform views for both lines: the Hijri date ellipsizes first, and without font padding the two lines fit even
    // at the smallest 4×1 height (Glance's own Text can do neither).
    val views = RemoteViews(LocalContext.current.packageName, R.layout.widget_date_strip).apply {
        setTextViewText(R.id.date_gregorian, view.date.gregorian)
        setTextViewText(R.id.date_hijri, view.date.hijri)
        setTextViewText(R.id.next_line, view.next)
        for (id in listOf(R.id.date_gregorian, R.id.date_hijri, R.id.next_line)) setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp)
    }
    val vertical = DateStripMetrics.verticalPaddingDp(height).dp
    WidgetFrame(rtl, horizontal = DateStripMetrics.horizontalPadding, vertical = vertical) {
        AndroidRemoteViews(views, GlanceModifier.fillMaxWidth().wrapContentHeight())
    }
}

/** The 4×1 entry in the widget picker. */
class DateStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateStripWidget()
}
