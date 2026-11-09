package app.mawaqeet.data

import android.content.Context
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
    fun `every write bumps the change signal`() {
        withLog(ctx) { log ->
            val before = log.changes.value

            log.setPrayed(day, Prayer.DHUHR, true)

            assertNotEquals(before, log.changes.value)
        }
    }
}
