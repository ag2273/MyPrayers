package app.mawaqeet.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.Prayer
import java.time.Duration
import java.time.Instant

/**
 * The living sky's four moods (design-system.md §2.4). They follow the prayer day, not the clock: dawn from Fajr to
 * half an hour after Sunrise, day until half an hour before Maghrib, dusk until 45 minutes after Isha, then night.
 */
enum class SkyPhase {
    DAWN, DAY, DUSK, NIGHT;

    companion object {
        private val AFTER_SUNRISE = Duration.ofMinutes(30)
        private val BEFORE_MAGHRIB = Duration.ofMinutes(30)
        private val AFTER_ISHA = Duration.ofMinutes(45)

        /** The phase at [now] on [day] (the Prayer Day running now). */
        fun of(day: DisplayDay, now: Instant): SkyPhase {
            fun at(prayer: Prayer) = day.timeOf(prayer).toInstant()
            return when {
                now.isBefore(at(Prayer.FAJR)) -> NIGHT
                now.isBefore(day.sunrise.toInstant().plus(AFTER_SUNRISE)) -> DAWN
                now.isBefore(at(Prayer.MAGHRIB).minus(BEFORE_MAGHRIB)) -> DAY
                now.isBefore(at(Prayer.ISHA).plus(AFTER_ISHA)) -> DUSK
                else -> NIGHT
            }
        }
    }
}

/** The two ambient glows of a phase (design tokens `--glow-a`, `--glow-b`). */
private fun SkyPhase.glows(): Pair<Color, Color> = when (this) {
    SkyPhase.DAWN -> Color(0x8CE0A246) to Color(0x59C97A6B)
    SkyPhase.DAY -> Color(0xD9F2F6F4) to Color(0x381F6F66)
    SkyPhase.DUSK -> Color(0x731F6F66) to Color(0x4DE0A246)
    SkyPhase.NIGHT -> Color(0x661F6F66) to Color(0xD90E1917)
}

/**
 * The page behind every screen: the theme's sky gradient, the phase's two glows breathing slowly, and the sun (a pale
 * moon at night) rising out of the top start corner. [content] draws on top.
 */
@Composable
fun SkyBackground(phase: SkyPhase, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val skin = LocalSkin.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val breath = rememberInfiniteTransition(label = "sky")
    // 26 s in and out, as on the prototype; the system's animator scale (Remove animations) stills it.
    val glow by breath.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val rise by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sun",
    )
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawSky(skin, phase, glow, rise, rtl) }
        // Transparent scaffolds take their content colour from here (contentColorFor finds no match for Transparent).
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

private fun DrawScope.drawSky(skin: Skin, phase: SkyPhase, glow: Float, rise: Float, rtl: Boolean) {
    val (w, h) = size.width to size.height
    // 170°: from just right of the top centre to just left of the bottom centre.
    drawRect(
        Brush.linearGradient(
            0f to skin.sky[0], 0.52f to skin.sky[1], 1f to skin.sky[2],
            start = Offset(w * 0.5f + h * 0.088f, 0f),
            end = Offset(w * 0.5f - h * 0.088f, h),
        ),
    )
    val (a, b) = phase.glows()
    fun glowAt(color: Color, x: Float, y: Float, rx: Float) {
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = color.alpha * glow), Color.Transparent), Offset(x, y), rx),
            radius = rx,
            center = Offset(x, y),
        )
    }
    glowAt(a, w * 0.24f, h * 0.12f, w * 0.62f)
    glowAt(b, w * 0.82f, h * 0.06f, w * 0.7f)
    // The sun: 170dp, its centre 13dp in from the start edge and 11dp above the top, drifting up and down by 12dp.
    val r = 85.dp.toPx()
    val cx = 13.dp.toPx().let { if (rtl) w - it else it }
    val cy = -11.dp.toPx() + (10 - 24 * rise).dp.toPx()
    val night = phase == SkyPhase.NIGHT
    val core = if (night) Color(0xFFF4F8F6) else Color(0xFFFFF3DF)
    val edge = if (night) Color(0xFFBFD4CE) else Color(0xFFE0A246)
    val opacity = if (phase == SkyPhase.NIGHT || phase == SkyPhase.DUSK) 0.35f else 0.55f
    // The soft halo first, then the disc.
    drawCircle(
        Brush.radialGradient(listOf(edge.copy(alpha = 0.28f * opacity * 2), Color.Transparent), Offset(cx, cy), r * 1.9f),
        radius = r * 1.9f,
        center = Offset(cx, cy),
    )
    drawCircle(
        Brush.radialGradient(
            0f to core.copy(alpha = opacity), 0.94f to edge.copy(alpha = opacity), 1f to edge.copy(alpha = 0f),
            center = Offset(cx - r * 0.2f, cy - r * 0.24f),
            radius = r * 1.1f,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
}
