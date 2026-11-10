package app.mawaqeet

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** A widget tap opens its screen, whatever screen the running app was showing. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en")
class WidgetOpensTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var activity: ActivityController<MainActivity>

    @After
    fun close() {
        if (::activity.isInitialized) activity.pause().stop().destroy()
    }

    /** Starts the app as a widget tap would when it isn't running. */
    private fun launch(opens: MainActivity.Opens?) {
        val intent = opens?.let { MainActivity.openIntent(context, it) } ?: Intent(context, MainActivity::class.java)
        activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    /** What Android does when a widget's intent reaches the running activity (CLEAR_TOP | SINGLE_TOP). */
    private fun tapWidget(opens: MainActivity.Opens) {
        activity.newIntent(MainActivity.openIntent(context, opens))
    }

    private fun waitFor(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `the Week Prayer Log widget opens the Prayer Log from Settings, and a today widget then opens Home`() {
        launch(null)
        waitFor("Settings")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("About & your data").performScrollTo().performClick()
        waitFor("Delete Prayer Log")

        tapWidget(MainActivity.Opens.PRAYER_LOG)
        waitFor("This week")

        tapWidget(MainActivity.Opens.TODAY)
        // Only Home lists Sunrise.
        waitFor("Sunrise")
    }

    @Test
    fun `a widget tap that starts the app opens the Prayer Log straight away`() {
        launch(MainActivity.Opens.PRAYER_LOG)
        waitFor("This week")
    }

    @Test
    fun `each screen's intent is distinct, so the two widgets' taps never replace each other`() {
        val today = MainActivity.openIntent(context, MainActivity.Opens.TODAY)
        val log = MainActivity.openIntent(context, MainActivity.Opens.PRAYER_LOG)

        assert(!today.filterEquals(log))
        assert(MainActivity.Opens.from(log) == MainActivity.Opens.PRAYER_LOG)
    }
}
