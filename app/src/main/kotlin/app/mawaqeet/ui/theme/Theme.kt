package app.mawaqeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.mawaqeet.R

// Brand palette (spec #14 "Visual identity"): light / dark pairs.
private val NightTeal = Color(0xFF1F6F66)
// "A Day That Breathes" (1.4): the dark accent is the design's mint.
private val TealGlow = Color(0xFF8FD3C8)
private val DawnAmber = Color(0xFFE0A246)
private val DawnAmberDark = Color(0xFFF2B65E)
private val DayMist = Color(0xFFF2F6F4)
private val IshaNight = Color(0xFF0E1917)
private val Ink = Color(0xFF142421)
private val InkDark = Color(0xFFE4EEEB)
// The Next Prayer card (redesign): a deep teal card that is the same in light and dark, with amber countdown.
private val HeroTeal = Color(0xFF134A44)
private val HeroAmber = Color(0xFFF2B65E)
// Cards, the navigation bar and segmented-control tracks sit on the DayMist / IshaNight background.
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF15221F)
private val TrackLight = Color(0xFFE3EBE8)
private val TrackDark = Color(0xFF22312E)
private val TipLight = Color(0xFFFBEBD0)
private val OnTipLight = Color(0xFF4A3208)
private val TipDark = Color(0xFF3A2A10)
private val OnTipDark = Color(0xFFF7DDB0)

internal val LightColors = lightColorScheme(
    primary = NightTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBE8),
    onPrimaryContainer = Ink,
    secondary = DawnAmber,
    onSecondary = Ink,
    secondaryContainer = TipLight,
    onSecondaryContainer = OnTipLight,
    tertiary = HeroAmber,
    tertiaryContainer = HeroTeal,
    onTertiaryContainer = DayMist,
    background = DayMist,
    onBackground = Ink,
    surface = DayMist,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF4F5E5A),
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = TrackLight,
    surfaceContainerHighest = TrackLight,
    surfaceVariant = TrackLight,
    outline = Color(0xFF6E7F7B),
    outlineVariant = Color(0xFFDCE5E2),
    error = Color(0xFFA3261B),
)

internal val DarkColors = darkColorScheme(
    primary = TealGlow,
    onPrimary = IshaNight,
    primaryContainer = Color(0xFF1E3632),
    onPrimaryContainer = InkDark,
    secondary = DawnAmberDark,
    onSecondary = IshaNight,
    secondaryContainer = TipDark,
    onSecondaryContainer = OnTipDark,
    tertiary = HeroAmber,
    tertiaryContainer = HeroTeal,
    onTertiaryContainer = DayMist,
    background = IshaNight,
    onBackground = InkDark,
    surface = IshaNight,
    onSurface = InkDark,
    onSurfaceVariant = Color(0xFFA8B7B3),
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = TrackDark,
    surfaceContainerHighest = TrackDark,
    surfaceVariant = TrackDark,
    outline = Color(0xFF8A9B97),
    outlineVariant = Color(0xFF263733),
    error = Color(0xFFFFB4AB),
)

private fun variableFont(resId: Int, weight: FontWeight) =
    Font(resId, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Headings and large times. Bundled variable font (wght 100–900), OFL. */
val NotoKufiArabic = FontFamily(
    variableFont(R.font.noto_kufi_arabic, FontWeight.Normal),
    variableFont(R.font.noto_kufi_arabic, FontWeight.Medium),
    variableFont(R.font.noto_kufi_arabic, FontWeight.SemiBold),
    variableFont(R.font.noto_kufi_arabic, FontWeight.Bold),
)

/** Running text and controls. Bundled variable font (wght 400–700, subset to Arabic and Latin), OFL. */
val NotoSansArabic = FontFamily(
    variableFont(R.font.noto_sans_arabic, FontWeight.Normal),
    variableFont(R.font.noto_sans_arabic, FontWeight.Medium),
    variableFont(R.font.noto_sans_arabic, FontWeight.SemiBold),
    variableFont(R.font.noto_sans_arabic, FontWeight.Bold),
)

private fun TextStyle.kufi() = copy(fontFamily = NotoKufiArabic)
private fun TextStyle.sans() = copy(fontFamily = NotoSansArabic)

/** Display, headline and large title styles use Kufi; row titles, body and label styles use Noto Sans Arabic. */
private val MawaqeetTypography = Typography().run {
    copy(
        displayLarge = displayLarge.kufi(),
        displayMedium = displayMedium.kufi(),
        displaySmall = displaySmall.kufi(),
        headlineLarge = headlineLarge.kufi(),
        headlineMedium = headlineMedium.kufi(),
        headlineSmall = headlineSmall.kufi(),
        titleLarge = titleLarge.kufi(),
        titleMedium = titleMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.sans(),
        bodyMedium = bodyMedium.sans(),
        bodySmall = bodySmall.sans(),
        labelLarge = labelLarge.sans(),
        labelMedium = labelMedium.sans(),
        labelSmall = labelSmall.sans(),
    )
}

/** Settings → Language and display → Theme (1.4): follow the phone, or always light or dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The "A Day That Breathes" surfaces (design-system.md §2): what sits on the living sky. Cards are translucent so the
 * sky shows through ([surface]); the current timeline stop, sheets and the tab bar are near-solid ([surfaceStrong],
 * [bar]); hairlines are [line]. [amber] is the next-prayer accent (arc, sun, current dot).
 */
@Immutable
data class Skin(
    val dark: Boolean,
    val sky: List<Color>,
    val surface: Color,
    val surfaceStrong: Color,
    val bar: Color,
    val line: Color,
    val accentSoft: Color,
    val track: Color,
    val amber: Color,
    /** A prayed mark's fill (teal by day, mint at night) and the tick on it. */
    val mark: Color,
    val onMark: Color,
) {
    companion object {
        val Light = Skin(
            dark = false,
            sky = listOf(Color(0xFFF6E7CE), DayMist, Color(0xFFCFE6E1)),
            surface = Color.White.copy(alpha = 0.72f),
            surfaceStrong = Color.White.copy(alpha = 0.94f),
            bar = Color(0xFFFBFDFC),
            line = Ink.copy(alpha = 0.12f),
            accentSoft = NightTeal.copy(alpha = 0.12f),
            track = Ink.copy(alpha = 0.14f),
            amber = DawnAmber,
            mark = NightTeal,
            onMark = Color.White,
        )
        val Dark = Skin(
            dark = true,
            sky = listOf(Color(0xFF123B36), IshaNight, Color(0xFF1B4B45)),
            surface = Color(0xFF182C29).copy(alpha = 0.6f),
            surfaceStrong = Color(0xFF142624).copy(alpha = 0.92f),
            bar = Color(0xFF12201E),
            line = Color.White.copy(alpha = 0.12f),
            accentSoft = TealGlow.copy(alpha = 0.14f),
            track = Color.White.copy(alpha = 0.16f),
            amber = DawnAmberDark,
            mark = TealGlow,
            onMark = IshaNight,
        )
    }
}

/** The current [Skin]; [MawaqeetTheme] provides it. */
val LocalSkin = staticCompositionLocalOf { Skin.Light }

/** Whether [mode] is dark on a phone whose own setting is [systemDark]. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** The app's look: brand palette in light or dark ([darkTheme]), the design's [Skin], and the bundled Arabic fonts. */
@Composable
fun MawaqeetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSkin provides if (darkTheme) Skin.Dark else Skin.Light) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MawaqeetTypography,
            content = content,
        )
    }
}
