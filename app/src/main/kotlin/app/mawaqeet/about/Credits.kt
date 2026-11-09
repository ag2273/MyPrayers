package app.mawaqeet.about

import androidx.annotation.StringRes
import app.mawaqeet.R

/** What a [Credit] is: the About screen lists them in this order. */
enum class CreditKind(@param:StringRes val title: Int) {
    LIBRARY(R.string.about_libraries),
    DATASET(R.string.about_datasets),
    FONT(R.string.about_fonts),
    RECORDING(R.string.about_recordings),
}

/**
 * One thing the app is built from or ships, with its licence (#33). Names and licences are proper names, so they are
 * not translated; [use] says what the app takes from it.
 */
data class Credit(
    val name: String,
    val kind: CreditKind,
    val licence: String,
    val url: String,
    @param:StringRes val use: Int,
    /** Maven groups (or `group:artifact`) this credit covers, so a test can check every shipped dependency is listed. */
    val modules: List<String> = emptyList(),
)

object Credits {
    val all: List<Credit> = listOf(
        Credit(
            "adhan-kotlin (Batoul Apps)", CreditKind.LIBRARY, "MIT",
            "https://github.com/batoulapps/adhan-kotlin", R.string.about_use_adhan,
            modules = listOf("com.batoulapps.adhan"),
        ),
        Credit(
            "kotlinx-datetime (JetBrains)", CreditKind.LIBRARY, "Apache 2.0",
            "https://github.com/Kotlin/kotlinx-datetime", R.string.about_use_datetime,
            modules = listOf("org.jetbrains.kotlinx:kotlinx-datetime"),
        ),
        Credit(
            "Android Jetpack (AndroidX): Core, AppCompat, Activity, Lifecycle, Compose, Material 3, Room, DataStore, Glance",
            CreditKind.LIBRARY, "Apache 2.0",
            "https://developer.android.com/jetpack/androidx", R.string.about_use_androidx,
            modules = listOf("androidx."),
        ),
        Credit(
            "GeoNames", CreditKind.DATASET, "CC BY 4.0",
            "https://www.geonames.org/", R.string.about_use_geonames,
        ),
        Credit(
            "Natural Earth", CreditKind.DATASET, "Public domain",
            "https://www.naturalearthdata.com/", R.string.about_use_natural_earth,
        ),
        Credit(
            "Noto Kufi Arabic (Google)", CreditKind.FONT, "SIL Open Font License 1.1",
            "https://fonts.google.com/noto/specimen/Noto+Kufi+Arabic", R.string.about_use_kufi,
        ),
        Credit(
            "Noto Naskh Arabic (Google)", CreditKind.FONT, "SIL Open Font License 1.1",
            "https://fonts.google.com/noto/specimen/Noto+Naskh+Arabic", R.string.about_use_naskh,
        ),
    )

    /** The credits of [kind], in list order. A kind with none (no adhan recording ships yet) shows a note instead. */
    fun of(kind: CreditKind) = all.filter { it.kind == kind }
}
