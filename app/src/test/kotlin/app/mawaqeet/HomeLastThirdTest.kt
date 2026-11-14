package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.PastPlace
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerLogRepository
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.home.HomeScreen
import app.mawaqeet.ui.home.RING_TAG
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.Locale

/**
 * The Last Third of the Night on Today (#96): after Isha, until Fajr, the hero's line under the countdown names when it
 * starts in place of the next Prayer's time; during suhoor in Ramadan, in place of Imsak's time (Imsak stays as the
 * first date chip), while Fajr keeps its stop and time.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar-w412dp-h915dp")
class HomeLastThirdTest {
    @get:Rule
    val compose = createComposeRule()

    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val format = DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC)

    private fun instant(dateTime: String) = LocalDateTime.parse(dateTime).atZone(riyadh).toInstant()

    private fun show(dateTime: String): PrayerCore {
        val clock = Clock.fixed(instant(dateTime), ZoneOffset.UTC)
        val core = PrayerCore(PrayerSettings.Default, clock, NoMarks)
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.ARABIC_INDIC) {
                    HomeScreen(core, clock, MutableStateFlow(0), "مكة المكرمة")
                }
            }
        }
        compose.waitForIdle()
        return core
    }

    private fun lastThirdOf(core: PrayerCore) = format.time(core.displayDay().lastThirdOfNight)

    private fun assertShown(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()

    private fun count(text: String) =
        compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `after Isha the hero names the start of the Last Third instead of Fajr's time`() {
        val core = show("2026-11-09T21:00")

        assertShown("الثلث الأخير ${lastThirdOf(core)}")
        // Fajr's time is left only on its stop.
        assertEquals(1, count(format.time(core.displayDay().next.time)))
    }

    @Test
    fun `after midnight, once the Last Third has begun, the hero says it began`() {
        val core = show("2026-11-10T03:00")

        assertShown("بدأ الثلث الأخير ${lastThirdOf(core)}")
    }

    @Test
    @Config(qualifiers = "ar-w393dp-h852dp", fontScale = 1.3f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the Last Third line stays inside the ring`() {
        val core = show("2026-11-10T03:00")

        val line = compose.onNodeWithText("بدأ الثلث الأخير ${lastThirdOf(core)}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val ring = compose.onNodeWithTag(RING_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Within the chord the stroke leaves at the line's height: 60% of the ring, centred.
        assertTrue("line ${line.width} wide in a ring of ${ring.width}", line.width <= ring.width * 0.6f + 1f)
        assertTrue(line.center.x in ring.center.x - 2f..ring.center.x + 2f)
    }

    @Test
    fun `before Isha the hero shows Isha's time and no Last Third`() {
        val core = show("2026-11-09T18:30")
        val isha = core.prayerDay(LocalDate.parse("2026-11-09")).prayerTimes.single { it.prayer == Prayer.ISHA }.time

        assertEquals("in the hero and on its stop", 2, count(format.time(isha)))
        assertEquals(0, count("الثلث الأخير"))
    }

    @Test
    fun `during suhoor after Isha the hero names the Last Third, and Fajr keeps its stop and time`() {
        // 15 Ramadan 1448 in Makkah; at 21:00 the suhoor hero counts to the 16th's Imsak.
        val core = show("2027-02-22T21:00")
        val fajr = core.displayDay().next.time

        assertShown("رمضان · اليوم ١٦")
        assertShown("السحور")
        assertShown("الثلث الأخير ${lastThirdOf(core)}")
        assertShown(format.time(fajr))
    }
}

/** No Prayer Log: these tests look only at the card. */
private object NoMarks : PrayerLogRepository {
    override fun prayedOn(prayerDay: LocalDate) = emptySet<Prayer>()
    override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) = Unit
    override fun hasAnyEntry() = true
    override fun firstUseDate(): LocalDate? = null
    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) = Unit
    override fun pastPlaces() = emptyList<PastPlace>()
    override fun addPastPlace(place: PastPlace) = Unit
    override fun deleteAllEntries(firstUseFrom: LocalDate) = Unit
}
