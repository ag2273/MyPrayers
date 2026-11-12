package app.mawaqeet


import android.view.View
import android.app.NotificationManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.lifecycle.Lifecycle
import org.robolectric.Shadows.shadowOf
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
    fun `turning off all app alerts stores it, hides the per-Prayer settings and shows Alerts are off`() {
        openSettings("Settings")
        openPage("Alerts & adhan")
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun alerts() = runBlocking { store.settings.first() }.alerts
        try {
            compose.onNodeWithText("All app alerts").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !alerts().enabled }
            compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Vibration").fetchSemanticsNodes().isEmpty() }
            // Prayer Silence doesn't depend on the master switch.
            compose.onNodeWithText("Silent during Prayer").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Alerts are off").assertIsDisplayed()
        } finally {
            runBlocking { store.setAlertsEnabled(true) }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `turning on Silent during Prayer asks for Do Not Disturb access, stays off without it and offers Grant until it is given`() {
        val policy = shadowOf(compose.activity.getSystemService(NotificationManager::class.java))
        policy.setNotificationPolicyAccessGranted(false)
        openSettings("Settings")
        openPage("Alerts & adhan")
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun alerts() = runBlocking { store.settings.first() }.alerts
        fun grantShown() = compose.onAllNodesWithText("Grant").fetchSemanticsNodes().isNotEmpty()
        try {
            compose.onNodeWithText("Silent during Prayer").performScrollTo().assertIsOff()
            assertEquals(false, grantShown())

            compose.onNodeWithText("Silent during Prayer").performClick()

            assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, shadowOf(compose.activity).nextStartedActivity?.action)
            compose.waitUntil(timeoutMillis = 5_000) { alerts().prayerSilence.enabled && grantShown() }
            compose.onNodeWithText("Silent during Prayer").assertIsOff()

            // The user allows access in system settings and comes back.
            policy.setNotificationPolicyAccessGranted(true)
            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

            compose.waitUntil(timeoutMillis = 5_000) { !grantShown() }
            compose.onNodeWithText("Silent during Prayer").performScrollTo().assertIsOn()

            // Revoked later: the switch reads off again and Grant comes back.
            policy.setNotificationPolicyAccessGranted(false)
            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(timeoutMillis = 5_000) { grantShown() }
            compose.onNodeWithText("Silent during Prayer").assertIsOff()
        } finally {
            runBlocking { store.setPrayerSilenceEnabled(false) }
        }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    @Config(qualifiers = "en")
    fun `salawat reminders are off under Worship reminders, and switching them on every 3 hours stores it`() {
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun salawat() = runBlocking { store.settings.first() }.alerts.worshipReminders.salawat
        openSettings("Settings")
        openPage("Alerts & adhan")
        try {
            compose.onNodeWithText("Worship reminders").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Salawat on the Prophet ﷺ").performScrollTo().assertIsOff()
            // The interval only shows once it is on.
            assertEquals(false, shown("Every 2 hours"))

            compose.onNodeWithText("Salawat on the Prophet ﷺ").performClick()

            compose.waitUntil(timeoutMillis = 5_000) { salawat().enabled && shown("Every 2 hours") }
            compose.onNodeWithText("Every 2 hours").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Every 3 hours").performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { salawat().intervalHours == 3 }
            compose.waitUntil(timeoutMillis = 5_000) { runCatching { compose.onNodeWithText("Every 3 hours").assertIsSelected() }.isSuccess }
            compose.onNodeWithText("Every 2 hours").assertIsNotSelected()
            compose.onNodeWithText("Salawat on the Prophet ﷺ").performScrollTo().assertIsOn()
        } finally {
            runBlocking {
                store.setSalawatReminder(false)
                store.setSalawatInterval(2)
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `the Surat Al-Kahf reminder is off under Worship reminders, and switching it on stores it`() {
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun kahf() = runBlocking { store.settings.first() }.alerts.worshipReminders.kahf
        openSettings("Settings")
        openPage("Alerts & adhan")
        try {
            compose.onNodeWithText("Surat Al-Kahf").performScrollTo().assertIsOff()

            compose.onNodeWithText("Surat Al-Kahf").performClick()

            compose.waitUntil(timeoutMillis = 5_000) { kahf().enabled }
            compose.waitUntil(timeoutMillis = 5_000) { runCatching { compose.onNodeWithText("Surat Al-Kahf").assertIsOn() }.isSuccess }
        } finally {
            runBlocking { store.setKahfReminder(false) }
        }
    }

    /** Opens Settings → Alerts with Do Not Disturb access granted and Prayer Silence turned on, then runs [block]. */
    private fun withPrayerSilenceOn(block: (alerts: () -> app.mawaqeet.core.AlertSettings) -> Unit) {
        shadowOf(compose.activity.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        val store = (compose.activity.application as MawaqeetApplication).settings
        fun alerts() = runBlocking { store.settings.first() }.alerts
        openSettings("Settings")
        openPage("Alerts & adhan")
        try {
            // Its choices only show once it is on.
            assertEquals(0, compose.onAllNodesWithText("Vibrate").fetchSemanticsNodes().size)
            compose.onNodeWithText("Silent during Prayer").performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { alerts().prayerSilence.enabled && shown("Vibrate") }
            block(::alerts)
        } finally {
            runBlocking {
                store.setPrayerSilenceEnabled(false)
                store.setPrayerSilenceMode(app.mawaqeet.core.SilenceMode.SILENT)
                Prayer.entries.forEach { store.setPrayerSilencePrayer(it, true) }
                store.stepPrayerSilenceMinutes(jumuah = false, step = -100)
                store.stepPrayerSilenceMinutes(jumuah = false, step = +3)
                store.stepPrayerSilenceMinutes(jumuah = true, step = -100)
                store.stepPrayerSilenceMinutes(jumuah = true, step = +6)
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `Prayer Silence silences by default and choosing Vibrate stores it`() = withPrayerSilenceOn { alerts ->
        compose.onNodeWithText("Silent").performScrollTo().assertIsSelected()

        compose.onNodeWithText("Vibrate").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { alerts().prayerSilence.mode == app.mawaqeet.core.SilenceMode.VIBRATE }
        compose.waitUntil(timeoutMillis = 5_000) { runCatching { compose.onNodeWithText("Vibrate").assertIsSelected() }.isSuccess }
        compose.onNodeWithText("Silent").assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "en")
    fun `every Prayer is silenced once Prayer Silence is on, and turning one off stores it`() = withPrayerSilenceOn { alerts ->
        val asr = compose.onNode(hasText("Asr") and isToggleable())
        asr.performScrollTo().assertIsOn()

        asr.performClick()

        compose.waitUntil(timeoutMillis = 5_000) { Prayer.ASR !in alerts().prayerSilence.prayers }
        assertEquals(Prayer.entries.toSet() - Prayer.ASR, alerts().prayerSilence.prayers)
        compose.waitUntil(timeoutMillis = 5_000) { runCatching { asr.assertIsOff() }.isSuccess }
    }

    @Test
    @Config(qualifiers = "en")
    fun `the minus and plus buttons change the Silence Window and Jumu'ah durations five minutes at a time`() = withPrayerSilenceOn { alerts ->
        compose.onNodeWithText("20 minutes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("45 minutes").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Silence 5 minutes longer").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Silence 5 minutes longer").performClick()
        compose.onNodeWithContentDescription("Jumu'ah silence 5 minutes shorter").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { alerts().prayerSilence.let { it.minutes == 30 && it.jumuahMinutes == 40 } }
        compose.waitUntil(timeoutMillis = 5_000) { shown("30 minutes") && shown("40 minutes") }
        compose.onNodeWithText("30 minutes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40 minutes").performScrollTo().assertIsDisplayed()
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
    fun `choosing wallpaper colours for the widgets stores it, and transparency reads as a percentage`() {
        openSettings("Settings")
        openPage("Widgets")
        val store = (compose.activity.application as MawaqeetApplication).settings

        compose.onNodeWithText("Background transparency").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wallpaper colours").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { store.widgetAppearance.first() }.colors == app.mawaqeet.widget.WidgetColors.WALLPAPER
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
