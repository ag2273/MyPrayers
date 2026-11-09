package app.mawaqeet

import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    private fun assertGroupsShown(vararg titles: String) = titles.forEach {
        compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `Settings shows its six groups in Arabic, right to left`() {
        openSettings("الإعدادات")

        assertGroupsShown("مواقيت الصلاة", "التنبيهات", "سجل الصلاة", "اللغة والعرض", "أدوات الشاشة الرئيسية", "حول التطبيق")
        assertEquals(View.LAYOUT_DIRECTION_RTL, compose.activity.window.decorView.layoutDirection)
    }

    @Test
    @Config(qualifiers = "en")
    fun `Settings shows its six groups in English, left to right`() {
        openSettings("Settings")

        assertGroupsShown("Prayer Times", "Alerts", "Prayer Log", "Language & display", "Widgets", "About")
        assertEquals(View.LAYOUT_DIRECTION_LTR, compose.activity.window.decorView.layoutDirection)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `choosing a Calculation Method and Asr Convention stores them`() {
        openSettings("الإعدادات")

        compose.onNodeWithText("طريقة الحساب").performClick()
        compose.onNodeWithText("الهيئة المصرية العامة للمساحة").performScrollTo().performClick()
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
        val store = (compose.activity.application as MawaqeetApplication).settings

        compose.onNodeWithText("أرقام غربية").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.digitStyle.first() } == DigitStyle.WESTERN }
        compose.onNodeWithText("تعديل التاريخ الهجري").performScrollTo().performClick()
        compose.onNodeWithText("-1", substring = true).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.settings.first() }.hijriOffsetDays == -1 }
    }

    @Test
    @Config(qualifiers = "en")
    fun `choosing Fajr's Full Adhan, a 10-minute reminder and no vibration stores them`() {
        openSettings("Settings")
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun alerts() = runBlocking { store.settings.first() }.alerts
        try {
            runBlocking { store.setDigitStyle(DigitStyle.WESTERN) }
            compose.onNodeWithText("Fajr").performScrollTo().performClick()
            compose.onNodeWithText("Fajr's Full Adhan", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("Prayer Reminder").performScrollTo().performClick()
            // The lead-time choices show once the DataStore write comes back (async).
            compose.waitUntil(timeoutMillis = 5_000) { alerts().of(Prayer.FAJR).reminder }
            compose.waitForIdle()
            compose.onNodeWithText("10 minutes before").performScrollTo().performClick()
            compose.onNodeWithText("Done").performClick()
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
    @Config(qualifiers = "ar")
    fun `choosing English switches the app language`() {
        openSettings("الإعدادات")

        compose.onNodeWithText("English").performScrollTo().performClick()

        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
}
