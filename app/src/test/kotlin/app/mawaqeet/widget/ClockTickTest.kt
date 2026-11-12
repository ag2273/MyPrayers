package app.mawaqeet.widget

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/** The Clock widgets' minute re-render stops with the last Clock widget (#60). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ClockTickTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    @Test
    fun `removing the last Clock widget cancels the minute re-render before its broadcast ends`() {
        ClockTickReceiver.schedule(context, Instant.now().plusSeconds(60))
        assertEquals(1, alarms.size)

        GoldClockWidgetReceiver().onDisabled(context)

        assertEquals(0, alarms.size)
    }
}
