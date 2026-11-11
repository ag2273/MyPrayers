package app.mawaqeet.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.Prayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.Executors

/** Opens the database and runs [block] off the main thread: Room refuses blocking queries on it. */
private fun <T> withAdhkar(ctx: Context, block: (RoomAdhkarRepository, RoomPrayerLogRepository) -> T): T {
    val executor = Executors.newSingleThreadExecutor()
    try {
        return executor.submit<T> {
            val db = PrayerLogDatabase.open(ctx)
            try {
                block(RoomAdhkarRepository(db), RoomPrayerLogRepository(db))
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
class RoomAdhkarRepositoryTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 9)

    @After
    fun deleteDatabase() {
        ctx.deleteDatabase(PrayerLogDatabase.FILE_NAME)
    }

    @Test
    fun `counts and the Adhkar Record survive reopening the database`() {
        withAdhkar(ctx) { adhkar, _ ->
            adhkar.setCount(day, "morning", "a", 1)
            adhkar.setCount(day, "morning", "b", 2)
            adhkar.setCount(day, "morning", "b", 3)
            adhkar.setCount(day, "evening", "a", 1)
            adhkar.recordCompleted(day, "morning")
            adhkar.recordCompleted(day, "morning")
        }

        withAdhkar(ctx) { adhkar, _ ->
            assertEquals(mapOf("a" to 1, "b" to 3), adhkar.counts(day, "morning"))
            assertEquals(emptyMap<String, Int>(), adhkar.counts(day.plusDays(1), "morning"))
            assertEquals(setOf("morning"), adhkar.completedOn(day))
            assertEquals(emptySet<String>(), adhkar.completedOn(day.plusDays(1)))
        }
    }

    @Test
    fun `a restart clears that set's counts only and keeps the record`() {
        withAdhkar(ctx) { adhkar, _ ->
            adhkar.setCount(day, "morning", "a", 1)
            adhkar.setCount(day, "evening", "a", 1)
            adhkar.recordCompleted(day, "morning")

            adhkar.clearCounts(day, "morning")

            assertEquals(emptyMap<String, Int>(), adhkar.counts(day, "morning"))
            assertEquals(mapOf("a" to 1), adhkar.counts(day, "evening"))
            assertEquals(setOf("morning"), adhkar.completedOn(day))
        }
    }

    @Test
    fun `clearing older counts keeps today's and every record`() {
        withAdhkar(ctx) { adhkar, _ ->
            adhkar.setCount(day.minusDays(1), "morning", "a", 1)
            adhkar.recordCompleted(day.minusDays(1), "morning")
            adhkar.setCount(day, "morning", "a", 1)

            adhkar.clearCountsBefore(day)

            assertEquals(emptyMap<String, Int>(), adhkar.counts(day.minusDays(1), "morning"))
            assertEquals(mapOf("a" to 1), adhkar.counts(day, "morning"))
            assertEquals(setOf("morning"), adhkar.completedOn(day.minusDays(1)))
        }
    }

    @Test
    fun `every write bumps the change signal`() {
        withAdhkar(ctx) { adhkar, _ ->
            val before = adhkar.changes.value
            adhkar.setCount(day, "morning", "a", 1)
            val afterCount = adhkar.changes.value
            adhkar.clearCounts(day, "morning")

            assertNotEquals(before, afterCount)
            assertNotEquals(afterCount, adhkar.changes.value)
        }
    }

    @Test
    fun `the Adhkar Record lives in the Prayer Log's database file, which the backup rules cover`() {
        withAdhkar(ctx) { adhkar, _ -> adhkar.recordCompleted(day, "morning") }

        val file = ctx.getDatabasePath(PrayerLogDatabase.FILE_NAME)
        val rows = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT slot FROM adhkar_record", null).use { it.count }
        }
        assertEquals(1, rows)
    }

    @Test
    fun `a version 2 database keeps its Prayer Log after the upgrade and gains empty adhkar tables`() {
        // The version 2 schema, as exported in app/schemas/.../2.json.
        val file = ctx.getDatabasePath(PrayerLogDatabase.FILE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            execSQL("CREATE TABLE prayer_log (prayer_day TEXT NOT NULL, prayer TEXT NOT NULL, PRIMARY KEY(prayer_day, prayer))")
            execSQL("CREATE TABLE first_use (id INTEGER NOT NULL, prayer_day TEXT NOT NULL, PRIMARY KEY(id))")
            execSQL("CREATE TABLE past_place (until TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, zone TEXT NOT NULL, PRIMARY KEY(until))")
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table (id,identity_hash) VALUES(42, 'b5adb04044dff2e6c1179a85ea99bab8')")
            execSQL("INSERT INTO prayer_log VALUES ('2026-11-09', 'FAJR')")
            version = 2
            close()
        }

        withAdhkar(ctx) { adhkar, log ->
            assertEquals(setOf(Prayer.FAJR), log.prayedOn(day))
            assertEquals(emptyMap<String, Int>(), adhkar.counts(day, "morning"))
            adhkar.recordCompleted(day, "morning")
            assertEquals(setOf("morning"), adhkar.completedOn(day))
        }
    }
}
