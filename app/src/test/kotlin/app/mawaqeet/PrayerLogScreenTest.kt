package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PrayerLogScreenTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: MawaqeetApplication by lazy { compose.activity.application as MawaqeetApplication }

    private fun core() = runBlocking { PrayerCore(app.settings.settings.first(), Clock.systemUTC(), app.prayerLog) }

    private fun <T> io(block: () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    @Test
    @Config(qualifiers = "ar")
    fun `the Prayer Log opens from Home with the week's summary and seven days, in Arabic`() {
        compose.onNodeWithText("السجل").performClick()
        // The week is read off the main thread.
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("ملخص الأسبوع").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("ملخص الأسبوع").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("المجموع").performScrollTo().assertIsDisplayed()
        // TalkBack reads every day of the strip with its Hijri date, and the chosen day shows it too.
        val withHijri = androidx.compose.ui.test.hasContentDescription(" هـ", substring = true)
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(withHijri).fetchSemanticsNodes().size == 7 }
        compose.onNodeWithText(" هـ", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("الأسبوع السابق").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("الأيام الأقدم من أسبوع للعرض فقط.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `Delete Prayer Log asks first and then leaves no entries`() {
        val app = app
        val core = core()
        val day = io { core.week().days.first { it.isMarkable(Prayer.FAJR) } }
        io { core.mark(day.date, Prayer.FAJR) }
        assertTrue(io { Prayer.FAJR in app.prayerLog.prayedOn(day.date) })

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("About & your data").performScrollTo().performClick()
        compose.onNodeWithText("Delete Prayer Log").performScrollTo().performClick()
        compose.onNodeWithText("Delete Prayer Log?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(io { Prayer.FAJR in app.prayerLog.prayedOn(day.date) })

        compose.onNodeWithText("Delete Prayer Log").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { io { app.prayerLog.prayedOn(day.date).isEmpty() } }
        assertEquals(emptySet<Prayer>(), io { app.prayerLog.prayedOn(day.date) })
    }
}
