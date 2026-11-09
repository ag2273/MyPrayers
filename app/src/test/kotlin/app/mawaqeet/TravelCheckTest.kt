package app.mawaqeet

import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.SavedLocation
import app.mawaqeet.location.DeviceLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

/** Before the activity starts: optionally grants approximate location with a fix in Alexandria, and stores [stored]. */
class DeviceRule(private val granted: Boolean, private val stored: SavedLocation?) : ExternalResource() {
    private val app: Application = ApplicationProvider.getApplicationContext()

    override fun before() {
        val settings = (app as MawaqeetApplication).settings
        runBlocking { if (stored != null) settings.setLocation(stored) else settings.clearLocation() }
        if (!granted) return
        shadowOf(app).grantPermissions(DeviceLocation.PERMISSION)
        val manager = shadowOf(app.getSystemService(LocationManager::class.java))
        manager.setLocationEnabled(true)
        for (provider in listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            manager.setProviderEnabled(provider, true)
            // Alexandria's city centre (GeoNames 361058).
            manager.setLastKnownLocation(provider, Location(provider).apply {
                latitude = 31.2018
                longitude = 29.9158
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                time = System.currentTimeMillis()
            })
        }
    }

    override fun after() {
        runBlocking { (app as MawaqeetApplication).settings.clearLocation() }
        shadowOf(app).denyPermissions(DeviceLocation.PERMISSION)
    }
}

// Riyadh's city centre (GeoNames 108410).
private val MANUAL_RIYADH = SavedLocation(
    app.mawaqeet.core.Location(24.6877, 46.7219, ZoneId.of("Asia/Riyadh")), LocationSource.MANUAL,
    app.mawaqeet.core.CityRef(108410, "Riyadh", "الرياض", "SA", isNear = false),
)

private val AUTOMATIC_RIYADH = MANUAL_RIYADH.copy(source = LocationSource.AUTOMATIC)

private fun MainActivityRule() = createAndroidComposeRule<MainActivity>()

private fun saved(app: Any?) = runBlocking { (app as MawaqeetApplication).settings.savedLocation.first() }

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, MainActivity>.waitForCity(name: String) =
    waitUntil(timeoutMillis = 10_000) { saved(activity.application)?.city?.name == name }

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar")
class AutomaticTravelTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val device = DeviceRule(granted = true, stored = AUTOMATIC_RIYADH)

    @get:Rule(order = 2)
    val compose = MainActivityRule()

    @Test
    fun `an automatic location follows the device and shows a short note`() {
        compose.waitForCity("Alexandria")

        assertEquals(LocationSource.AUTOMATIC, saved(compose.activity.application)?.source)
        compose.onNodeWithText("تم تحديث الموقع إلى الإسكندرية").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar")
class ManualTravelTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val device = DeviceRule(granted = true, stored = MANUAL_RIYADH)

    @get:Rule(order = 2)
    val compose = MainActivityRule()

    @Test
    fun `a manual location asks first and agreeing switches to automatic`() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("هل انتقلت؟").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(MANUAL_RIYADH, saved(compose.activity.application))
        compose.onNodeWithText("يبدو أنك الآن قرب الإسكندرية. هل تريد تحديث الموقع واستخدام موقعك تلقائيًا من الآن؟")
            .assertIsDisplayed()

        compose.onNodeWithText("تحديث").performClick()

        compose.waitForCity("Alexandria")
        assertEquals(LocationSource.AUTOMATIC, saved(compose.activity.application)?.source)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar")
class LocationDeniedTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val device = DeviceRule(granted = false, stored = null)

    @get:Rule(order = 2)
    val compose = MainActivityRule()

    @After
    fun nothingStored() = assertNull(saved(compose.activity.application))

    @Test
    fun `using the device location explains first and declining keeps city search`() {
        compose.onNodeWithText("الإعدادات").performClick()
        compose.onNodeWithText("الموقع").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("جارٍ تحميل قائمة المدن…").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("استخدام موقعي الحالي").performClick()
        compose.onNodeWithText(
            "يُستخدم موقعك فقط لحساب مواقيت الصلاة، ولا يغادر هاتفك أبدًا. نطلب الموقع التقريبي فقط، ولا نقرؤه إلا والتطبيق مفتوح.",
        ).assertIsDisplayed()
        compose.onNodeWithText("ليس الآن").performClick()

        compose.onNode(hasSetTextAction() and hasText("ابحث عن مدينتك")).assertIsDisplayed()
    }
}
