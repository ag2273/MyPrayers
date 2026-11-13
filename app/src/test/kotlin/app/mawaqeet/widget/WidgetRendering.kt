package app.mawaqeet.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt

/**
 * Renders [widget] through Glance at [width]×[height] dp and lays the RemoteViews out as a launcher would, with real
 * text measurement under `@GraphicsMode(NATIVE)`. Composed on the main thread, as on a phone, so a Prayer Log query
 * in composition fails here too (Room refuses the main thread). A Responsive widget is composed at the bucket the
 * launcher would pick for that size (the largest that fits, else the smallest), as on a phone, and laid out at the
 * real size.
 */
internal fun renderWidget(context: Context, widget: GlanceAppWidget, width: Int, height: Int): View {
    val views = runBlocking { widget.compose(context, size = composedSize(widget, DpSize(width.dp, height.dp))) }
    val density = context.resources.displayMetrics.density
    val w = (width * density).roundToInt()
    val h = (height * density).roundToInt()
    val parent = FrameLayout(context)
    return views.apply(context, parent).also {
        parent.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, w, h)
    }
}

/** The size Glance composes [widget] at when the launcher gives it [real]. */
internal fun composedSize(widget: GlanceAppWidget, real: DpSize): DpSize {
    val sizes = (widget.sizeMode as? SizeMode.Responsive)?.sizes ?: return real
    return sizes.filter { it.width <= real.width && it.height <= real.height }.maxByOrNull { it.width.value * it.height.value }
        ?: sizes.minBy { it.width.value * it.height.value }
}

/** Every visible, non-empty TextView under [view]. */
internal fun widgetTexts(view: View): List<TextView> = when (view) {
    is TextView -> listOf(view)
    is ViewGroup -> (0 until view.childCount).flatMap { widgetTexts(view.getChildAt(it)) }
    else -> emptyList()
}.filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }

/** The Hijri date, the one line that may shorten by design. */
internal val isHijriDate: (String) -> Boolean = { "هـ" in it || it.endsWith(" AH") }

/** No text is wider or taller than its view, or ellipsized, except the texts [allowEllipsis] accepts. */
internal fun assertNothingClipped(root: View, allowEllipsis: (String) -> Boolean = { false }) {
    for (text in widgetTexts(root)) {
        val layout = text.layout ?: continue
        val needed = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val has = (text.width - text.totalPaddingLeft - text.totalPaddingRight).toFloat()
        if (allowEllipsis(text.text.toString())) continue
        assertTrue("«${text.text}» needs ${needed}px, has ${has}px", needed <= has + 0.5f)
        assertEquals("«${text.text}» is ellipsized", 0, layout.getEllipsisCount(0))
        assertTrue("«${text.text}» is cut vertically: ${text.height}px for ${layout.height}px", text.height >= layout.height)
    }
}

/** Every view under [view] with a tap action. */
internal fun clickableViews(view: View): List<View> =
    (if (view.hasOnClickListeners()) listOf(view) else emptyList()) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { clickableViews(view.getChildAt(it)) } else emptyList())

/**
 * The colours a rendered widget's background shows: its tint when it is a flat colour, or the stops of its gradient art
 * (1.4: the app's sky and the deep Next Prayer surface).
 */
internal fun backgroundColours(root: View): List<Int> {
    val bg = root.findViewById<android.widget.ImageView>(app.mawaqeet.R.id.widget_bg)
    bg.imageTintList?.let { return listOf(it.defaultColor) }
    val art = bg.drawable as? android.graphics.drawable.GradientDrawable ?: return emptyList()
    return art.colors?.toList().orEmpty()
}

/** The stops of a gradient drawable resource, as [backgroundColours] reads them off a widget. */
internal fun artColours(context: Context, @androidx.annotation.DrawableRes art: Int): List<Int> =
    (androidx.core.content.ContextCompat.getDrawable(context, art) as android.graphics.drawable.GradientDrawable).colors!!.toList()
