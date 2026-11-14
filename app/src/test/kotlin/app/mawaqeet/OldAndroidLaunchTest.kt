package app.mawaqeet

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app opens on the oldest Android versions it still supports (a Samsung Note 8 runs Android 9). */
@RunWith(AndroidJUnit4::class)
class OldAndroidLaunchTest {
    private fun launches() {
        ActivityScenario.launch(MainActivity::class.java).use { it.moveToState(Lifecycle.State.RESUMED) }
    }

    @Test @Config(sdk = [28], qualifiers = "ar")
    fun `it opens on Android 9`() = launches()

    @Test @Config(sdk = [26], qualifiers = "ar")
    fun `it opens on Android 8`() = launches()
}
