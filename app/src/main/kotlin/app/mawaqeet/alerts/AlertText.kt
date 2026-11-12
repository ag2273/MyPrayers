package app.mawaqeet.alerts

import android.content.Context
import app.mawaqeet.ui.appLocale
import app.mawaqeet.ui.localizedContext

/**
 * [context] with its resources in the app's language, for a notification's text and its channel's name. A receiver or
 * service process can keep the phone's language (seen on Android 17 with the app in Arabic and the phone in English),
 * so the language stored with the plan ([AlertPlanStore.readStyle]) decides, as for the widgets. [language] skips the
 * read when the caller has it.
 */
internal fun alertText(context: Context, language: String? = null): Context =
    localizedContext(context, appLocale(context, language ?: AlertPlanStore(context).readStyle().language))
