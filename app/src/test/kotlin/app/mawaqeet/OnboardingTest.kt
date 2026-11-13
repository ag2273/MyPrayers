package app.mawaqeet

import android.content.pm.PackageManager
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.fixalerts.FixAlerts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowBuild

/** First run (#29) on Android 14+: exact alarms start denied, as they do on a fresh install there. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OnboardingTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule(exactAlarms = false, onboarded = false)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val store get() = (compose.activity.application as MawaqeetApplication).settings

    /** The app's DataStore and AppCompat's language outlive a test in this JVM, so put back what these tests change. */
    @After
    fun restore() = runBlocking {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        store.clearLocation()
        store.setCalculationMethod(PrayerSettings.Default.method)
        store.setAsrConvention(PrayerSettings.Default.asrConvention)
        store.setFixAlertsDone(false)
        store.setOnboardingDone(true)
    }

    private fun waitForText(text: String) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** The user grants exact alarms in system settings and comes back to the app. */
    private fun grantExactAlarmsAndReturn() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    /** Welcome → refuse approximate location → search Cairo → Egypt's suggestion, applied. */
    private fun walkToPermissions() {
        compose.onNodeWithText("ابدأ").performClick()
        compose.onNodeWithText("ابحث عن مدينتي بدلًا من ذلك").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("جارٍ تحميل قائمة المدن…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNode(hasSetTextAction() and hasText("ابحث عن مدينتك")).performTextInput("القاهرة")
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("القاهرة").fetchSemanticsNodes().size > 1 }
        compose.onAllNodesWithText("القاهرة")[1].performClick()
        waitForText("لأنك في مصر")
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.settings.first() }.method == CalculationMethod.EGYPTIAN }
        waitForText("مقترحة لمصر")
        compose.onNodeWithText("متابعة").performClick()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `a fresh install walks through every step in Arabic, right to left, and city search replaces a refused location`() {
        compose.onNodeWithText("ابدأ").assertIsDisplayed()
        assertEquals(View.LAYOUT_DIRECTION_RTL, compose.activity.window.decorView.layoutDirection)

        walkToPermissions()
        assertEquals("Cairo", runBlocking { store.savedLocation.first() }?.city?.name)

        // Both permissions on one screen: notifications are on in this test device, exact alarms are not.
        compose.onNodeWithText("ليصلك الأذان في وقته").assertIsDisplayed()
        compose.onNodeWithText("✓ تم").assertIsDisplayed()
        compose.onNodeWithText("متابعة").assertIsNotEnabled()
        compose.onNodeWithText("السماح").performClick()
        // "Don't allow" in system settings: the button becomes Grant, and setup still can't finish.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("منح الإذن").assertIsDisplayed()
        compose.onNodeWithText("متابعة").assertIsNotEnabled()
        assertFalse(runBlocking { store.onboardingDone.first() })

        grantExactAlarmsAndReturn()
        compose.onNodeWithText("متابعة").assertIsEnabled().performClick()

        // Not an alarm-killing brand: no battery step, straight to Home with working Prayer Times.
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.onboardingDone.first() } }
        waitForText("حتى الأذان")
    }

    @Test
    @Config(qualifiers = "en")
    fun `onboarding opens in Arabic even on an English phone, and switching to English applies at once`() {
        // Robolectric doesn't recreate the activity when the app language changes, so check the stored choice.
        compose.waitUntil(timeoutMillis = 10_000) { AppCompatDelegate.getApplicationLocales().toLanguageTags() == "ar" }

        compose.onNodeWithText("English").performClick()
        compose.waitForIdle()

        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    @Config(qualifiers = "ar")
    fun `on an alarm-killing brand a skippable battery step follows, and Home then shows the Fix alerts tip`() {
        ShadowBuild.setManufacturer("samsung")
        walkToPermissions()
        grantExactAlarmsAndReturn()
        compose.onNodeWithText("متابعة").performClick()

        compose.onNodeWithText("لا تدع الهاتف يوقف التنبيهات").assertIsDisplayed()
        compose.onNodeWithText("سامسونج", substring = true).assertIsDisplayed()
        compose.onNodeWithText("تخطٍّ").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.onboardingDone.first() } }
        waitForText("قد يؤخر هاتفك تنبيهات الأذان")
        compose.onNodeWithText("تم").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.fixAlertsDone.first() } }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("قد يؤخر هاتفك تنبيهات الأذان", substring = true).fetchSemanticsNodes().isEmpty()
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en")
class FixAlertsTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val store get() = (compose.activity.application as MawaqeetApplication).settings

    @After
    fun restore() = runBlocking { store.setFixAlertsDone(false) }

    @Test
    fun `Fix alerts is reachable from Settings and its Done is remembered`() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Fix alerts").performScrollTo().performClick()

        compose.onNodeWithText("Open battery optimisation settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.fixAlertsDone.first() } }
        compose.onNodeWithText("Fix alerts").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `phones that keep alarms get no Home tip`() {
        compose.onNodeWithText("until the adhan").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Your phone may delay", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the app never asks for a battery-optimisation exemption`() {
        val forbidden = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        val context = compose.activity
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains(forbidden))
        listOf(FixAlerts.appSettingsIntent(context), FixAlerts.batteryOptimisationListIntent()).forEach {
            assertNotEquals(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, it.action)
        }
        // Nor anywhere in the app's sources (the unit tests run from the app module's directory).
        val hits = java.io.File("src/main").walkTopDown()
            .filter { it.isFile && it.readText().contains("IGNORE_BATTERY_OPTIMIZATIONS\"") }
            .toList()
        assertTrue("$hits", hits.isEmpty())
        val sources = java.io.File("src/main").walkTopDown().filter { it.isFile }.toList()
        assertTrue(sources.none { it.readText().contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS") })
    }
}
