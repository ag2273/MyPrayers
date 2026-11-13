package app.mawaqeet.weather

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import app.mawaqeet.R

/**
 * What a tap on a widget's weather opens (1.4): the phone's own weather app. Android has no standard "weather" intent,
 * so the makers' weather apps are tried in turn (each is listed under `<queries>` in the manifest), then a web search
 * for the weather, which the Google app answers with its weather page. Null when nothing can open it: the tap then
 * opens Mawaqeet, as the rest of the widget does.
 */
object WeatherApp {
    /** Weather apps by maker, most common first. */
    internal val PACKAGES = listOf(
        "com.sec.android.daemonapp", // Samsung
        "com.google.android.apps.weather", // Pixel Weather
        "com.miui.weather2", // Xiaomi, Redmi, POCO
        "com.huawei.android.totemweather", // Huawei
        "com.hihonor.android.totemweather", // Honor
        "com.oplus.weather.service", // OPPO, realme, OnePlus (ColorOS)
        "com.coloros.weather2",
        "com.coloros.weather.service",
        "net.oneplus.weather",
        "com.vivo.weather", // vivo
        "com.transsion.weather", // Tecno, Infinix, itel
        "com.motorola.timeweatherwidget", // Motorola
        "com.asus.weathertime", // ASUS
        "com.sonymobile.xperiaweather", // Sony
        "com.lge.sizechangable.weather", // LG
    )

    fun intent(context: Context): Intent? {
        val packages = context.packageManager
        PACKAGES.firstNotNullOfOrNull { packages.getLaunchIntentForPackage(it) }?.let {
            return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val search = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, context.getString(R.string.weather_search_query))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return search.takeIf { it.resolveActivity(packages) != null }
    }
}
