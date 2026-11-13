package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.HijriCalendar
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.calendar.CalendarScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.abs

/**
 * The Calendar screen's «الشهر» tab (#94) on Friday 13 November 2026 (3 Jumada al-Akhirah 1448). Umm al-Qura
 * (published): 1 Jumada al-Akhirah 1448 is Wednesday 11 November 2026 and the month has 29 days; 1 Dhu al-Hijjah 1448
 * is 7 May 2027; 1 Muharram 1449 is 6 June 2027.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar-w360dp-h4000dp")
class CalendarMonthTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showMonth(offsetDays: Int = 0) {
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    CalendarScreen(HijriCalendar(offsetDays), today = LocalDate.of(2026, 11, 13), onBack = {})
                }
            }
        }
        compose.onNodeWithText("الشهر").performClick()
    }

    /** The grid cell whose TalkBack label starts with [hijri], such as «١٣ جمادى الآخرة ١٤٤٨ هـ». */
    private fun cell(hijri: String): String {
        val labels = compose.onAllNodesWithContentDescription(hijri, substring = true).fetchSemanticsNodes()
            .map { it.label() }
            .filter { it.startsWith("$hijri،") }
        assertEquals("one cell for $hijri: $labels", 1, labels.size)
        return labels.single()
    }

    private fun centerX(description: String): Float =
        compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot().let { (it.left + it.right).value / 2 }

    private fun next(times: Int = 1) = repeat(times) { compose.onNodeWithContentDescription("الشهر التالي").performClick() }

    @Test
    fun `the Month tab opens on the current Hijri month, its Gregorian span under the title`() {
        showMonth()

        compose.onNodeWithText("جمادى الآخرة ١٤٤٨ هـ").assertIsDisplayed()
        compose.onNodeWithText("نوفمبر – ديسمبر ٢٠٢٦").assertIsDisplayed()
    }

    @Test
    fun `the grid starts on Saturday and the 1st falls under its weekday, with as many days as the month has`() {
        showMonth()

        val saturday = centerX("السبت")
        val wednesday = centerX("الأربعاء")
        val friday = centerX("الجمعة")
        // Right to left in Arabic: Saturday is the first column, on the right.
        assertTrue(saturday > wednesday && wednesday > friday)
        assertEquals(wednesday, centerX(cell("١ جمادى الآخرة ١٤٤٨ هـ")), 1f)
        // Wednesday 11 November is its 1st; Friday 13th its 3rd.
        assertTrue(cell("١ جمادى الآخرة ١٤٤٨ هـ").contains("١١ نوفمبر"))
        assertEquals(friday, centerX(cell("٣ جمادى الآخرة ١٤٤٨ هـ")), 1f)
        cell("٢٩ جمادى الآخرة ١٤٤٨ هـ")
        assertTrue(compose.onAllNodesWithContentDescription("٣٠ جمادى الآخرة", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `cells say today, the Monday and Thursday fast and the white days`() {
        showMonth()

        assertTrue(cell("٣ جمادى الآخرة ١٤٤٨ هـ").endsWith("اليوم"))
        assertTrue(cell("٦ جمادى الآخرة ١٤٤٨ هـ").contains("صيام الاثنين والخميس")) // Monday 16 November
        assertFalse(cell("٧ جمادى الآخرة ١٤٤٨ هـ").contains("صيام")) // Tuesday
        val thirteenth = cell("١٣ جمادى الآخرة ١٤٤٨ هـ") // Monday 23 November
        assertTrue(thirteenth.contains("الأيام البيض") && thirteenth.contains("صيام الاثنين والخميس"))
        assertTrue(cell("١٥ جمادى الآخرة ١٤٤٨ هـ").contains("الأيام البيض"))
        assertFalse(cell("١٦ جمادى الآخرة ١٤٤٨ هـ").contains("الأيام البيض"))
        assertFalse(cell("٤ جمادى الآخرة ١٤٤٨ هـ").contains("اليوم"))
    }

    @Test
    fun `in Dhu al-Hijjah the Occasions are marked and the 13th, a day of Tashreeq, is no white day`() {
        showMonth()
        next(6)

        compose.onNodeWithText("ذو الحجة ١٤٤٨ هـ").assertIsDisplayed()
        assertTrue(cell("٩ ذو الحجة ١٤٤٨ هـ").contains("يوم عرفة"))
        val eid = cell("١٠ ذو الحجة ١٤٤٨ هـ")
        assertTrue(eid.contains("عيد الأضحى") && !eid.contains("صيام"))
        val thirteenth = cell("١٣ ذو الحجة ١٤٤٨ هـ")
        assertTrue(thirteenth.contains("أيام التشريق") && !thirteenth.contains("الأيام البيض"))
        assertTrue(cell("١٤ ذو الحجة ١٤٤٨ هـ").contains("الأيام البيض"))
        // 17 May 2027, a Monday of Tashreeq: no Monday fast.
        assertFalse(cell("١١ ذو الحجة ١٤٤٨ هـ").contains("صيام"))
        assertTrue(cell("١ ذو الحجة ١٤٤٨ هـ").contains("٧ مايو"))
    }

    @Test
    fun `next and previous cross the Hijri year`() {
        showMonth()
        next(7)

        compose.onNodeWithText("محرم ١٤٤٩ هـ").assertIsDisplayed()
        assertTrue(cell("١ محرم ١٤٤٩ هـ").contains("٦ يونيو ٢٠٢٧"))
        compose.onNodeWithContentDescription("الشهر السابق").performClick()
        compose.onNodeWithText("ذو الحجة ١٤٤٨ هـ").assertIsDisplayed()
    }

    @Test
    fun `In this month lists the month's Occasions and fasts`() {
        showMonth()
        next(6)

        compose.onNodeWithText("في هذا الشهر").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("العشر الأوائل من ذي الحجة").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("١ – ١٠ ذو الحجة", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("أيام التشريق").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("١٤ – ١٥ ذو الحجة", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the legend names each mark`() {
        showMonth()

        listOf("صيام الاثنين والخميس", "الأيام البيض", "مناسبة", "اليوم").forEach {
            compose.onAllNodesWithText(it).onFirst().performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `the offset moves the grid by a day`() {
        showMonth(offsetDays = 1)

        assertTrue(cell("١ جمادى الآخرة ١٤٤٨ هـ").contains("١٠ نوفمبر"))
        assertTrue(abs(centerX("الثلاثاء") - centerX(cell("١ جمادى الآخرة ١٤٤٨ هـ"))) < 1f)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun `in English the Month tab reads left to right from Saturday`() {
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    CalendarScreen(HijriCalendar(), today = LocalDate.of(2026, 11, 13), onBack = {})
                }
            }
        }
        compose.onNodeWithText("Month").performClick()

        compose.onNodeWithText("Jumada al-Akhirah 1448 AH").assertIsDisplayed()
        assertTrue(centerX("Saturday") < centerX("Friday"))
        compose.onNodeWithContentDescription("Next month").assertIsDisplayed()
        compose.onNodeWithText("In this month").performScrollTo().assertIsDisplayed()
    }
}
