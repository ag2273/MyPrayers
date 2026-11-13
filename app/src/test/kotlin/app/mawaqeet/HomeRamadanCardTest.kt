package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Ramadan Mode on Today (#90), in the 1.4 hero: from Imsak to Maghrib the hero counts to iftar, from Maghrib to the
 * next Imsak to the end of suhoor, with the day of Ramadan over it and Imsak as the first date chip. The next Prayer
 * keeps its own countdown on its stop in the timeline. Outside Ramadan the hero counts to the next Prayer's adhan.
 * Ramadan 1448 starts on 2027-02-08 in Makkah (Umm al-Qura), so 2027-02-22 is 15 Ramadan.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar-w412dp-h915dp")
class HomeRamadanCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val format = DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC)

    private fun clockAt(dateTime: String) = Clock.fixed(LocalDateTime.parse(dateTime).atZone(riyadh).toInstant(), ZoneOffset.UTC)

    private fun show(clock: Clock): PrayerCore {
        val core = PrayerCore(PrayerSettings.Default, clock, NoLog)
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

    private fun PrayerCore.timeOn(date: String, prayer: Prayer): ZonedDateTime =
        prayerDay(LocalDate.parse(date)).prayerTimes.single { it.prayer == prayer }.time

    private fun assertShown(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()

    private fun count(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `while fasting the hero counts to iftar, with Imsak and the day of Ramadan, and the next Prayer counts on its stop`() {
        val clock = clockAt("2027-02-22T15:00")
        val core = show(clock)
        val now = clock.instant()
        val fajr = core.timeOn("2027-02-22", Prayer.FAJR)
        val asr = core.timeOn("2027-02-22", Prayer.ASR)
        val maghrib = core.timeOn("2027-02-22", Prayer.MAGHRIB)

        assertShown("رمضان · اليوم ١٥")
        assertShown("الإمساك ${format.time(fajr.minusMinutes(10))}")
        assertShown("الإفطار")
        assertShown("حتى الإفطار")
        assertShown(format.countdown(Duration.between(now, maghrib.toInstant())))
        assertEquals("Maghrib's time in the hero and on its stop", 2, count(format.time(maghrib)))
        assertShown("بعد ${format.countdown(Duration.between(now, asr.toInstant()))}")
        // The usual hero's line is gone.
        assertEquals(0, count("حتى الأذان"))
    }

    @Test
    fun `after Maghrib the hero counts to the end of suhoor, for the next day's fast`() {
        // Before Isha (Maghrib + 2 hours in Ramadan under Umm al-Qura): after it the last line is the Last Third (#96).
        val clock = clockAt("2027-02-22T19:00")
        val core = show(clock)
        val now = clock.instant()
        val imsak = core.timeOn("2027-02-23", Prayer.FAJR).minusMinutes(10)
        val isha = core.timeOn("2027-02-22", Prayer.ISHA)

        assertShown("رمضان · اليوم ١٦")
        assertShown("الإمساك ${format.time(imsak)}")
        assertShown("السحور")
        assertShown("حتى نهاية السحور")
        assertShown(format.countdown(Duration.between(now, imsak.toInstant())))
        assertShown("بعد ${format.countdown(Duration.between(now, isha.toInstant()))}")
        assertEquals(0, count("الإفطار", substring = true))
    }

    @Test
    fun `outside Ramadan the hero counts to the next Prayer's adhan`() {
        show(clockAt("2026-11-09T13:00"))

        assertShown("الصلاة القادمة")
        assertShown("حتى الأذان")
        assertEquals(0, count("رمضان", substring = true))
        assertEquals(0, count("الإمساك", substring = true))
    }
}

/** No Prayer Log: these tests look only at the card. */
private object NoLog : app.mawaqeet.core.PrayerLogRepository {
    override fun prayedOn(prayerDay: LocalDate) = emptySet<Prayer>()
    override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) = Unit
    override fun hasAnyEntry() = true
    override fun firstUseDate(): LocalDate? = null
    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) = Unit
    override fun pastPlaces() = emptyList<app.mawaqeet.core.PastPlace>()
    override fun addPastPlace(place: app.mawaqeet.core.PastPlace) = Unit
    override fun deleteAllEntries(firstUseFrom: LocalDate) = Unit
}
