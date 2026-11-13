package app.mawaqeet

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.job
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app's process-long work ends with the Application. On a phone `onTerminate` never runs; under Robolectric it
 * runs after every test, and each test gets a new Application while the settings DataStore is one per JVM, so an old
 * Application still collecting it would reschedule into a deleted test folder and fail a later test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AppScopeTest {
    @Test
    fun `when the Application terminates its background work is cancelled and finished`() {
        val app = ApplicationProvider.getApplicationContext<MawaqeetApplication>()

        app.onTerminate()

        assertTrue(app.appScope.coroutineContext.job.isCompleted)
    }
}
