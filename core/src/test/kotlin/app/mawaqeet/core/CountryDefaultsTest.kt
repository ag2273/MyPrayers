package app.mawaqeet.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** Expected rows are ADR 0003's table (docs/adr/0003-country-defaults.md), the source of truth. */
class CountryDefaultsTest {

    private fun suggestion(country: String) = CountryDefaults.suggestionFor(country)

    @Test
    fun `Egypt is suggested the Egyptian General Authority of Survey with Standard Asr`() {
        assertEquals(MethodSuggestion("EG", CalculationMethod.EGYPTIAN, AsrConvention.STANDARD), suggestion("EG"))
    }

    @Test
    fun `Saudi Arabia is suggested Umm al-Qura with Standard Asr`() {
        assertEquals(MethodSuggestion("SA", CalculationMethod.UMM_AL_QURA, AsrConvention.STANDARD), suggestion("SA"))
    }

    @Test
    fun `South Asia is suggested Karachi with Hanafi Asr`() {
        for (country in listOf("PK", "IN", "BD", "AF")) {
            assertEquals(MethodSuggestion(country, CalculationMethod.KARACHI, AsrConvention.HANAFI), suggestion(country))
        }
    }

    @Test
    fun `Turkiye is suggested the Diyanet method with Hanafi Asr`() {
        assertEquals(MethodSuggestion("TR", CalculationMethod.TURKEY, AsrConvention.HANAFI), suggestion("TR"))
    }

    @Test
    fun `Central Asia is suggested Muslim World League with Hanafi Asr`() {
        for (country in listOf("KZ", "UZ", "TM", "KG", "TJ")) {
            assertEquals(
                MethodSuggestion(country, CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrConvention.HANAFI),
                suggestion(country),
            )
        }
    }

    @Test
    fun `Gulf states with their own method get it`() {
        assertEquals(CalculationMethod.DUBAI, suggestion("AE").method)
        assertEquals(CalculationMethod.KUWAIT, suggestion("KW").method)
        assertEquals(CalculationMethod.QATAR, suggestion("QA").method)
    }

    @Test
    fun `the rest of Africa and the Levant follow Egypt`() {
        for (country in listOf("MA", "SD", "NG", "SY", "LB", "JO", "PS")) {
            assertEquals(CalculationMethod.EGYPTIAN, suggestion(country).method, country)
            assertEquals(AsrConvention.STANDARD, suggestion(country).asrConvention, country)
        }
    }

    @Test
    fun `Singapore, Malaysia and Indonesia get the Singapore method`() {
        for (country in listOf("SG", "MY", "ID")) assertEquals(CalculationMethod.SINGAPORE, suggestion(country).method)
    }

    @Test
    fun `the US, Canada and the UK get the Moonsighting Committee method`() {
        for (country in listOf("US", "CA", "GB")) {
            assertEquals(CalculationMethod.MOON_SIGHTING_COMMITTEE, suggestion(country).method)
        }
    }

    @Test
    fun `any other country gets Muslim World League with Standard Asr`() {
        assertEquals(MethodSuggestion("FR", CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrConvention.STANDARD), suggestion("FR"))
    }

    @Test
    fun `country codes are matched case-insensitively`() {
        assertEquals(suggestion("PK"), suggestion("pk"))
    }
}
