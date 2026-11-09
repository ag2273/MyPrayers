package app.mawaqeet.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
 * else the context's locale. Read [digits] from `SettingsRepository.digitStyle`; pass `SettingsRepository.appLanguage`
 * as [storedLanguage] wherever this may run in a process no activity has run in (widgets, receivers).
 */
fun displayFormatter(context: Context, digits: DigitStyle, storedLanguage: String = ""): DisplayFormatter =
    DisplayFormatter(appLocale(context, storedLanguage), digits)

/** [appLocale] for this process: AppCompat's per-app language, else [storedLanguage], else the context's locale. */
fun appLocale(context: Context, storedLanguage: String): Locale = appLocale(
    AppCompatDelegate.getApplicationLocales(),
    storedLanguage,
    context.resources.configuration.locales[0],
)

/**
 * The app language. [applicationLocales] (AppCompat) is authoritative when set, but on Android 8–12 it is empty in a
 * fresh widget or receiver process until an activity runs, so [storedTag] (`SettingsRepository.appLanguage`) comes
 * next; "" means follow [system].
 */
fun appLocale(applicationLocales: LocaleListCompat, storedTag: String, system: Locale): Locale =
    applicationLocales[0]
        ?: storedTag.takeIf { it.isNotEmpty() }?.let { LocaleListCompat.forLanguageTags(it)[0] }
        ?: system

/**
 * [context] with its resources in [locale], for strings and layout direction outside an activity: on Android 8–12 the
 * application context keeps the system language even when the user chose another.
 */
fun localizedContext(context: Context, locale: Locale): Context {
    val config = Configuration(context.resources.configuration)
    config.setLocales(LocaleList(locale))
    return context.createConfigurationContext(config)
}
