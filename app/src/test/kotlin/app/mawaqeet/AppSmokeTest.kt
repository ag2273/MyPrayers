package app.mawaqeet

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AppSmokeTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    // Robolectric's default qualifiers are en-rUS, so pin the locale per test.
    @Test
    @Config(qualifiers = "fr")
    fun `an unsupported locale falls back to Arabic`() {
        assertEquals("مواقيت", ctx.getString(R.string.app_name))
        assertEquals("الجمعة", ctx.getString(R.string.prayer_jumuah))
    }

    @Test
    @Config(qualifiers = "en")
    fun `English resources exist`() {
        assertEquals("Mawaqeet", ctx.getString(R.string.app_name))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `home opens right-to-left in Arabic`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertEquals(View.LAYOUT_DIRECTION_RTL, activity.window.decorView.layoutDirection)
            }
        }
    }
}
