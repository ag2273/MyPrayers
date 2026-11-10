package app.mawaqeet.widget

import android.text.TextPaint
import android.text.StaticLayout
import android.graphics.Typeface
import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.wrapContentWidth
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.absolutePadding
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.unit.ColorProvider
import app.mawaqeet.MainActivity
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.R
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.appLocale
import app.mawaqeet.ui.localizedContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

/** What a widget needs to put the display day into words: the formatter, strings and direction of the app language. */
internal class WidgetText(val formatter: DisplayFormatter, val strings: Context, val rtl: Boolean)

/**
 * The app language's reading direction, laid out in the launcher's. Glance's views (and the launcher) follow the
 * phone's language, not the app's: with the phone in English and the app in Arabic, `Alignment.Start` and a Row's
 * first child land on the left. So widgets place things through this: [start] is the app language's start, and
 * [inReadingOrder] puts a Row's children so the first one reads first.
 */
internal class WidgetDirection(val rtl: Boolean, hostRtl: Boolean = phoneRtl()) {
    private val flipped = rtl != hostRtl

    val start: Alignment.Horizontal get() = if (flipped) Alignment.End else Alignment.Start

    /** A Box's content at the app language's end, vertically centred. */
    val endAlignment: Alignment get() = if (flipped) Alignment.CenterStart else Alignment.CenterEnd

    fun <T> inReadingOrder(items: List<T>): List<T> = if (flipped) items.asReversed() else items

    /** Padding on the app language's start side, whatever the launcher's direction. */
    fun paddingStart(padding: Dp): GlanceModifier =
        if (rtl) GlanceModifier.absolutePadding(right = padding) else GlanceModifier.absolutePadding(left = padding)

    /** For a platform view inside a widget: lays it out in the app language's direction whatever the launcher's. */
    fun applyTo(views: RemoteViews, viewId: Int) =
        views.setInt(viewId, "setLayoutDirection", if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)

    companion object {
        /**
         * The direction the launcher lays widgets out in: the phone's language. From Android 13 the app's own
         * language (a per-app language) also reaches this process's system resources, so there it is read from
         * [LocaleManager.getSystemLocales], the phone's language whatever the app's.
         */
        fun phoneRtl(context: Context? = null): Boolean {
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val system = context.getSystemService(LocaleManager::class.java)?.systemLocales
                if (system != null && !system.isEmpty) {
                    return TextUtils.getLayoutDirectionFromLocale(system[0]) == View.LAYOUT_DIRECTION_RTL
                }
            }
            return Resources.getSystem().configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        }
    }
}

/** The app language's direction, for every widget part; set by [provideFromCore]. */
internal val LocalWidgetDirection = staticCompositionLocalOf { WidgetDirection(rtl = false) }

/** One child of a [ReadingRow]. */
internal typealias RowCell = @Composable RowScope.() -> Unit

/** Spells out a [RowCell] where Kotlin can't infer a lambda's type. */
internal fun cell(content: RowCell): RowCell = content

/** A Row whose [children] read in the app language's order: the first is on the right in Arabic. */
@Composable
internal fun ReadingRow(
    modifier: GlanceModifier = GlanceModifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    children: List<RowCell>,
) {
    val direction = LocalWidgetDirection.current
    Row(modifier = modifier, verticalAlignment = verticalAlignment) {
        direction.inReadingOrder(children).forEach { it() }
    }
}

/** The appearance every widget part draws with (Settings → Widgets, #32). */
internal val LocalWidgetStyle = staticCompositionLocalOf { WidgetStyle.of(WidgetAppearance()) }

/**
 * The shared body of a widget's `provideGlance`: reads settings, digits, the app language (the stored copy too, for a
 * fresh process on Android 8–12) and the widget appearance, builds the view from a fresh [PrayerCore] with [build],
 * and rebuilds it on every change of those and on [Widgets.refreshAll] (alarms and Prayer Log changes).
 *
 * [build] runs on [Dispatchers.IO], never in composition: on a phone Glance composes on the main thread, where the
 * Prayer Log (Room) refuses to be read, and the widget then shows only "Can't show content".
 */
internal suspend fun <T> GlanceAppWidget.provideFromCore(
    context: Context,
    build: (PrayerCore, WidgetText) -> T,
    layout: @Composable (T, rtl: Boolean) -> Unit,
) {
    val app = context.applicationContext as MawaqeetApplication
    val store = app.settings
    val initialSettings = store.settings.first()
    val initialDigits = store.digitStyle.first()
    val initialLanguage = store.appLanguage.first()
    val initialAppearance = store.widgetAppearance.first()
    suspend fun view(settings: PrayerSettings, digits: DigitStyle, language: String): Pair<T, Boolean> =
        withContext(Dispatchers.IO) {
            val locale = appLocale(context, language)
            val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
            val core = PrayerCore(settings, Clock.systemUTC(), app.prayerLog)
            build(core, WidgetText(DisplayFormatter(locale, digits), localizedContext(context, locale), rtl)) to rtl
        }
    val initialView = view(initialSettings, initialDigits, initialLanguage)
    provideContent {
        val settings by store.settings.collectAsState(initialSettings)
        val digits by store.digitStyle.collectAsState(initialDigits)
        val language by store.appLanguage.collectAsState(initialLanguage)
        val appearance by store.widgetAppearance.collectAsState(initialAppearance)
        val refreshedAt = currentState(Widgets.REFRESHED_AT)
        val current by produceState(initialView, settings, digits, language, refreshedAt) {
            // The first composition already has its view; rebuild only after something changed.
            if (settings != initialSettings || digits != initialDigits || language != initialLanguage || refreshedAt != null) {
                value = view(settings, digits, language)
            }
        }
        CompositionLocalProvider(
            LocalWidgetStyle provides remember(appearance) { WidgetStyle.of(appearance) },
            LocalWidgetDirection provides remember(current.second) { WidgetDirection(current.second, WidgetDirection.phoneRtl(context)) },
        ) {
            layout(current.first, current.second)
        }
    }
}

/**
 * A widget's frame: content aligned to the app language's start ([WidgetDirection]), the background in the style's
 * colour and opacity, and a tap that opens the app on today's Prayer Times, or on the
 * Prayer Log when [opens] says so. It is the widget's only tap target: no widget changes the Prayer Log.
 */
@Composable
internal fun WidgetFrame(
    rtl: Boolean,
    horizontal: Dp,
    vertical: Dp,
    opens: MainActivity.Opens = MainActivity.Opens.TODAY,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val root = RemoteViews(context.packageName, if (rtl) R.layout.widget_root_rtl else R.layout.widget_root_ltr).apply {
        setColor(context, R.id.widget_bg, "setImageTintList", "setColorFilter", style.background)
        setInt(R.id.widget_bg, "setImageAlpha", style.backgroundAlpha)
    }
    AndroidRemoteViews(root, R.id.widget_root, GlanceModifier.fillMaxSize()) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = horizontal, vertical = vertical)
                .clickable(actionStartActivity(MainActivity.openIntent(context, opens))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = LocalWidgetDirection.current.start,
        ) {
            content()
        }
    }
}

/**
 * Text grows in steps when the launcher gives a widget more room than its smallest size ([minWidth]×[minHeight] dp):
 * 1 at that size, then 1.15 and 1.3. Android 12+ only; on Android 8–11 widgets keep one layout at the default size.
 */
@Composable
internal fun textStep(minWidth: Float, minHeight: Float): Float {
    if (!LocalWidgetStyle.current.responsive) return 1f
    val size = LocalSize.current
    val room = minOf(size.width.value / minWidth, size.height.value / minHeight)
    return when {
        room >= 1.6f -> 1.3f
        room >= 1.3f -> 1.15f
        else -> 1f
    }
}

/** Which of the style's colours a piece of widget text takes. */
internal enum class Tone { TEXT, MUTED, ACCENT, ON_ACCENT }

@ColorRes
internal fun WidgetStyle.colorOf(tone: Tone): Int = when (tone) {
    Tone.TEXT -> text
    Tone.MUTED -> muted
    Tone.ACCENT -> accent
    Tone.ON_ACCENT -> onAccent
}

/** The style's colour as a Glance tint, for the white shape drawables (backgrounds, marks, dots). */
internal fun tint(@ColorRes color: Int) = ColorFilter.tint(ColorProvider(color))

/**
 * One line of widget text as a platform `TextView`, so it can carry the soft shadow that light text needs over a
 * transparent background (Glance's `Text` has no shadow) and ellipsize at the end rather than being clipped. Colours
 * are resources, so on Android 12+ the launcher follows light and dark itself.
 */
@Composable
internal fun WidgetLine(
    text: String,
    sp: Float,
    tone: Tone = Tone.TEXT,
    bold: Boolean = false,
    center: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    // On the accent fill (a highlighted cell) the dark on-accent text needs no shadow.
    val shadow = style.shadowedText && tone != Tone.ON_ACCENT
    val layout = when {
        shadow && bold -> R.layout.widget_text_shadow_bold
        shadow -> R.layout.widget_text_shadow
        bold -> R.layout.widget_text_bold
        else -> R.layout.widget_text_plain
    }
    val views = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.widget_text, text)
        setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, sp)
        setColor(context, R.id.widget_text, "setTextColor", "setTextColor", style.colorOf(tone))
        LocalWidgetDirection.current.applyTo(this, R.id.widget_text)
    }
    // setGravity isn't remotable before Android 12, so a centred line wraps its text inside a centring box.
    // An AndroidRemoteViews fills its parent unless told otherwise; a line wraps its text unless [modifier] says more.
    val wrap = GlanceModifier.wrapContentWidth().wrapContentHeight()
    if (center) {
        Box(wrap.then(modifier), contentAlignment = Alignment.Center) { AndroidRemoteViews(views, wrap) }
    } else {
        AndroidRemoteViews(views, wrap.then(modifier))
    }
}

/** Sets a colour resource: as a resource on Android 12+ (day/night follow the launcher), resolved before that. */
private fun RemoteViews.setColor(context: Context, id: Int, listMethod: String, intMethod: String, @ColorRes color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setColorStateList(id, listMethod, color)
    } else {
        setInt(id, intMethod, context.getColor(color))
    }
}

/**
 * The height in dp of one line of [text] at [sp], measured with the system font as the launcher lays it out (no font
 * padding). Arabic lines are taller than Latin ones, so layouts that must fit a small height measure rather than guess.
 */
internal fun lineHeightDp(text: String, sp: Float, fontScale: Float = 1f, bold: Boolean = false): Int {
    val paint = TextPaint().apply {
        textSize = sp * fontScale
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, 10_000)
        .setIncludePad(false)
        // TextView uses the (taller) Arabic fallback font's line spacing from Android 9 on.
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUseLineSpacingFromFallbacks(true) }
        .build().height
}

/** The width in dp of [text] at [sp] in the system font the launcher draws in. */
internal fun textWidthDp(text: String, sp: Float, fontScale: Float = 1f, bold: Boolean = false): Float =
    TextPaint().apply {
        textSize = sp * fontScale
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }.measureText(text)

/**
 * The largest scale from 1 down, in 5% steps and never below [floor], at which [fits] holds: a layout drawn at the
 * design's sizes shrinks as a whole, so its proportions stay, when the launcher gives it less room than the design.
 */
internal inline fun fitScale(floor: Float = 0.6f, fits: (Float) -> Boolean): Float {
    var scale = 1f
    while (scale > floor + 0.001f && !fits(scale)) scale -= 0.05f
    return scale
}
