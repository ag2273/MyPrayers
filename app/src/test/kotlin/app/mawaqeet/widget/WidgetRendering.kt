package app.mawaqeet.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt

/**
 * Renders [widget] through Glance at [width]×[height] dp and lays the RemoteViews out as a launcher would, with real
 * text measurement under `@GraphicsMode(NATIVE)`. Composed off the main thread, since widgets query the Prayer Log.
 */
internal fun renderWidget(context: Context, widget: GlanceAppWidget, width: Int, height: Int): View {
    val views = runBlocking(Dispatchers.Default) { widget.compose(context, size = DpSize(width.dp, height.dp)) }
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

/** Every visible, non-empty TextView under [view]. */
internal fun widgetTexts(view: View): List<TextView> = when (view) {
    is TextView -> listOf(view)
    is ViewGroup -> (0 until view.childCount).flatMap { widgetTexts(view.getChildAt(it)) }
    else -> emptyList()
}.filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }

/** No text is wider or taller than its view, or ellipsized, except the views in [allowEllipsis]. */
internal fun assertNothingClipped(root: View, allowEllipsis: Set<Int> = emptySet()) {
    for (text in widgetTexts(root)) {
        val layout = text.layout ?: continue
        val needed = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val has = (text.width - text.totalPaddingLeft - text.totalPaddingRight).toFloat()
        if (text.id in allowEllipsis) continue
        assertTrue("«${text.text}» needs ${needed}px, has ${has}px", needed <= has + 0.5f)
        assertEquals("«${text.text}» is ellipsized", 0, layout.getEllipsisCount(0))
        assertTrue("«${text.text}» is cut vertically: ${text.height}px for ${layout.height}px", text.height >= layout.height)
    }
}

/** Every view under [view] with a tap action. */
internal fun clickableViews(view: View): List<View> =
    (if (view.hasOnClickListeners()) listOf(view) else emptyList()) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { clickableViews(view.getChildAt(it)) } else emptyList())
