package app.mawaqeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.mawaqeet.R

// Brand palette (spec #14 "Visual identity"): light / dark pairs.
private val NightTeal = Color(0xFF1F6F66)
private val TealGlow = Color(0xFF7FC9BC)
private val DawnAmber = Color(0xFFE0A246)
private val DawnAmberDark = Color(0xFFF2B65E)
private val DayMist = Color(0xFFF2F6F4)
private val IshaNight = Color(0xFF111615)
private val Ink = Color(0xFF142421)
private val InkDark = Color(0xFFE4EEEB)
// The Next Prayer card (redesign): a deep teal card that is the same in light and dark, with amber countdown.
private val HeroTeal = Color(0xFF134A44)
private val HeroAmber = Color(0xFFF2B65E)
// Cards, the navigation bar and segmented-control tracks sit on the DayMist / IshaNight background.
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF181E1D)
private val TrackLight = Color(0xFFE3EBE8)
private val TrackDark = Color(0xFF212927)
private val TipLight = Color(0xFFFBEBD0)
private val OnTipLight = Color(0xFF4A3208)
private val TipDark = Color(0xFF3A2A10)
private val OnTipDark = Color(0xFFF7DDB0)

internal val LightColors = lightColorScheme(
    primary = NightTeal,
    onPrimary = Color.White,
    // A neutral surface step (the selected tab, today's row, the next Prayer's row): selection without colour.
    primaryContainer = Color(0xFFE8EEEB),
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
    outlineVariant = Color(0xFFE3E8E6),
    error = Color(0xFFA3261B),
)

internal val DarkColors = darkColorScheme(
    primary = TealGlow,
    onPrimary = IshaNight,
    primaryContainer = Color(0xFF242C2A),
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
    onSurfaceVariant = Color(0xFFA3B0AD),
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = TrackDark,
    surfaceContainerHighest = TrackDark,
    surfaceVariant = TrackDark,
    outline = Color(0xFF8A9B97),
    outlineVariant = Color(0xFF262E2C),
    error = Color(0xFFFFB4AB),
)

/**
 * The gold that means "the next Prayer", and nothing else (unslop pass, 2026-11-10): its countdown, the dot on its row
 * and its tick on the sky arc. A deeper shade on light surfaces so it still reads.
 */
val ColorScheme.nextPrayerGold: Color get() = if (background.luminance() > 0.5f) Color(0xFFC98A2E) else HeroAmber

/** The gold on the deep Next Prayer surface, the same in light and dark. */
val HeroGold: Color = HeroAmber

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
        titleMedium = titleMedium.sans().copy(fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.sans().copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.sans(),
        bodyMedium = bodyMedium.sans(),
        bodySmall = bodySmall.sans(),
        labelLarge = labelLarge.sans(),
        labelMedium = labelMedium.sans(),
        labelSmall = labelSmall.sans(),
    )
}

/** The app's look: brand palette following the system light/dark mode, and the bundled Arabic fonts. */
@Composable
fun MawaqeetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MawaqeetTypography,
        content = content,
    )
}
