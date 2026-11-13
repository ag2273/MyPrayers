package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * The Last Third of the Night on Today (#96): after Isha, until Fajr, the Next Prayer card names when it starts, in
 * place of its bar and «العشاء … الفجر …» line; on the Ramadan card during suhoor, in place of the next Prayer line.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar")
class HomeLastThirdTest {
    @get:Rule
    val compose = createComposeRule()

    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val format = DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC)

    private fun show(dateTime: String): PrayerCore {
        val clock = Clock.fixed(LocalDateTime.parse(dateTime).atZone(riyadh).toInstant(), ZoneOffset.UTC)
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
    fun `after Isha the card names the start of the Last Third instead of the bar's line`() {
        val core = show("2026-11-09T21:00")

        assertShown("الثلث الأخير من الليل يبدأ ${lastThirdOf(core)}")
        // The bar's line «العشاء ٧:٠٦ م … الفجر …» is gone: Isha is named only in its row of the list.
        assertEquals(1, count("العشاء"))
    }

    @Test
    fun `after midnight, once the Last Third has begun, the card says it began`() {
        val core = show("2026-11-10T03:00")

        assertShown("الثلث الأخير من الليل بدأ ${lastThirdOf(core)}")
        assertEquals(0, count("يبدأ"))
    }

    @Test
    fun `before Isha the card shows its bar's line and no Last Third`() {
        val core = show("2026-11-09T18:30")
        val maghrib = core.prayerDay(LocalDate.parse("2026-11-09")).prayerTimes.single { it.prayer == Prayer.MAGHRIB }.time

        assertShown("المغرب ${format.time(maghrib)}")
        assertEquals(0, count("الثلث الأخير"))
    }

    @Test
    fun `during suhoor after Isha the Ramadan card names the Last Third in place of the next Prayer`() {
        // 15 Ramadan 1448 in Makkah; at 21:00 the suhoor card counts to the 16th's Imsak.
        val core = show("2027-02-22T21:00")

        assertShown("رمضان · اليوم ١٦")
        assertShown("الثلث الأخير من الليل يبدأ ${lastThirdOf(core)}")
        assertEquals(0, count("الصلاة القادمة"))
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
