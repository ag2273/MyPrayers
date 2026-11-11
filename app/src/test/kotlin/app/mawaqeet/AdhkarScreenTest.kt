package app.mawaqeet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarCatalog
import app.mawaqeet.core.AdhkarRepository
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.AdhkarSlot
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertFalse
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerLogRepository
import app.mawaqeet.core.PastPlace
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.adhkar.AdhkarScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A two-dhikr morning set: one repetition, then three. */
private val catalog = AdhkarCatalog.parse(
    "# mawaqeet-adhkar-v1\n" +
        "morning\tone\t1\tرواه مسلم\tاللَّهُمَّ بِكَ أَصْبَحْنَا\n" +
        "morning\tthree\t3\tرواه أبو داود والترمذي\tبِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ\n" +
        "evening\tone\t1\tرواه مسلم\tاللَّهُمَّ بِكَ أَمْسَيْنَا\n" +
        "after-prayer\tone\t1\tرواه مسلم\tأَسْتَغْفِرُ اللَّهَ\n",
)

internal class FakeAdhkarRepository : AdhkarRepository {
    val changes = MutableStateFlow(0)
    private val counts = mutableMapOf<Triple<LocalDate, String, String>, Int>()
    private val record = mutableSetOf<Pair<LocalDate, String>>()

    @Synchronized override fun counts(prayerDay: LocalDate, slot: String) =
        counts.filterKeys { it.first == prayerDay && it.second == slot }.mapKeys { it.key.third }

    @Synchronized override fun setCount(prayerDay: LocalDate, slot: String, dhikrId: String, count: Int) {
        counts[Triple(prayerDay, slot, dhikrId)] = count
        changes.update { it + 1 }
    }

    @Synchronized override fun clearCounts(prayerDay: LocalDate, slot: String) {
        counts.keys.removeAll { it.first == prayerDay && it.second == slot }
        changes.update { it + 1 }
    }

    @Synchronized override fun clearCountsBefore(prayerDay: LocalDate) {
        counts.keys.removeAll { it.first < prayerDay }
    }

    @Synchronized override fun completedOn(prayerDay: LocalDate) =
        record.filter { it.first == prayerDay }.mapTo(mutableSetOf()) { it.second }

    @Synchronized override fun recordCompleted(prayerDay: LocalDate, slot: String) {
        record += prayerDay to slot
        changes.update { it + 1 }
    }

    private var firstUse: LocalDate? = null

    @Synchronized override fun firstUseDate() = firstUse

    @Synchronized override fun recordFirstUseIfAbsent(prayerDay: LocalDate) {
        if (firstUse == null) firstUse = prayerDay
    }

    @Synchronized override fun deleteRecord(firstUseFrom: LocalDate) {
        counts.clear()
        record.clear()
        firstUse = firstUseFrom
        changes.update { it + 1 }
    }
}

internal object EmptyLog : PrayerLogRepository {
    override fun prayedOn(prayerDay: LocalDate) = emptySet<Prayer>()
    override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) = Unit
    override fun firstUseDate(): LocalDate? = null
    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) = Unit
    override fun pastPlaces() = emptyList<PastPlace>()
    override fun addPastPlace(place: PastPlace) = Unit
    override fun deleteAllEntries(firstUseFrom: LocalDate) = Unit
}

private class CountingHaptics : HapticFeedback {
    var count = 0
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        count++
    }
}

/** The Adhkar tab (#62): the morning set read card by card, counted by tapping, with what TalkBack reads. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AdhkarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeAdhkarRepository()
    private val haptics = CountingHaptics()

    // 07:00 in Makkah: the morning of the 9 November 2026 Prayer Day.
    private val adhkar = Adhkar(
        catalog,
        PrayerCore(
            PrayerSettings.Default,
            Clock.fixed(LocalDateTime.parse("2026-11-09T07:00").atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC),
            EmptyLog,
        ),
        repo,
    )

    private fun show() {
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.WESTERN) {
                    CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                        AdhkarScreen(adhkar, repo.changes)
                    }
                }
            }
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) = compose.waitUntil(5_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `tapping the card counts with a vibration and moves to the next dhikr, in Arabic`() {
        show()
        waitForText("أذكار الصباح")
        compose.onNodeWithText("أذكار الصباح").performClick()
        waitForText("الذكر 1 من 2")
        compose.onNodeWithText("اللَّهُمَّ بِكَ أَصْبَحْنَا").assertIsDisplayed()
        compose.onNodeWithText("رواه مسلم").assertIsDisplayed()

        compose.onNodeWithText("اللَّهُمَّ بِكَ أَصْبَحْنَا").performClick()
        waitForText("الذكر 2 من 2")
        assertEquals(1, haptics.count)

        val text = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ"
        compose.onNodeWithText(text).performClick()
        waitForText("1/3", substring = true)
        compose.onNodeWithText(text).performClick()
        compose.onNodeWithText(text).performClick()
        waitForText("اكتملت أذكار الصباح لهذا اليوم")
        assertEquals(4, haptics.count)
        assertTrue(adhkar.isComplete(AdhkarSetId.MORNING))

        compose.onNodeWithText("ابدأ من جديد").performClick()
        waitForText("الذكر 1 من 2")
    }

    @Test
    @Config(qualifiers = "en")
    fun `TalkBack reads each card's text with what is done and left, and the count action`() {
        show()
        waitForText("Morning adhkar")
        compose.onNodeWithText("Morning adhkar").performClick()
        waitForText("Dhikr 1 of 2")
        compose.onNodeWithText("اللَّهُمَّ بِكَ أَصْبَحْنَا").performClick()
        waitForText("Dhikr 2 of 2")
        compose.onNodeWithText("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasStateDescription("1 of 3 done, 2 left")).fetchSemanticsNodes().isNotEmpty()
        }

        // The card is one TalkBack item: the dhikr, its source and the count, with its state and action.
        val card = compose.onNode(hasStateDescription("1 of 3 done, 2 left")).fetchSemanticsNode()
        val texts = card.config[SemanticsProperties.Text].map { it.text }
        assertTrue("reads $texts", "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ" in texts)
        assertTrue("reads $texts", "رواه أبو داود والترمذي" in texts)
        val action = card.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label
        assertEquals("Count one", action)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the list shows each set's state for today, and leaving halfway resumes`() {
        show()
        waitForText("لم تبدأ بعد")
        compose.onNodeWithText("أذكار الصباح").performClick()
        waitForText("الذكر 1 من 2")
        compose.onNodeWithText("اللَّهُمَّ بِكَ أَصْبَحْنَا").performClick()
        waitForText("الذكر 2 من 2")

        compose.onNode(androidx.compose.ui.test.hasContentDescription("رجوع")).performClick()
        waitForText("وصلت إلى الذكر 2 من 2")
        compose.onNodeWithText("أذكار الصباح").performClick()
        waitForText("الذكر 2 من 2")

        repeat(3) { compose.onNodeWithText("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ").performClick() }
        waitForText("اكتملت أذكار الصباح لهذا اليوم")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("رجوع")).performClick()
        waitForText("تمّت اليوم")
    }

    private fun waitForRow(title: String, summary: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(title) and hasText(summary)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the list shows the morning, the evening and each of the five after-prayer states`() {
        adhkar.count(AdhkarSlot.afterPrayer(Prayer.FAJR))
        show()

        waitForRow("أذكار الصباح", "لم تبدأ بعد")
        // 07:00 is before Asr: the evening adhkar can be opened, but their time is later.
        waitForRow("أذكار المساء", "وقتها من العصر إلى الفجر")
        waitForRow("أذكار بعد الصلاة", "بعد صلاة الفجر")
        waitForRow("بعد صلاة الفجر", "تمّت اليوم")
        for (prayer in listOf("الظهر", "العصر", "المغرب", "العشاء")) waitForRow("بعد صلاة $prayer", "لم يحن وقتها بعد")
    }

    @Test
    @Config(qualifiers = "en")
    fun `after-prayer adhkar open on the most recent Prayer, and another Prayer can be chosen before starting`() {
        show()
        waitForText("After-prayer adhkar")
        compose.onNodeWithText("After-prayer adhkar").performClick()
        waitForText("Dhikr 1 of 1")
        compose.onNode(hasText("Fajr") and androidx.compose.ui.test.isSelected()).assertExists()

        compose.onNodeWithText("Dhuhr").performClick()
        waitForText("Dhuhr's time hasn't come yet", substring = true)
        compose.onNodeWithText("Fajr").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Fajr") and androidx.compose.ui.test.isSelected()).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("أَسْتَغْفِرُ اللَّهَ").performClick()
        waitForText("Adhkar after Fajr completed for today")
        assertTrue(adhkar.isComplete(AdhkarSlot.afterPrayer(Prayer.FAJR)))
        assertFalse(adhkar.isComplete(AdhkarSlot.afterPrayer(Prayer.DHUHR)))
    }
}

/** At the largest system font size, the reader shows every word of a long dhikr and big enough tap targets. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], fontScale = 2.0f)
class AdhkarAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeAdhkarRepository()

    @Test
    @Config(qualifiers = "ar-w360dp-h12000dp")
    fun `in Arabic at 200% text a long dhikr reflows and nothing is cut off`() {
        val bundled = java.io.File("src/main/assets/adhkar.tsv").inputStream().use(AdhkarCatalog::read)
        val adhkar = Adhkar(
            bundled,
            PrayerCore(PrayerSettings.Default, Clock.systemUTC(), EmptyLog),
            repo,
        )
        compose.setContent {
            MawaqeetTheme { ProvideDisplayFormatter(DigitStyle.AUTOMATIC) { AdhkarScreen(adhkar, repo.changes) } }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("أذكار الصباح")).fetchSemanticsNodes().isNotEmpty() }
        val density = compose.density.density
        compose.assertAccessible("Adhkar", density)
        compose.onNodeWithText("أذكار الصباح").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("الذكر", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.assertAccessible("Morning adhkar", density)
    }
}
