package app.mawaqeet

import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
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
}
