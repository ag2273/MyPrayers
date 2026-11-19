package app.mawaqeet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.audio.AdhkarAudio
import app.mawaqeet.audio.FakeCipher
import app.mawaqeet.audio.FakeSite
import app.mawaqeet.audio.TEST_KEY
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarCatalog
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.adhkar.AdhkarAudioPage
import app.mawaqeet.ui.adhkar.AdhkarScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val audioCatalog = AdhkarCatalog.parse(
    "# mawaqeet-adhkar-v1\n" +
        "morning\tone\t1\tرواه مسلم\tاللَّهُمَّ بِكَ أَصْبَحْنَا\n" +
        "morning\tthree\t3\tرواه أبو داود\tبِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ\n",
)

/** Listening to the adhkar: the one-time download offer, its sheet, and the player in the dhikr card. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar")
class AdhkarAudioScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeAdhkarRepository()
    private val haptics = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
        var count = 0
        override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
            count++
        }
    }
    private val adhkar = Adhkar(
        audioCatalog,
        PrayerCore(
            PrayerSettings.Default,
            Clock.fixed(LocalDateTime.parse("2026-11-09T07:00").atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC),
            EmptyLog,
        ),
        repo,
    )
    private val site = FakeSite(
        mapOf("r1" to ByteArray(64) { 1 }, "r3" to ByteArray(64) { 3 }),
        listOf(Triple("morning", "one", "r1"), Triple("morning", "three", "r3")),
    )
    private val app = ApplicationProvider.getApplicationContext<MawaqeetApplication>()
    private val audio = AdhkarAudio(app, site.pack, TEST_KEY, FakeCipher(), site.fetcher).also { app.adhkarAudioOverride = it }

    init {
        // Robolectric doesn't run App Startup, which sets WorkManager up on a phone.
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun waitForText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun showReader() {
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.WESTERN) {
                    CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                        AdhkarScreen(adhkar, repo.changes)
                    }
                }
            }
        }
        waitForText("أذكار الصباح")
        compose.onNodeWithText("أذكار الصباح").performClick()
        waitForText("الذكر 1 من 2")
    }

    @Test
    fun `before the download the reader offers it, and Not now stops offering it`() {
        showReader()
        compose.onNodeWithText("استمع إلى الأذكار").assertIsDisplayed()
        compose.onNodeWithText("استمع لهذا الذكر").assertIsDisplayed()

        compose.onNodeWithText("تنزيل").performClick()
        waitForText("تنزيل أصوات الأذكار")
        compose.onNodeWithText("ليس الآن").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("استمع إلى الأذكار")).fetchSemanticsNodes().isEmpty() }
        assertTrue(audio.prefs.value.offerDismissed)
        // The card's own button can still offer it, and no tap on it counted the dhikr.
        compose.onNodeWithContentDescription("استمع لهذا الذكر").performClick()
        waitForText("تنزيل أصوات الأذكار")
        assertEquals(0, haptics.count)
    }

    @Test
    fun `once downloaded each dhikr has a play button and the offer is gone`() {
        runBlocking { audio.store.download() }
        showReader()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("تشغيل الذكر")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasText("استمع إلى الأذكار")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("تشغيل الذكر التالي تلقائيًا").assertExists()
    }

    @Test
    fun `the settings page says what is on the phone and deletes it`() {
        runBlocking { audio.store.download() }
        compose.setContent {
            MawaqeetTheme { ProvideDisplayFormatter(DigitStyle.WESTERN) { androidx.compose.foundation.layout.Column { AdhkarAudioPage(audio) } } }
        }
        waitForText("تم التنزيل")
        compose.onNodeWithText("حذف الأصوات من الهاتف").performClick()
        compose.onNodeWithText("حذف").performClick()
        waitForText("لم تُنزَّل بعد")
        assertEquals(0, audio.store.state.value.ready)
    }
}
