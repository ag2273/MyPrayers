package app.mawaqeet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.PastPlace
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerLogRepository
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.components.MainScaffold
import app.mawaqeet.ui.components.Tab
import app.mawaqeet.ui.home.HomeScreen
import app.mawaqeet.ui.home.ToolsRow
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

private const val HINT = "اضغط الصلاة بعد أدائها لتسجيلها. الجرس يبيّن نوع التنبيه."
private const val ISHA = "العشاء"
private const val QIBLA = "القبلة"

/** A Prayer Log in memory that tells the screen when it changes, as the Room one does. */
private class WatchedLog : PrayerLogRepository {
    val changes = MutableStateFlow(0)
    private val entries = mutableSetOf<Pair<LocalDate, Prayer>>()

    @Synchronized override fun prayedOn(prayerDay: LocalDate) = entries.filter { it.first == prayerDay }.mapTo(mutableSetOf()) { it.second }

    @Synchronized override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) {
        if (prayed) entries += prayerDay to prayer else entries -= prayerDay to prayer
        changes.update { it + 1 }
    }

    @Synchronized override fun hasAnyEntry() = entries.isNotEmpty()
    override fun firstUseDate(): LocalDate? = LocalDate.of(2026, 11, 1)
    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) = Unit
    override fun pastPlaces() = emptyList<PastPlace>()
    override fun addPastPlace(place: PastPlace) = Unit
    override fun deleteAllEntries(firstUseFrom: LocalDate) = Unit
}

/**
 * Today fits its screen exactly at the default font size (#87): no scroll on a 360×740dp phone, even with the 48dp
 * Tools Row (#88), and no blank area under the last row on a 412×915dp one. A larger font scrolls instead of
 * clipping. The page is shown in the real tab frame, with 24dp stand-ins for the status and gesture bars.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HomeFitTest {
    @get:Rule
    val compose = createComposeRule()

    private val log = WatchedLog()

    /** 13:00 in Makkah on 9 November 2026: after Dhuhr, so the Next Prayer card shows its progress bar too. */
    private val clock = Clock.fixed(LocalDateTime.parse("2026-11-09T13:00").atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC)
    private val core = PrayerCore(PrayerSettings.Default, clock, log)

    private val density get() = compose.density.density

    private fun show(withToolsRow: Boolean = false) {
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    Box(Modifier.fillMaxSize().padding(vertical = 24.dp)) {
                        MainScaffold(Tab.TODAY, onSelect = {}) {
                            HomeScreen(
                                core, clock, log.changes, "مكة المكرمة",
                                tools = if (withToolsRow) { { ToolsRow(onQibla = {}) } } else null,
                            )
                        }
                    }
                }
            }
        }
        // The Prayer Log is read off the main thread.
        compose.waitUntil(5_000) { compose.onAllNodes(prayerRow(ISHA)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForHint() = compose.waitUntil(5_000) { compose.onAllNodes(hasText(HINT)).fetchSemanticsNodes().isNotEmpty() }

    private fun page(): SemanticsNode =
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(ISHA)), useUnmergedTree = true).fetchSemanticsNodes().single()

    private fun scrollRange(): Float = page().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    /** A Prayer's row in the list (the Next Prayer card names one too): the one TalkBack reads as a checkbox. */
    private fun prayerRow(name: String) = hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

    private fun rowOf(name: String): SemanticsNode = compose.onNode(prayerRow(name)).fetchSemanticsNode()

    private fun dp(px: Float) = px / density

    @Test
    @Config(qualifiers = "ar-w360dp-h740dp")
    fun `on a 360x740dp phone Today fits without scrolling, with the tools row`() {
        log.setPrayed(LocalDate.of(2026, 11, 9), Prayer.FAJR, true)
        show(withToolsRow = true)

        assertEquals("scroll range", 0f, scrollRange())
        val qibla = compose.onNode(hasText(QIBLA) and hasClickAction()).fetchSemanticsNode()
        assertEquals("the Qibla button is 48dp tall", 48f, dp(qibla.touchBoundsInRoot.height), 0.5f)
        for (prayer in listOf("الفجر", "الظهر", "العصر", "المغرب", ISHA)) {
            assertTrue("$prayer row is a 48dp tap target", dp(rowOf(prayer).touchBoundsInRoot.height) >= 47.5f)
        }
    }

    @Test
    @Config(qualifiers = "ar-w412dp-h915dp")
    fun `on a 412x915dp phone the rows grow so nothing is blank below the last one`() {
        log.setPrayed(LocalDate.of(2026, 11, 9), Prayer.FAJR, true)
        show(withToolsRow = true)

        assertEquals("scroll range", 0f, scrollRange())
        val gap = dp(page().boundsInRoot.bottom - rowOf(ISHA).boundsInRoot.bottom)
        // Only the card's inset and the page's margin are left below Isha.
        assertTrue("gap below Isha is ${gap}dp", gap <= 20f)
        assertTrue("rows grew past 48dp", dp(rowOf(ISHA).boundsInRoot.height) > 52f)
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h740dp", fontScale = 1.5f)
    fun `with a large font the page scrolls and no text is cut off`() {
        show(withToolsRow = true)
        waitForHint()

        assertTrue("scrolls", scrollRange() > 0f)
        compose.assertAccessible("Today at the top", density, parentClips = false)
        compose.onNodeWithText(HINT).performScrollTo().assertIsDisplayed()
        compose.assertAccessible("Today at the bottom", density, parentClips = false)
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h740dp")
    fun `the log hint shows until the first mark and is gone after it`() {
        show()
        waitForHint()
        compose.onNodeWithText(HINT).assertIsDisplayed()

        compose.onNode(prayerRow("الفجر")).performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText(HINT)).fetchSemanticsNodes().isEmpty() }
    }
}
