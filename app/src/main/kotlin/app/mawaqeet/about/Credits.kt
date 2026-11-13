package app.mawaqeet.about

import androidx.annotation.StringRes
import app.mawaqeet.R

/** What a [Credit] is: the About screen lists them in this order. */
enum class CreditKind(@param:StringRes val title: Int) {
    LIBRARY(R.string.about_libraries),
    DATASET(R.string.about_datasets),
    TEXT(R.string.about_texts),
    FONT(R.string.about_fonts),
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
    /** The name in the app's language, for a work with an Arabic title (a book), instead of [name]. */
    @param:StringRes val nameRes: Int? = null,
    /** The licence line in the app's language when it is a description rather than a licence's proper name. */
    @param:StringRes val licenceRes: Int? = null,
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
            "kotlinx.serialization (JetBrains)", CreditKind.LIBRARY, "Apache 2.0",
            "https://github.com/Kotlin/kotlinx.serialization", R.string.about_use_serialization,
            modules = listOf("org.jetbrains.kotlinx:kotlinx-serialization"),
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
        // The compilation is not openly licensed (#72): the texts are taken from the hadith collections it cites,
        // and the book is credited for the selection, order and counts.
        Credit(
            "Hisn al-Muslim (Sa'id ibn Ali ibn Wahf al-Qahtani)", CreditKind.TEXT, "Hadith texts from their primary sources",
            "https://sunnah.com/hisn", R.string.about_use_hisn,
            nameRes = R.string.about_name_hisn, licenceRes = R.string.about_licence_hisn,
        ),
        Credit(
            "Noto Kufi Arabic (Google)", CreditKind.FONT, "SIL Open Font License 1.1",
            "https://fonts.google.com/noto/specimen/Noto+Kufi+Arabic", R.string.about_use_kufi,
        ),
        Credit(
            "Noto Sans Arabic (Google)", CreditKind.FONT, "SIL Open Font License 1.1",
            "https://fonts.google.com/noto/specimen/Noto+Sans+Arabic", R.string.about_use_sans,
        ),
    )

    /** The credits of [kind], in list order. The adhan recordings are deliberately not credited, so there is no kind for them. */
    fun of(kind: CreditKind) = all.filter { it.kind == kind }
}
