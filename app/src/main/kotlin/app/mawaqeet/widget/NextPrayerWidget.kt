package app.mawaqeet.widget

import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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
import kotlinx.coroutines.flow.first
import java.time.Clock

/**
 * The Next Prayer Widget Variant (2×2, and 2×1 via [NextPrayerCompactWidgetReceiver]): the next Prayer's name, its
 * time and a live `بعد H:MM:SS` countdown. A thin layer over [NextPrayerContent]: it reads settings, digits and the
 * app language (the stored copy too, for a fresh process on Android 8–12), and re-renders on [Widgets.refreshAll].
 * Tapping opens the app on today's Prayer Times.
 */
class NextPrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, SQUARE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = (context.applicationContext as MawaqeetApplication).settings
        val prayerLog = (context.applicationContext as MawaqeetApplication).prayerLog
        val initialSettings = store.settings.first()
        val initialDigits = store.digitStyle.first()
        val initialLanguage = store.appLanguage.first()
        provideContent {
            val settings by store.settings.collectAsState(initialSettings)
            val digits by store.digitStyle.collectAsState(initialDigits)
            val language by store.appLanguage.collectAsState(initialLanguage)
            val refreshedAt = currentState(Widgets.REFRESHED_AT)
            val view = remember(settings, digits, language, refreshedAt) {
                NextPrayerView.build(context, settings, digits, language, PrayerCore(settings, Clock.systemUTC(), prayerLog))
            }
            NextPrayerLayout(view, compact = LocalSize.current.height < SQUARE.height)
        }
    }

    private companion object {
        val COMPACT = DpSize(110.dp, 40.dp)
        val SQUARE = DpSize(110.dp, 100.dp)
    }
}

/** Everything the layout draws, already in the user's language and digits. */
private data class NextPrayerView(
    val label: String,
    val name: String,
    val time: String,
    /** `Chronometer` format, e.g. "بعد %s". */
    val countdownFormat: String,
    /** `SystemClock.elapsedRealtime()` at which the countdown reaches zero. */
    val countdownBase: Long,
    val rtl: Boolean,
) {
    companion object {
        fun build(context: Context, settings: PrayerSettings, digits: DigitStyle, language: String, core: PrayerCore): NextPrayerView {
            val locale = appLocale(context, language)
            val content = NextPrayerContent.of(core.displayDay(), Clock.systemUTC().instant(), DisplayFormatter(locale, digits))
            val strings = localizedContext(context, locale)
            return NextPrayerView(
                label = strings.getString(R.string.widget_next_prayer),
                name = strings.getString(content.nameRes),
                time = content.time,
                countdownFormat = strings.getString(R.string.widget_countdown_format),
                countdownBase = SystemClock.elapsedRealtime() + content.untilNext.toMillis(),
                rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL,
            )
        }
    }
}

@Composable
private fun NextPrayerLayout(view: NextPrayerView, compact: Boolean) {
    val packageName = androidx.glance.LocalContext.current.packageName
    // The launcher lays widgets out in ITS language; the root sets the app language's direction (Arabic RTL first).
    val root = RemoteViews(packageName, if (view.rtl) R.layout.widget_root_rtl else R.layout.widget_root_ltr)
    AndroidRemoteViews(root, R.id.widget_root, GlanceModifier.fillMaxSize()) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp)
                .clickable(actionStartActivity(MainActivity.openIntent(androidx.glance.LocalContext.current, MainActivity.Opens.TODAY))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(view.name, style = TextStyle(color = ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(view.time, style = TextStyle(color = MUTED, fontSize = 13.sp), maxLines = 1)
                }
                Countdown(view, packageName, R.layout.widget_countdown_compact)
            } else {
                Text(view.label, style = TextStyle(color = MUTED, fontSize = 12.sp), maxLines = 1)
                Text(view.name, style = TextStyle(color = ACCENT, fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(view.time, style = TextStyle(color = TEXT, fontSize = 15.sp), maxLines = 1)
                Spacer(GlanceModifier.padding(top = 4.dp))
                Countdown(view, packageName, R.layout.widget_countdown)
            }
        }
    }
}

/** A platform count-down `Chronometer`, ticked by the launcher; it always shows seconds. */
@Composable
private fun Countdown(view: NextPrayerView, packageName: String, layout: Int) {
    val chronometer = RemoteViews(packageName, layout).apply {
        setChronometer(R.id.countdown, view.countdownBase, view.countdownFormat, true)
        setChronometerCountDown(R.id.countdown, true)
    }
    AndroidRemoteViews(chronometer)
}

private val TEXT = ColorProvider(R.color.widget_text)
private val MUTED = ColorProvider(R.color.widget_text_muted)
private val ACCENT = ColorProvider(R.color.widget_accent)

/** The 2×2 entry in the widget picker. */
class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}

/** The compact 2×1 entry in the widget picker; the same widget, laid out for its height. */
class NextPrayerCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}
