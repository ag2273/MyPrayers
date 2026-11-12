package app.mawaqeet.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.database.sqlite.SQLiteDatabase
import app.mawaqeet.core.Location
import app.mawaqeet.core.PastPlace
import app.mawaqeet.core.Prayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/** Opens the database and runs [block] off the main thread: Room refuses blocking queries on it. */
private fun <T> withLog(ctx: Context, block: (RoomPrayerLogRepository) -> T): T {
    val executor = Executors.newSingleThreadExecutor()
    try {
        return executor.submit<T> {
            val db = PrayerLogDatabase.open(ctx)
            try {
                block(RoomPrayerLogRepository(db))
            } finally {
                db.close()
            }
        }.get()
    } finally {
        executor.shutdown()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RoomPrayerLogRepositoryTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 8)

    @After
    fun deleteDatabase() {
        ctx.deleteDatabase(PrayerLogDatabase.FILE_NAME)
    }

    @Test
    fun `deleting all entries leaves no marks, replaces the first-use date and keeps past places`() {
        val place = PastPlace(day, Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo")))
        withLog(ctx) { log ->
            log.setPrayed(day, Prayer.FAJR, true)
            log.setPrayed(day.minusDays(3), Prayer.ASR, true)
            log.recordFirstUseIfAbsent(day.minusDays(30))
            log.addPastPlace(place)
            val before = log.changes.value

            log.deleteAllEntries(firstUseFrom = day)

            assertNotEquals(before, log.changes.value)
        }

        withLog(ctx) { log ->
            assertEquals(emptySet<Prayer>(), log.prayedOn(day))
            assertEquals(emptySet<Prayer>(), log.prayedOn(day.minusDays(3)))
            assertEquals(day, log.firstUseDate())
            assertEquals(listOf(place), log.pastPlaces())
        }
    }

    @Test
    fun `the log has an entry once any Prayer Day has a mark, and none after it is unmarked`() {
        withLog(ctx) { log ->
            assertFalse(log.hasAnyEntry())
            log.setPrayed(day.minusDays(5), Prayer.MAGHRIB, true)
        }
        withLog(ctx) { log ->
            assertTrue(log.hasAnyEntry())
            log.setPrayed(day.minusDays(5), Prayer.MAGHRIB, false)
            assertFalse(log.hasAnyEntry())
        }
    }

    @Test
    fun `marks and the first-use date survive reopening the database`() {
        withLog(ctx) { log ->
            log.setPrayed(day, Prayer.FAJR, true)
            log.setPrayed(day, Prayer.ISHA, true)
            log.setPrayed(day, Prayer.ISHA, false)
            log.recordFirstUseIfAbsent(day)
            log.recordFirstUseIfAbsent(day.plusDays(3))
        }

        withLog(ctx) { log ->
            assertEquals(setOf(Prayer.FAJR), log.prayedOn(day))
            assertEquals(emptySet<Prayer>(), log.prayedOn(day.plusDays(1)))
            assertEquals(day, log.firstUseDate())
        }
    }

    @Test
    fun `past places survive reopening the database`() {
        val place = PastPlace(day, Location(21.4225, 39.8262, ZoneId.of("Asia/Riyadh")))
        withLog(ctx) { log -> log.addPastPlace(place) }

        withLog(ctx) { log -> assertEquals(listOf(place), log.pastPlaces()) }
    }

    @Test
    fun `a version 1 database keeps its marks after the upgrade`() {
        // The version 1 schema, as exported in app/schemas/.../1.json.
        val file = ctx.getDatabasePath(PrayerLogDatabase.FILE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            execSQL("CREATE TABLE prayer_log (prayer_day TEXT NOT NULL, prayer TEXT NOT NULL, PRIMARY KEY(prayer_day, prayer))")
            execSQL("CREATE TABLE first_use (id INTEGER NOT NULL, prayer_day TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table (id,identity_hash) VALUES(42, 'b430464c135c7c0c505d6b479cebbb9a')")
            execSQL("INSERT INTO prayer_log VALUES ('2026-11-08', 'FAJR')")
            version = 1
            close()
        }

        withLog(ctx) { log ->
            assertEquals(setOf(Prayer.FAJR), log.prayedOn(day))
            assertEquals(emptyList<PastPlace>(), log.pastPlaces())
        }
    }

    @Test
    fun `every write bumps the change signal`() {
        withLog(ctx) { log ->
            val before = log.changes.value

            log.setPrayed(day, Prayer.DHUHR, true)

            assertNotEquals(before, log.changes.value)
        }
    }
}
