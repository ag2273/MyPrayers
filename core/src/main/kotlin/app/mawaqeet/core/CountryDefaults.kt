package app.mawaqeet.core

/**
 * The Calculation Method and Asr Convention suggested for a country, offered after the user picks a location.
 * [countryCode] (ISO 3166-1 alpha-2, upper case) is the reason shown with it ("because you are in Egypt").
 */
data class MethodSuggestion(
    val countryCode: String,
    val method: CalculationMethod,
    val asrConvention: AsrConvention,
)

/**
 * Country → suggested Calculation Method and Asr Convention: our own table, recorded in
 * docs/adr/0003-country-defaults.md. Change the ADR and this table together.
 */
object CountryDefaults {

    fun suggestionFor(countryCode: String): MethodSuggestion {
        val country = countryCode.uppercase()
        val method = METHODS.entries.firstOrNull { country in it.value }?.key ?: CalculationMethod.MUSLIM_WORLD_LEAGUE
        val asr = if (country in HANAFI) AsrConvention.HANAFI else AsrConvention.STANDARD
        return MethodSuggestion(country, method, asr)
    }

    private val AFRICA = setOf(
        "DZ", "AO", "BJ", "BW", "BF", "BI", "CV", "CM", "CF", "TD", "KM", "CD", "CG", "CI", "DJ", "EG", "GQ", "ER",
        "SZ", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR", "LY", "MG", "MW", "ML", "MR", "MU", "YT", "MA",
        "MZ", "NA", "NE", "NG", "RE", "RW", "SH", "ST", "SN", "SC", "SL", "SO", "ZA", "SS", "SD", "TZ", "TG", "TN",
        "UG", "EH", "ZM", "ZW",
    )

    private val METHODS: Map<CalculationMethod, Set<String>> = mapOf(
        CalculationMethod.UMM_AL_QURA to setOf("SA"),
        CalculationMethod.DUBAI to setOf("AE"),
        CalculationMethod.KUWAIT to setOf("KW"),
        CalculationMethod.QATAR to setOf("QA"),
        CalculationMethod.EGYPTIAN to AFRICA + setOf("SY", "LB", "JO", "PS"),
        CalculationMethod.KARACHI to setOf("PK", "IN", "BD", "AF"),
        CalculationMethod.TURKEY to setOf("TR"),
        CalculationMethod.SINGAPORE to setOf("SG", "MY", "ID"),
        CalculationMethod.MOON_SIGHTING_COMMITTEE to setOf("US", "CA", "GB"),
    )

    /** Hanafi Asr by default: South Asia, Afghanistan, Türkiye and Central Asia. Everyone else: Standard. */
    private val HANAFI = setOf("PK", "IN", "BD", "AF", "TR", "KZ", "UZ", "TM", "KG", "TJ")
}
