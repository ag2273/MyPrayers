package app.mawaqeet.widget

import android.content.Context
import android.os.Build
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import app.mawaqeet.R
import java.time.Clock

/**
 * The Gold Clock Widget Variant «ساعة ذهبية» (4×2): Big Clock's layout and content in one fixed look, a black surface,
 * a white thin clock, a gold countdown and bar, muted gold Stops and a light grey date line. Settings → Widgets
 * (transparency, colours, «كل الأدوات بتصميم الصلاة القادمة») never changes it.
 */
class GoldClockWidget(private val clock: Clock = Clock.systemDefaultZone()) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideFromCore(
        context,
        clock = clock,
        build = { core, text -> ClockView.of(clockContent(context, core.displayDay(), text, clock), text, context, clockWeather(context, core, clock)) },
        fixedStyle = goldClockStyle(),
        layout = { view, rtl -> BigClockLayout(view, rtl, GOLD_TONES) },
    )
}

/** Both Stops in muted gold; the date line in the style's [WidgetStyle.onAccentSoft], the light grey. */
private val GOLD_TONES = ClockTones(previous = Tone.MUTED, next = Tone.MUTED, date = Tone.ON_SOFT)

/** Gold Clock's own palette: solid, no shadow, the same on every Android version and in light and dark. */
internal fun goldClockStyle(sdk: Int = Build.VERSION.SDK_INT) = WidgetStyle(
    background = R.color.widget_gold_background,
    text = R.color.widget_gold_text,
    muted = R.color.widget_gold_muted,
    accent = R.color.widget_gold,
    onAccent = R.color.widget_gold_background,
    accentSoft = R.color.widget_gold_track,
    faint = R.color.widget_gold_track,
    backgroundAlpha = 255,
    shadowedText = false,
    responsive = sdk >= Build.VERSION_CODES.S,
    onAccentSoft = R.color.widget_gold_date,
    countdown = R.color.widget_gold,
)

/** The Gold Clock entry in the widget picker (4×2, down to 4×1). */
class GoldClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoldClockWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopClockTicksIfNoneLeft(context)
    }
}
