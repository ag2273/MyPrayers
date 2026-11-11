package app.mawaqeet

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarCatalog
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.AdhkarSlot
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.adhkar.AdhkarScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** One dhikr said once in each set. */
private val recordCatalog = AdhkarCatalog.parse(
    "# mawaqeet-adhkar-v1\n" +
        "morning\tm\t1\tرواه مسلم\tاللَّهُمَّ بِكَ أَصْبَحْنَا\n" +
        "evening\te\t1\tرواه مسلم\tاللَّهُمَّ بِكَ أَمْسَيْنَا\n" +
        "after-prayer\ta\t1\tرواه مسلم\tأَسْتَغْفِرُ اللَّهَ\n",
)

/** The Adhkar Record week grid in the Adhkar tab (#74). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AdhkarRecordScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeAdhkarRepository()

    /** The Adhkar at a Makkah wall-clock time (9 November 2026: Fajr 05:10, Asr 15:17, Isha 19:11). */
    private fun adhkarAt(dateTime: String) = Adhkar(
        recordCatalog,
        PrayerCore(
            PrayerSettings.Default,
            Clock.fixed(LocalDateTime.parse(dateTime).atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC),
            EmptyLog,
        ),
        repo,
    )

    /** First used on 5 November after Isha; this morning's adhkar read; now 07:00 on 9 November. */
    private fun showRecord(record: String) {
        adhkarAt("2026-11-05T20:00").count(AdhkarSlot.afterPrayer(Prayer.ISHA))
        val adhkar = adhkarAt("2026-11-09T07:00")
        adhkar.count(AdhkarSetId.MORNING)
        compose.setContent {
            MawaqeetTheme { ProvideDisplayFormatter(DigitStyle.WESTERN) { AdhkarScreen(adhkar, repo.changes) } }
        }
        waitFor(hasText(record))
        compose.onNodeWithText(record).performScrollTo().performClick()
    }

    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntil(5_000) {
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun count(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().size

    @Test
    @Config(qualifiers = "ar")
    fun `the record shows seven Prayer Days with a mark for the morning, the evening and each Prayer, in Arabic`() {
        showRecord("سجل الأذكار")
        waitFor(hasText("آخر سبعة أيام"))

        compose.waitUntil(5_000) { count(hasText(" هـ", substring = true)) == 7 }
        // 7 marks a day.
        assertEquals(7, count(hasContentDescription("أذكار الصباح")))
        assertEquals(7, count(hasContentDescription("أذكار بعد صلاة العشاء")))
        assertEquals(1, count(hasContentDescription("أذكار الصباح") and hasStateDescription("اكتملت")))
        assertEquals(1, count(hasContentDescription("أذكار بعد صلاة العشاء") and hasStateDescription("اكتملت")))
        // 3 and 4 November are before the first use: never shown as missed.
        assertEquals(14, count(hasStateDescription("قبل بدء استخدامك للأذكار")))
        // Today at 07:00 the evening and Dhuhr to Isha have not come yet.
        assertEquals(1, count(hasContentDescription("أذكار المساء") and hasStateDescription("لم يحن وقتها بعد")))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `older weeks are browsable and the next week button returns to the latest`() {
        showRecord("سجل الأذكار")
        waitFor(hasText("آخر سبعة أيام"))
        compose.onNodeWithContentDescription("الأسبوع التالي").performClick()
        compose.onNodeWithText("آخر سبعة أيام").assertIsDisplayed()

        compose.onNodeWithContentDescription("الأسبوع السابق").performClick()
        waitFor(hasText("أسبوع سابق"))
        // 27 October to 2 November: all before the first use.
        compose.waitUntil(5_000) { count(hasStateDescription("قبل بدء استخدامك للأذكار")) == 49 }
        assertTrue(count(hasText("27 أكتوبر", substring = true)) >= 1)

        compose.onNodeWithContentDescription("الأسبوع التالي").performClick()
        waitFor(hasText("آخر سبعة أيام"))
        compose.waitUntil(5_000) { count(hasStateDescription("اكتملت")) == 2 }
    }

    @Test
    @Config(qualifiers = "en")
    fun `TalkBack reads each mark's set and whether it was completed`() {
        showRecord("Adhkar Record")
        waitFor(hasText("Last seven days"))

        compose.waitUntil(5_000) { count(hasContentDescription("Adhkar after Isha") and hasStateDescription("Completed")) == 1 }
        assertEquals(1, count(hasContentDescription("Morning adhkar") and hasStateDescription("Completed")))
        assertEquals(1, count(hasContentDescription("Evening adhkar") and hasStateDescription("Not time yet")))
        assertTrue(count(hasContentDescription("Adhkar after Fajr") and hasStateDescription("Not completed")) >= 1)
        assertEquals(14, count(hasStateDescription("Before you started the adhkar")))
        // The marks are not buttons: nothing to tap in the record.
        assertEquals(0, count(hasContentDescription("Morning adhkar") and androidx.compose.ui.test.hasClickAction()))
    }
}

/** Settings → About & your data → Delete Adhkar Record, next to Delete Prayer Log. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DeleteAdhkarRecordTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: MawaqeetApplication by lazy { compose.activity.application as MawaqeetApplication }

    private fun <T> io(block: () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    @Test
    @Config(qualifiers = "en")
    fun `Delete Adhkar Record asks first and then leaves no completed sets`() {
        val app = app
        val day = LocalDate.of(2026, 11, 9)
        io { app.adhkar.recordCompleted(day, AdhkarSlot.MORNING.key) }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("About & your data").performScrollTo().performClick()
        compose.onNodeWithText("Delete Adhkar Record").performScrollTo().performClick()
        compose.onNodeWithText("Delete Adhkar Record?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(setOf("morning"), io { app.adhkar.completedOn(day) })

        compose.onNodeWithText("Delete Adhkar Record").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { io { app.adhkar.completedOn(day).isEmpty() } }
        compose.onNodeWithText("Adhkar Record deleted").assertExists()
    }
}
