package app.mawaqeet.widget

import android.os.Build
import androidx.annotation.ColorRes
import app.mawaqeet.R

/** Where widgets take their colours from (Android 12+; Android 8–11 always uses the app's). */
enum class WidgetColors { WALLPAPER, APP }

/**
 * Settings → Widgets (#32), shared by every Widget Variant: one background [transparency] (0–100%, in steps of 10,
 * default solid), the [colors] choice, and [nextPrayerLook]: every widget on the Next Prayer's deep surface, with its
 * star (off by default, when only the Next Prayer is deep).
 */
data class WidgetAppearance(
    val transparency: Int = 0,
    val colors: WidgetColors = WidgetColors.APP,
    val nextPrayerLook: Boolean = false,
) {
    init {
        require(transparency in TRANSPARENCY_RANGE && transparency % TRANSPARENCY_STEP == 0) { "transparency $transparency" }
    }

    companion object {
        val TRANSPARENCY_RANGE = 0..100
        const val TRANSPARENCY_STEP = 10

        /** From this transparency on, text turns light with a soft shadow so it reads on any wallpaper. */
        const val LIGHT_TEXT_FROM = 60
    }
}

/**
 * How a widget is drawn under a [WidgetAppearance] on this Android version: its colour resources (day and night
 * variants each), the background's opacity, and whether text carries a shadow. Most widgets are light; the Next Prayer
 * widget draws on the deep surface ([deep]): deep teal with light text and a gold countdown (widget design board,
 * 2026-11-10). In a deep style [accent] is the gold and [accentSoft] the progress track.
 */
data class WidgetStyle(
    @param:ColorRes val background: Int,
    @param:ColorRes val text: Int,
    @param:ColorRes val muted: Int,
    @param:ColorRes val accent: Int,
    @param:ColorRes val onAccent: Int,
    @param:ColorRes val accentSoft: Int,
    @param:ColorRes val faint: Int,
    /** 0 (fully transparent) to 255 (solid). */
    val backgroundAlpha: Int,
    /** Light text with a soft shadow, for a background at [WidgetAppearance.LIGHT_TEXT_FROM]% transparency or more. */
    val shadowedText: Boolean,
    /** Android 12+ only: widgets adapt across size steps; below it they keep one layout at the default size. */
    val responsive: Boolean,
    /** Text on the next Prayer's soft fill ([accentSoft]). */
    @param:ColorRes val onAccentSoft: Int = text,
    /** Hairline dividers (the Date strip). */
    @param:ColorRes val divider: Int = faint,
    /** Today's column in the week grid. */
    @param:ColorRes val band: Int = accentSoft,
    /** The ring of a Prayer whose time has come and that isn't marked. */
    @param:ColorRes val ringMuted: Int = muted,
    /** The next Prayer's cell in Today times: a background tint, so it must be opaque. */
    @param:ColorRes val cellFill: Int = accentSoft,
    /**
     * A Clock Widget's countdown: the bright [text] on the regular surfaces (the tinted accent was too pale to read
     * there); the gold [accent] on the deep surface and Gold Clock.
     */
    @param:ColorRes val countdown: Int = text,
    val deep: Boolean = false,
    /**
     * A widget that is deep only because of [WidgetAppearance.nextPrayerLook]: [WidgetFrame] draws the Next Prayer's
     * star behind it. The Next Prayer widgets draw their own.
     */
    val star: Boolean = false,
) {
    companion object {
        fun of(appearance: WidgetAppearance, sdk: Int = Build.VERSION.SDK_INT, deep: Boolean = false): WidgetStyle {
            val modern = sdk >= Build.VERSION_CODES.S
            val wallpaper = modern && appearance.colors == WidgetColors.WALLPAPER
            val light = appearance.transparency >= WidgetAppearance.LIGHT_TEXT_FROM
            val alpha = ((100 - appearance.transparency) * 255 + 50) / 100
            if (deep || appearance.nextPrayerLook) {
                // The deep surface keeps the design's teal and gold whatever the colours choice. A Glance background
                // tint draws opaque, so the filled cell and the week's today band get their own solid fill.
                val fill = if (light) R.color.widget_over_accent_soft else R.color.widget_deep_fill
                return WidgetStyle(
                    background = R.color.widget_deep_background,
                    text = if (light) R.color.widget_over_text else R.color.widget_deep_text,
                    muted = if (light) R.color.widget_over_muted else R.color.widget_deep_muted,
                    accent = if (light) R.color.widget_over_gold else R.color.widget_deep_gold,
                    onAccent = R.color.widget_deep_background,
                    accentSoft = if (light) R.color.widget_over_faint else R.color.widget_deep_track,
                    faint = if (light) R.color.widget_over_faint else R.color.widget_deep_track,
                    backgroundAlpha = alpha,
                    shadowedText = light,
                    responsive = modern,
                    band = fill,
                    cellFill = fill,
                    countdown = if (light) R.color.widget_over_gold else R.color.widget_deep_gold,
                    deep = true,
                    star = !deep,
                )
            }
            return WidgetStyle(
                background = if (wallpaper) R.color.widget_dyn_background else R.color.widget_background,
                text = when {
                    light -> R.color.widget_over_text
                    wallpaper -> R.color.widget_dyn_text
                    else -> R.color.widget_text
                },
                muted = when {
                    light -> R.color.widget_over_muted
                    wallpaper -> R.color.widget_dyn_muted
                    else -> R.color.widget_text_muted
                },
                accent = when {
                    light -> if (wallpaper) R.color.widget_dyn_over_accent else R.color.widget_over_accent
                    wallpaper -> R.color.widget_dyn_accent
                    else -> R.color.widget_accent
                },
                onAccent = when {
                    light -> R.color.widget_over_on_accent
                    wallpaper -> R.color.widget_dyn_on_accent
                    else -> R.color.widget_on_accent
                },
                accentSoft = when {
                    light -> R.color.widget_over_accent_soft
                    wallpaper -> R.color.widget_dyn_accent_soft
                    else -> R.color.widget_accent_soft
                },
                faint = if (light) R.color.widget_over_faint else R.color.widget_dot_upcoming,
                backgroundAlpha = alpha,
                shadowedText = light,
                responsive = modern,
                onAccentSoft = when {
                    light -> R.color.widget_over_text
                    wallpaper -> R.color.widget_dyn_on_accent_soft
                    else -> R.color.widget_on_accent_soft
                },
                divider = when {
                    light -> R.color.widget_over_faint
                    wallpaper -> R.color.widget_dyn_divider
                    else -> R.color.widget_divider
                },
                band = when {
                    light -> R.color.widget_over_accent_soft
                    wallpaper -> R.color.widget_dyn_band
                    else -> R.color.widget_band
                },
                ringMuted = when {
                    light -> R.color.widget_over_muted
                    wallpaper -> R.color.widget_dyn_muted
                    else -> R.color.widget_ring_muted
                },
            )
        }
    }
}
