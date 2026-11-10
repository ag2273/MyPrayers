package app.mawaqeet

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What TalkBack reads on Home and the Prayer Log (#33): each Prayer as one item with its name, time and Prayer Log
 * state, never a bare tick or circle.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TalkBackTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val hasState = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)
    private val states = mapOf(
        "ar" to setOf("تمّت الصلاة", "لم تُسجَّل", "لم يحن وقتها", "لا يمكن تعديل"),
        "en" to setOf("Prayed", "Not marked", "hasn't arrived yet", "can no longer be edited"),
    )

    private fun checkHome(names: List<String>, language: String) {
        // Home shows its rows once the saved settings have loaded (async).
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(names.first()) and hasState).fetchSemanticsNodes().isNotEmpty() }
        for (name in names) {
            val row = compose.onNode(hasText(name) and hasState).fetchSemanticsNode()
            val texts = row.config[SemanticsProperties.Text].map { it.text }
            assertEquals("$name row reads $texts", 2, texts.size) // the name, then its time, and no "✓"
            assertTrue(texts[1].any { it.isDigit() })
            val state = row.config[SemanticsProperties.StateDescription]
            assertTrue("$name: «$state»", states.getValue(language).any { it in state })
        }
    }

    private fun checkLog(names: List<String>, log: String) {
        compose.onNodeWithText(log).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(names.first()).fetchSemanticsNodes().isNotEmpty() }
        for (name in names) {
            val cells = compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes()
            assertEquals("$name: one cell a day", 7, cells.size)
            for (cell in cells) {
                assertTrue("$name has no state", !cell.config.getOrNull(SemanticsProperties.StateDescription).isNullOrBlank())
                assertTrue("$name reads a tick", cell.config.getOrNull(SemanticsProperties.Text).orEmpty().none { "✓" in it.text })
            }
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `in Arabic each Prayer is read with its time and state, on Home and in the Prayer Log`() {
        val names = listOf("الفجر", "العصر", "المغرب", "العشاء")
        checkHome(names, "ar")
        checkLog(names, "سجل الصلاة")
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English each Prayer is read with its time and state, on Home and in the Prayer Log`() {
        val names = listOf("Fajr", "Asr", "Maghrib", "Isha")
        checkHome(names, "en")
        checkLog(names, "Prayer Log")
    }
}
