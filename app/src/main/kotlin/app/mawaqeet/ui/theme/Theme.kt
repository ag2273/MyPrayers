package app.mawaqeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
private val IshaNight = Color(0xFF0E1917)
private val Ink = Color(0xFF142421)
private val InkDark = Color(0xFFE4EEEB)
// The countdown on the Next Prayer card (#33): amber that keeps 4.5:1 on the card's teal in both themes.
private val CountdownAmber = Color(0xFFFFE0A3)
private val CountdownAmberDark = Color(0xFF5C3B00)

internal val LightColors = lightColorScheme(
    primary = NightTeal,
    onPrimary = DayMist,
    primaryContainer = NightTeal.copy(alpha = 0.12f).compositeOver(DayMist),
    onPrimaryContainer = Ink,
    secondary = DawnAmber,
    onSecondary = Ink,
    tertiary = CountdownAmber,
    background = DayMist,
    onBackground = Ink,
    surface = DayMist,
    onSurface = Ink,
    onSurfaceVariant = Ink.copy(alpha = 0.72f).compositeOver(DayMist),
)

internal val DarkColors = darkColorScheme(
    primary = TealGlow,
    onPrimary = IshaNight,
    primaryContainer = TealGlow.copy(alpha = 0.16f).compositeOver(IshaNight),
    onPrimaryContainer = InkDark,
    secondary = DawnAmberDark,
    onSecondary = IshaNight,
    tertiary = CountdownAmberDark,
    background = IshaNight,
    onBackground = InkDark,
    surface = IshaNight,
    onSurface = InkDark,
    onSurfaceVariant = InkDark.copy(alpha = 0.72f).compositeOver(IshaNight),
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

/** Running text. Bundled variable font (wght 400–700), OFL. */
val NotoNaskhArabic = FontFamily(
    variableFont(R.font.noto_naskh_arabic, FontWeight.Normal),
    variableFont(R.font.noto_naskh_arabic, FontWeight.Medium),
    variableFont(R.font.noto_naskh_arabic, FontWeight.SemiBold),
    variableFont(R.font.noto_naskh_arabic, FontWeight.Bold),
)

private fun TextStyle.kufi() = copy(fontFamily = NotoKufiArabic)
private fun TextStyle.naskh() = copy(fontFamily = NotoNaskhArabic)

/** Display, headline and title styles use Kufi; body and label styles use Naskh. */
private val MawaqeetTypography = Typography().run {
    copy(
        displayLarge = displayLarge.kufi(),
        displayMedium = displayMedium.kufi(),
        displaySmall = displaySmall.kufi(),
        headlineLarge = headlineLarge.kufi(),
        headlineMedium = headlineMedium.kufi(),
        headlineSmall = headlineSmall.kufi(),
        titleLarge = titleLarge.kufi(),
        titleMedium = titleMedium.kufi(),
        titleSmall = titleSmall.kufi(),
        bodyLarge = bodyLarge.naskh(),
        bodyMedium = bodyMedium.naskh(),
        bodySmall = bodySmall.naskh(),
        labelLarge = labelLarge.naskh(),
        labelMedium = labelMedium.naskh(),
        labelSmall = labelSmall.naskh(),
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
