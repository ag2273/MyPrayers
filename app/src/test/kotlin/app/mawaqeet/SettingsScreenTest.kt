package app.mawaqeet


import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.HighLatitudeRule
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsScreenTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun openSettings(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    /** Opens a Settings section's page from the hub. */
    private fun openPage(title: String) {
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun assertGroupsShown(vararg titles: String) = titles.forEach {
        compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `Settings shows its six sections in Arabic, right to left`() {
        openSettings("الإعدادات")

        assertGroupsShown("التنبيهات جاهزة", "الموقع وطريقة الحساب", "التنبيهات والأذان", "اللغة والعرض", "أدوات الشاشة الرئيسية", "إصلاح التنبيهات", "حول مواقيت وبياناتك")
        assertEquals(View.LAYOUT_DIRECTION_RTL, compose.activity.window.decorView.layoutDirection)
    }

    @Test
    @Config(qualifiers = "en")
    fun `Settings shows its six sections in English, left to right`() {
        openSettings("Settings")

        assertGroupsShown("Alerts are ready", "Location & calculation", "Alerts & adhan", "Language & display", "Widgets", "Fix alerts", "About & your data")
        assertEquals(View.LAYOUT_DIRECTION_LTR, compose.activity.window.decorView.layoutDirection)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `choosing a Calculation Method and Asr Convention stores them`() {
        openSettings("الإعدادات")
        openPage("الموقع وطريقة الحساب")

        compose.onNodeWithText("طريقة الحساب").performClick()
        compose.onNodeWithText("الهيئة المصرية العامة للمساحة").performScrollTo().performClick()
        // Choosing a method returns to the section, where the Asr Convention is a segmented choice.
        compose.onNodeWithText("الحنفي").performScrollTo().performClick()

        // DataStore writes off the main thread, so wait for the stored value rather than for Compose to be idle.
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun stored() = runBlocking { store.settings.first() }
        compose.waitUntil(timeoutMillis = 5_000) { stored().asrConvention == AsrConvention.HANAFI }
        assertEquals(CalculationMethod.EGYPTIAN, stored().method)
        assertEquals(AsrConvention.HANAFI, stored().asrConvention)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `choosing Western digits and a Hijri offset stores them`() {
        openSettings("الإعدادات")
        openPage("اللغة والعرض")
        val store = (compose.activity.application as MawaqeetApplication).settings

        compose.onNodeWithText("أرقام غربية").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.digitStyle.first() } == DigitStyle.WESTERN }
        compose.onNodeWithContentDescription("إنقاص يوم من التاريخ الهجري").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.settings.first() }.hijriOffsetDays == -1 }
    }

    @Test
    @Config(qualifiers = "en")
    fun `choosing Fajr's Full Adhan, a 10-minute reminder and no vibration stores them`() {
        openSettings("Settings")
        openPage("Alerts & adhan")
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun alerts() = runBlocking { store.settings.first() }.alerts
        try {
            runBlocking { store.setDigitStyle(DigitStyle.WESTERN) }
            compose.onNodeWithText("Fajr").performScrollTo().performClick()
            compose.onNodeWithText("Fajr's Full Adhan", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("Prayer Reminder").performScrollTo().performClick()
            // The lead-time choices show once the DataStore write comes back (async).
            compose.waitUntil(timeoutMillis = 5_000) {
                alerts().of(Prayer.FAJR).reminder && compose.onAllNodesWithText("10 minutes before").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("10 minutes before").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Vibration").performScrollTo().performClick()

            compose.waitUntil(timeoutMillis = 5_000) { !alerts().vibration && alerts().of(Prayer.FAJR).reminderMinutes == 10 }
            assertEquals(PrayerAlertSettings(sound = AdhanSoundChoice.FULL, reminder = true, reminderMinutes = 10), alerts().of(Prayer.FAJR))
        } finally {
            // The app's DataStore outlives the test in this JVM.
            runBlocking {
                store.setPrayerAlert(Prayer.FAJR, PrayerAlertSettings())
                store.setVibration(true)
                store.setDigitStyle(DigitStyle.AUTOMATIC)
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `adjusting Fajr and choosing a high-latitude rule stores them`() {
        openSettings("Settings")
        openPage("Location & calculation")
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun stored() = runBlocking { store.settings.first() }
        try {
            // Two quick taps both count, even before the first one is stored.
            compose.onNodeWithContentDescription("Fajr one minute later").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Fajr one minute later").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { stored().adjustmentOf(Prayer.FAJR) == 2 }
            compose.onNodeWithText("High-latitude rule").performScrollTo().performClick()
            compose.onNodeWithText("Middle of the night").performScrollTo().performClick()

            compose.waitUntil(timeoutMillis = 5_000) { stored().highLatitudeRule == HighLatitudeRule.MIDDLE_OF_THE_NIGHT }
            assertEquals(mapOf(Prayer.FAJR to 2), stored().adjustments)
        } finally {
            runBlocking {
                store.setAdjustment(Prayer.FAJR, 0)
                store.setHighLatitudeRule(HighLatitudeRule.AUTOMATIC)
            }
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `choosing English switches the app language`() {
        openSettings("الإعدادات")
        openPage("اللغة والعرض")

        compose.onNodeWithText("English").performScrollTo().performClick()

        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    @Config(qualifiers = "en")
    fun `choosing app colours for the widgets stores it, and transparency reads as a percentage`() {
        openSettings("Settings")
        openPage("Widgets")
        val store = (compose.activity.application as MawaqeetApplication).settings

        compose.onNodeWithText("Background transparency").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("App colours").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { store.widgetAppearance.first() }.colors == app.mawaqeet.widget.WidgetColors.APP
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `About shows the version and credits each library, dataset and font with its licence`() {
        openSettings("الإعدادات")
        openPage("حول مواقيت وبياناتك")

        compose.onNodeWithText("الإصدار ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("الخصوصية").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("حذف سجل الصلاة").performScrollTo().assertIsDisplayed()
        // The sources open the full list with each licence.
        compose.onNodeWithText("المكتبات").performScrollTo().performClick()
        for (name in listOf("adhan-kotlin (Batoul Apps)", "GeoNames", "Natural Earth", "Noto Sans Arabic (Google)")) {
            compose.onNodeWithContentDescription(name, substring = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("GeoNames: أسماء المدن وإحداثياتها ومناطقها الزمنية، الترخيص: CC BY 4.0. يفتح الموقع")
            .performScrollTo().assertIsDisplayed()
    }
}
