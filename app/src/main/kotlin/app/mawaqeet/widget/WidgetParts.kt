package app.mawaqeet.widget

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import app.mawaqeet.MainActivity
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.R
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.ui.appLocale
import app.mawaqeet.ui.localizedContext
import kotlinx.coroutines.flow.first
import java.time.Clock

/** What a widget needs to put the display day into words: the formatter, strings and direction of the app language. */
internal class WidgetText(val formatter: DisplayFormatter, val strings: Context, val rtl: Boolean)

/**
 * The shared body of a widget's `provideGlance`: reads settings, digits and the app language (the stored copy too,
 * for a fresh process on Android 8–12), builds the view from a fresh [PrayerCore] with [build], and rebuilds it on
 * every settings, digit or language change and on [Widgets.refreshAll] (alarms and Prayer Log changes).
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
    provideContent {
        val settings by store.settings.collectAsState(initialSettings)
        val digits by store.digitStyle.collectAsState(initialDigits)
        val language by store.appLanguage.collectAsState(initialLanguage)
        val refreshedAt = currentState(Widgets.REFRESHED_AT)
        val (view, rtl) = remember(settings, digits, language, refreshedAt) {
            val locale = appLocale(context, language)
            val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
            val core = PrayerCore(settings, Clock.systemUTC(), app.prayerLog)
            build(core, WidgetText(DisplayFormatter(locale, digits), localizedContext(context, locale), rtl)) to rtl
        }
        layout(view, rtl)
    }
}

/**
 * A widget's frame: the root in the app language's direction (the launcher lays widgets out in its own), the widget
 * background, and a tap that opens the app on today's Prayer Times, or on the Prayer Log when [opens] says so. It is
 * the widget's only tap target: no widget changes the Prayer Log.
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
    val root = RemoteViews(context.packageName, if (rtl) R.layout.widget_root_rtl else R.layout.widget_root_ltr)
    AndroidRemoteViews(root, R.id.widget_root, GlanceModifier.fillMaxSize()) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(horizontal = horizontal, vertical = vertical)
                .clickable(actionStartActivity(MainActivity.openIntent(context, opens))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            content()
        }
    }
}

/**
 * The date line, Gregorian then Hijri, as platform views: the Hijri date is the one that ellipsizes when space is
 * tight (Glance's own `Text` can't).
 */
@Composable
internal fun DateLine(date: WidgetDate) {
    val views = RemoteViews(LocalContext.current.packageName, R.layout.widget_date_line).apply {
        setTextViewText(R.id.date_gregorian, date.gregorian)
        setTextViewText(R.id.date_hijri, date.hijri)
    }
    AndroidRemoteViews(views, GlanceModifier.fillMaxWidth().wrapContentHeight())
}

internal val WIDGET_TEXT = ColorProvider(R.color.widget_text)
internal val WIDGET_MUTED = ColorProvider(R.color.widget_text_muted)
internal val WIDGET_ON_ACCENT = ColorProvider(R.color.widget_on_accent)
internal val WIDGET_ACCENT = ColorProvider(R.color.widget_accent)
