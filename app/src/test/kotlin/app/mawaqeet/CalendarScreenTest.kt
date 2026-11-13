package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.HijriCalendar
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.calendar.CalendarScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Calendar screen's «القادم» tab (#93) on Friday 13 November 2026 (3 Jumada al-Akhirah 1448): the next Monday
 * fast is in 3 days, the white days in 10, Isra and Mi'raj on 5 January 2027 (27 Rajab). Dates: core tests.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(offsetDays: Int = 0) {
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    CalendarScreen(HijriCalendar(offsetDays), today = LocalDate.of(2026, 11, 13), onBack = {})
                }
            }
        }
    }

    private fun top(text: String): Float =
        compose.onAllNodesWithText(text)[0].getUnclippedBoundsInRoot().top.value

    @Test
    @Config(qualifiers = "ar")
    fun `Upcoming lists the weekly fasts and the Occasions in date order, each with how many days are left`() {
        show()

        compose.onNodeWithText("القادم").assertIsDisplayed()
        compose.onNodeWithText("صيام الاثنين والخميس").assertIsDisplayed()
        compose.onNodeWithText("بعد ٣ أيام").assertIsDisplayed()
        compose.onNodeWithText("الأيام البيض").assertIsDisplayed()
        compose.onNodeWithText("بعد ١٠ أيام").assertIsDisplayed()
        compose.onNodeWithText("الإسراء والمعراج").assertIsDisplayed()
        compose.onNodeWithText("٢٧ رجب ١٤٤٨ هـ", substring = true).assertIsDisplayed()

        val order = listOf("صيام الاثنين والخميس", "الأيام البيض", "الإسراء والمعراج", "بداية رمضان", "عيد الفطر", "يوم عرفة", "عيد الأضحى", "رأس السنة الهجرية")
        val tops = order.map(::top)
        assertEquals(tops.sorted(), tops)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the Monday and Thursday fast is listed once, not every week`() {
        show()

        assertEquals(1, compose.onAllNodesWithText("صيام الاثنين والخميس").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the note says the dates follow Umm al-Qura and may differ by a day from local sighting`() {
        show()

        compose.onNodeWithText("أم القرى", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English an entry shows its Gregorian date and the days left`() {
        show()

        compose.onNodeWithText("Mondays and Thursdays").assertIsDisplayed()
        compose.onNodeWithText("in 3 days").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("16 November", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
