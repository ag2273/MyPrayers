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
import androidx.compose.ui.test.onFirst
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
import app.mawaqeet.ui.home.TodaySize
import app.mawaqeet.ui.home.TodaySizeKey
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

private const val HINT = "اضغط الدائرة بعد أداء الصلاة لتسجيلها. الجرس يبيّن نوع التنبيه."
private const val ISHA = "العشاء"
private const val QIBLA = "القبلة"
private const val LAST_THIRD = "الثلث الأخير"

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
 * Tools Row (#88) and in Ramadan (#90), and no blank area under the last row on a 412×915dp one. A larger font scrolls instead of
 * clipping. The page is shown in the real tab frame, with 24dp stand-ins for the status and gesture bars.
 *
 * Today picks its size class from the height it has (#101): the original (regular) sizes from 393×852dp up, the compact
 * ones only on shorter screens. The matrix covers 360×640 to 600×960dp, a phone on its side, font scales 1.3 and 1.5,
 * normal days and Ramadan, with and without the log hint.
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

    /** 15:00 in Makkah on 15 Ramadan 1448 (2027-02-22): the fasting card, with its Imsak → Maghrib bar. */
    private val ramadanClock = Clock.fixed(LocalDateTime.parse("2027-02-22T15:00").atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC)

    /** 21:00 in Makkah on 9 November 2026: after Isha, so the card names the Last Third of the Night (#96). */
    private val nightClock = Clock.fixed(LocalDateTime.parse("2026-11-09T21:00").atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC)

    /** 21:00 in Makkah on 15 Ramadan 1448: the suhoor card, after Isha. */
    private val ramadanNightClock = Clock.fixed(LocalDateTime.parse("2027-02-22T21:00").atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC)

    private fun show(withToolsRow: Boolean = false, clock: Clock = this.clock) {
        val core = if (clock == this.clock) core else PrayerCore(PrayerSettings.Default, clock, log)
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    Box(Modifier.fillMaxSize().padding(vertical = 24.dp)) {
                        MainScaffold(Tab.TODAY, onSelect = {}) {
                            HomeScreen(
                                core, clock, log.changes, "مكة المكرمة",
                                tools = if (withToolsRow) { { ToolsRow(onQibla = {}, onCalendar = {}, onMosques = {}) } } else null,
                                onOpenCalendar = {},
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
    @Config(qualifiers = "ar-w360dp-h740dp")
    fun `on a 360x740dp phone the Ramadan card fits too, with room for the tools row`() {
        log.setPrayed(LocalDate.of(2027, 2, 22), Prayer.FAJR, true)
        show(withToolsRow = true, clock = ramadanClock)
        compose.onNodeWithText("الإفطار", useUnmergedTree = true).assertIsDisplayed()

        assertEquals("scroll range", 0f, scrollRange())
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
        assertTrue("rows grew past 48dp", dp(rowOf(ISHA).boundsInRoot.height) > 49f)
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

    /**
     * Shows Today as the app does, with the Tools Row, on a normal day or in Ramadan, by day or after Isha ([night], the
     * Last Third on the card, #96), with the log hint (an empty Prayer Log) or without it (Fajr marked).
     */
    private fun showToday(ramadan: Boolean, hint: Boolean, night: Boolean = false) {
        val clock = when {
            night && ramadan -> ramadanNightClock
            night -> nightClock
            ramadan -> ramadanClock
            else -> clock
        }
        if (!hint) log.setPrayed(if (ramadan) LocalDate.of(2027, 2, 22) else LocalDate.of(2026, 11, 9), Prayer.FAJR, true)
        show(withToolsRow = true, clock = clock)
        if (hint) waitForHint()
        // From Maghrib the hero counts to the end of suhoor, for the next Fasting Day.
        if (ramadan) compose.onNodeWithText(if (night) "السحور" else "الإفطار", useUnmergedTree = true).assertIsDisplayed()
        compose.waitForIdle()
    }

    /**
     * Today fits its screen exactly: nothing to scroll, every Prayer row a 48dp tap target, nothing blank below the
     * last item (the hint, or Isha's row) beyond the card's inset and the page's margin, and no text cut off.
     */
    private fun assertFillsTheScreen(hint: Boolean) {
        assertEquals("scroll range", 0f, scrollRange())
        for (prayer in listOf("الفجر", "الظهر", "العصر", "المغرب", ISHA)) {
            assertTrue("$prayer row is a 48dp tap target", dp(rowOf(prayer).touchBoundsInRoot.height) >= 47.5f)
        }
        val last = if (hint) compose.onNodeWithText(HINT).fetchSemanticsNode() else rowOf(ISHA)
        val gap = dp(page().boundsInRoot.bottom - last.boundsInRoot.bottom)
        assertTrue("gap below the last item is ${gap}dp", gap <= 20f)
        compose.assertAccessible("Today", density, parentClips = false)
    }

    /** With a large font on a short screen: the page scrolls rather than clip, and the hint is reachable. */
    private fun assertScrollsWithoutClipping(hint: Boolean) {
        assertTrue("scrolls", scrollRange() > 0f)
        compose.assertAccessible("Today at the top", density, parentClips = false)
        if (hint) compose.onNodeWithText(HINT).performScrollTo().assertIsDisplayed()
        compose.onNode(prayerRow(ISHA)).performScrollTo().assertIsDisplayed()
        compose.assertAccessible("Today at the bottom", density, parentClips = false)
    }

    // 360x640dp is shorter than Today's minimum even at the compact sizes (six 48dp rows, the Tools Row, the card and
    // the header need ~560dp; the tab bar and system bars leave ~500dp), so it scrolls, without clipping.
    @Test @Config(qualifiers = "ar-w360dp-h640dp")
    fun `360x640 is compact and scrolls without clipping, with the hint`() {
        showToday(ramadan = false, hint = true)
        assertEquals(TodaySize.COMPACT, todaySize())
        assertScrollsWithoutClipping(hint = true)
    }

    @Test @Config(qualifiers = "ar-w360dp-h640dp")
    fun `360x640 in Ramadan is compact and scrolls without clipping`() {
        showToday(ramadan = true, hint = false)
        assertEquals(TodaySize.COMPACT, todaySize())
        assertScrollsWithoutClipping(hint = false)
    }

    @Test @Config(qualifiers = "ar-w360dp-h740dp")
    fun `360x740 fits at the compact sizes`() {
        showToday(ramadan = false, hint = true)
        assertFillsTheScreen(hint = true)
        assertEquals(TodaySize.COMPACT, todaySize())
    }

    @Test @Config(qualifiers = "ar-w393dp-h800dp")
    fun `the regular sizes come back when the log hint goes`() {
        // Just too short for the regular sizes with the hint, tall enough without it.
        showToday(ramadan = false, hint = true)
        assertEquals(TodaySize.COMPACT, todaySize())

        compose.onNode(prayerRow("الفجر")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(HINT)).fetchSemanticsNodes().isEmpty() }

        assertEquals(TodaySize.REGULAR, todaySize())
        assertFillsTheScreen(hint = false)
    }

    @Test @Config(qualifiers = "ar-w360dp-h740dp")
    fun `360x740 fits in Ramadan with the hint`() {
        showToday(ramadan = true, hint = true)
        assertFillsTheScreen(hint = true)
    }

    @Test @Config(qualifiers = "ar-w393dp-h852dp")
    fun `393x852 fits at the regular sizes, with the hint`() {
        showToday(ramadan = false, hint = true)
        assertFillsTheScreen(hint = true)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    @Test @Config(qualifiers = "ar-w393dp-h852dp")
    fun `393x852 fits in Ramadan at the regular sizes`() {
        showToday(ramadan = true, hint = false)
        assertFillsTheScreen(hint = false)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    @Test @Config(qualifiers = "ar-w412dp-h915dp")
    fun `412x915 fits at the regular sizes`() {
        showToday(ramadan = false, hint = false)
        assertFillsTheScreen(hint = false)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    @Test @Config(qualifiers = "ar-w412dp-h915dp")
    fun `412x915 fits in Ramadan at the regular sizes, with the hint`() {
        showToday(ramadan = true, hint = true)
        assertFillsTheScreen(hint = true)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    @Test @Config(qualifiers = "ar-w480dp-h1040dp")
    fun `480x1040 fills at the regular sizes, with the hint`() {
        showToday(ramadan = false, hint = true)
        assertFillsTheScreen(hint = true)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    @Test @Config(qualifiers = "ar-w600dp-h960dp")
    fun `600x960 fills in Ramadan at the regular sizes`() {
        showToday(ramadan = true, hint = false)
        assertFillsTheScreen(hint = false)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    @Test @Config(qualifiers = "ar-w740dp-h360dp")
    fun `a phone on its side scrolls at the compact sizes without clipping`() {
        showToday(ramadan = false, hint = true)
        assertEquals(TodaySize.COMPACT, todaySize())
        assertScrollsWithoutClipping(hint = true)
    }

    @Test @Config(qualifiers = "ar-w393dp-h852dp", fontScale = 1.3f)
    fun `393x852 at font scale 1_3 fits`() {
        showToday(ramadan = false, hint = true)
        assertFillsTheScreen(hint = true)
    }

    @Test @Config(qualifiers = "ar-w412dp-h915dp", fontScale = 1.3f)
    fun `412x915 at font scale 1_3 fits in Ramadan`() {
        showToday(ramadan = true, hint = false)
        assertFillsTheScreen(hint = false)
    }

    // At font scale 1.5 the header wraps and the rows outgrow 48dp: the page scrolls, without clipping.
    @Test @Config(qualifiers = "ar-w393dp-h852dp", fontScale = 1.5f)
    fun `393x852 at font scale 1_5 scrolls without clipping`() {
        showToday(ramadan = false, hint = false)
        assertScrollsWithoutClipping(hint = false)
    }

    @Test @Config(qualifiers = "ar-w412dp-h915dp", fontScale = 1.5f)
    fun `412x915 at font scale 1_5 scrolls without clipping in Ramadan, with the hint`() {
        showToday(ramadan = true, hint = true)
        assertScrollsWithoutClipping(hint = true)
    }

    // After Isha the hero names the Last Third of the Night (#96) under its countdown. Today still fits.
    private fun assertLastThirdShown() =
        compose.onNodeWithText(LAST_THIRD, substring = true, useUnmergedTree = true).assertIsDisplayed()

    /** During suhoor the hero counts to Imsak, which stays on its chip, and Fajr, the next Prayer (#83), keeps its stop. */
    private fun assertImsakAndFajrShown() {
        compose.onNodeWithText("الإمساك", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(prayerRow("الفجر")).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "ar-w360dp-h740dp")
    fun `360x740 after Isha fits at the compact sizes with the Last Third, with the hint`() {
        showToday(ramadan = false, hint = true, night = true)
        assertLastThirdShown()
        assertFillsTheScreen(hint = true)
        assertEquals(TodaySize.COMPACT, todaySize())
    }

    @Test @Config(qualifiers = "ar-w393dp-h852dp")
    fun `393x852 after Isha fits at the regular sizes with the Last Third`() {
        showToday(ramadan = false, hint = false, night = true)
        assertLastThirdShown()
        assertFillsTheScreen(hint = false)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    @Test @Config(qualifiers = "ar-w360dp-h740dp")
    fun `360x740 during suhoor fits with the Last Third on the Ramadan card, with the hint`() {
        showToday(ramadan = true, hint = true, night = true)
        assertLastThirdShown()
        assertImsakAndFajrShown()
        assertFillsTheScreen(hint = true)
        assertEquals(TodaySize.COMPACT, todaySize())
    }

    @Test @Config(qualifiers = "ar-w393dp-h852dp")
    fun `393x852 during suhoor fits at the regular sizes with the Last Third on the Ramadan card`() {
        showToday(ramadan = true, hint = false, night = true)
        assertLastThirdShown()
        assertImsakAndFajrShown()
        assertFillsTheScreen(hint = false)
        assertEquals(TodaySize.REGULAR, todaySize())
    }

    /** The size class Today chose (#101). */
    private fun todaySize(): TodaySize =
        compose.onNode(SemanticsMatcher.keyIsDefined(TodaySizeKey), useUnmergedTree = true).fetchSemanticsNode().config[TodaySizeKey]

    @Test
    @Config(qualifiers = "ar-w393dp-h852dp")
    fun `on a 393x852dp phone Today uses the regular sizes`() {
        log.setPrayed(LocalDate.of(2026, 11, 9), Prayer.FAJR, true)
        show(withToolsRow = true)

        assertEquals(TodaySize.REGULAR, todaySize())
        // The regular hero puts «الصلاة القادمة» over the Prayer's name, and the countdown in the arc below them.
        val label = compose.onNodeWithText("الصلاة القادمة", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val until = compose.onNodeWithText("حتى الأذان", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the arc below the label", until.top >= label.bottom)
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
