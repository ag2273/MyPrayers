package app.mawaqeet.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import java.util.Locale

/**
 * The [DisplayFormatter] for the current app language and digit setting. Screens read it with
 * `LocalDisplayFormatter.current`; [ProvideDisplayFormatter] supplies it at the root of each composition.
 */
val LocalDisplayFormatter = staticCompositionLocalOf { DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.AUTOMATIC) }

/** Supplies the formatter for the composition's locale (which follows the per-app language) and [digits]. */
@Composable
fun ProvideDisplayFormatter(digits: DigitStyle, content: @Composable () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale, digits) { DisplayFormatter(locale, digits) }
    CompositionLocalProvider(LocalDisplayFormatter provides formatter, content = content)
}

/**
 * The formatter outside Compose screens (notifications, widgets, receivers): the per-app language if one was chosen,
 * else the context's locale. Read [digits] from `SettingsRepository.digitStyle`.
 */
fun displayFormatter(context: Context, digits: DigitStyle): DisplayFormatter {
    val locale = AppCompatDelegate.getApplicationLocales()[0] ?: context.resources.configuration.locales[0]
    return DisplayFormatter(locale, digits)
}
