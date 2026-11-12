package app.mawaqeet.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.City
import app.mawaqeet.core.CityDirectory
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.PastPlace
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerLogRepository
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.core.SavedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// City centres (GeoNames).
private val RIYADH = City(108410, "Riyadh", "الرياض", "SA", 24.6877, 46.7219, ZoneId.of("Asia/Riyadh"))
private val ALEXANDRIA = City(361058, "Alexandria", "الإسكندرية", "EG", 31.2018, 29.9158, ZoneId.of("Africa/Cairo"))
private val CITIES = CityDirectory(listOf(RIYADH, ALEXANDRIA))

private class FakeLog : PrayerLogRepository {
    val places = mutableListOf<PastPlace>()
    override fun prayedOn(prayerDay: LocalDate): Set<Prayer> = emptySet()
    override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) = Unit
    override fun hasAnyEntry() = false
    override fun firstUseDate(): LocalDate? = null
    override fun recordFirstUseIfAbsent(prayerDay: LocalDate) = Unit
    override fun deleteAllEntries(firstUseFrom: LocalDate) = Unit
    override fun pastPlaces(): List<PastPlace> = places.toList()
    override fun addPastPlace(place: PastPlace) {
        places += place
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LocationUpdaterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val log = FakeLog()
    private val clock = Clock.fixed(Instant.parse("2026-11-09T09:00:00Z"), ZoneOffset.UTC)

    private fun <T> withUpdater(block: suspend (LocationUpdater, SettingsRepository) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "s.preferences_pb") })
            return runBlocking { block(LocationUpdater(settings, log, clock) { CITIES }, settings) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an automatic location updates to the nearest city and fixes the past days`() = withUpdater { updater, settings ->
        settings.setLocation(SavedLocation(RIYADH.location, LocationSource.AUTOMATIC, RIYADH.ref()))

        val result = updater.onDevicePosition(31.20, 29.92)

        val saved = settings.savedLocation.first()!!
        assertEquals(LocationCheckResult.Updated(saved), result)
        assertEquals("Alexandria", saved.city?.name)
        assertEquals(LocationSource.AUTOMATIC, saved.source)
        assertEquals(listOf(PastPlace(LocalDate.of(2026, 11, 9), RIYADH.location)), log.places)
    }

    @Test
    fun `a manual location is only proposed, and accepting it makes it automatic`() = withUpdater { updater, settings ->
        val manual = SavedLocation(RIYADH.location, LocationSource.MANUAL, RIYADH.ref())
        settings.setLocation(manual)

        val result = updater.onDevicePosition(31.20, 29.92) as LocationCheckResult.Ask

        assertEquals(manual, settings.savedLocation.first())
        assertEquals(LocationSource.AUTOMATIC, result.proposed.source)
        updater.change(result.proposed)
        assertEquals(result.proposed, settings.savedLocation.first())
    }

    @Test
    fun `staying put changes nothing and clears the possibly-stale flag`() = withUpdater { updater, settings ->
        settings.setLocation(SavedLocation(RIYADH.location, LocationSource.AUTOMATIC, RIYADH.ref()))
        settings.setLocationMaybeStale(true)

        assertEquals(LocationCheckResult.Unchanged, updater.onDevicePosition(24.69, 46.72))

        assertEquals(false, settings.locationMaybeStale.first())
        assertEquals(emptyList<PastPlace>(), log.places)
    }

    @Test
    fun `the first location with no stored one records the default as the past place`() = withUpdater { updater, settings ->
        assertNull(settings.savedLocation.first())

        updater.onDevicePosition(31.20, 29.92)

        assertEquals(listOf(PastPlace(LocalDate.of(2026, 11, 9), PrayerSettings.Default.location)), log.places)
    }
}
