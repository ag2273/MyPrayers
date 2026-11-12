package app.mawaqeet

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * At the largest system font size (200%), Home, the Prayer Log and Settings show every word in both languages, and
 * every tap target is at least 48×48dp (#33). A tall window shows each whole screen at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], fontScale = 2.0f)
class AccessibilityTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val density get() = compose.activity.resources.displayMetrics.density

    private fun open(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    private fun checkScreens(log: String, back: String, settings: String, fajr: String, pages: List<String>) {
        compose.waitForIdle()
        compose.assertAccessible("Home", density)
        open(log)
        // The week loads off the main thread.
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(fajr).fetchSemanticsNodes().isNotEmpty() }
        compose.assertAccessible("Prayer Log", density)
        open(settings)
        compose.assertAccessible("Settings", density)
        for (page in pages) {
            open(page)
            compose.assertAccessible(page, density)
            compose.onNodeWithContentDescription(back).performClick()
            compose.waitForIdle()
        }
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h12000dp")
    fun `in Arabic at 200% text nothing is cut off and every tap target is big enough`() =
        checkScreens(
            "سجل الصلاة", "رجوع", "الإعدادات", "الفجر",
            listOf("الموقع وطريقة الحساب", "التنبيهات والأذان", "اللغة والعرض", "أدوات الشاشة الرئيسية", "النسخة الاحتياطية", "حول مواقيت وبياناتك"),
        )

    @Test
    @Config(qualifiers = "en-w360dp-h12000dp")
    fun `in English at 200% text nothing is cut off and every tap target is big enough`() =
        checkScreens(
            "Prayer Log", "Back", "Settings", "Fajr",
            listOf("Location & calculation", "Alerts & adhan", "Language & display", "Widgets", "Backup", "About & your data"),
        )
}
