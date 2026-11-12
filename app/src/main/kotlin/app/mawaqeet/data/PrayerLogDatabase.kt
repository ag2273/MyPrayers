package app.mawaqeet.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * One Prayer marked as prayed. A Prayer Day is stored as its ISO date (`2026-11-08`), a Prayer as its enum name.
 * Not-marked Prayers have no row.
 */
@Entity(tableName = "prayer_log", primaryKeys = ["prayer_day", "prayer"])
internal data class PrayerLogEntry(
    @ColumnInfo(name = "prayer_day") val prayerDay: String,
    val prayer: String,
)

/** Single-row table holding the first-use Prayer Day. */
@Entity(tableName = "first_use")
internal data class FirstUse(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "prayer_day") val prayerDay: String,
)

/**
 * A past place (core `PastPlace`): the location in effect for Prayer Days before [until] (ISO date). The zone is
 * an IANA id.
 */
@Entity(tableName = "past_place")
internal data class PastPlaceEntry(
    @PrimaryKey val until: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
)

/** Blocking queries: call them off the main thread (the core's repository interface is synchronous). */
@Dao
internal interface PrayerLogDao {
    @Query("SELECT prayer FROM prayer_log WHERE prayer_day = :prayerDay")
    fun prayedOn(prayerDay: String): List<String>

    /** Every prayed entry, for the Backup File. */
    @Query("SELECT * FROM prayer_log")
    fun allEntries(): List<PrayerLogEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entry: PrayerLogEntry)

    @Query("DELETE FROM prayer_log WHERE prayer_day = :prayerDay AND prayer = :prayer")
    fun delete(prayerDay: String, prayer: String)

    @Query("DELETE FROM prayer_log")
    fun deleteAllEntries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceFirstUse(firstUse: FirstUse)

    @Query("SELECT prayer_day FROM first_use WHERE id = 0")
    fun firstUse(): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFirstUse(firstUse: FirstUse)

    @Query("SELECT * FROM past_place")
    fun pastPlaces(): List<PastPlaceEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPastPlace(place: PastPlaceEntry)
}

/**
 * How many times one dhikr of an Adhkar Set was counted on a Prayer Day (ISO date). [slot] names the set (core
 * `AdhkarRepository`). Rows of past Prayer Days are deleted: progress lasts only for its Prayer Day.
 */
@Entity(tableName = "adhkar_progress", primaryKeys = ["prayer_day", "slot", "dhikr_id"])
internal data class AdhkarProgressEntry(
    @ColumnInfo(name = "prayer_day") val prayerDay: String,
    val slot: String,
    @ColumnInfo(name = "dhikr_id") val dhikrId: String,
    val count: Int,
)

/** The Adhkar Record: one Adhkar Set slot completed on a Prayer Day (ISO date). Not completed has no row. */
@Entity(tableName = "adhkar_record", primaryKeys = ["prayer_day", "slot"])
internal data class AdhkarRecordEntry(
    @ColumnInfo(name = "prayer_day") val prayerDay: String,
    val slot: String,
)

/** Single-row table holding the Adhkar first-use Prayer Day (the first count): the Adhkar Record counts from it. */
@Entity(tableName = "adhkar_first_use")
internal data class AdhkarFirstUse(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "prayer_day") val prayerDay: String,
)

/** Blocking queries: call them off the main thread (the core's repository interface is synchronous). */
@Dao
internal interface AdhkarDao {
    @Query("SELECT * FROM adhkar_progress WHERE prayer_day = :prayerDay AND slot = :slot")
    fun counts(prayerDay: String, slot: String): List<AdhkarProgressEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putCount(entry: AdhkarProgressEntry)

    @Query("DELETE FROM adhkar_progress WHERE prayer_day = :prayerDay AND slot = :slot")
    fun clearCounts(prayerDay: String, slot: String)

    /** ISO dates sort as text. Returns how many rows went. */
    @Query("DELETE FROM adhkar_progress WHERE prayer_day < :prayerDay")
    fun clearCountsBefore(prayerDay: String): Int

    @Query("SELECT slot FROM adhkar_record WHERE prayer_day = :prayerDay")
    fun completedOn(prayerDay: String): List<String>

    /** The whole Adhkar Record, for the Backup File. */
    @Query("SELECT * FROM adhkar_record")
    fun allRecords(): List<AdhkarRecordEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRecord(entry: AdhkarRecordEntry)

    @Query("SELECT prayer_day FROM adhkar_first_use WHERE id = 0")
    fun firstUse(): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFirstUse(firstUse: AdhkarFirstUse)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceFirstUse(firstUse: AdhkarFirstUse)

    @Query("DELETE FROM adhkar_record")
    fun deleteRecord()

    @Query("DELETE FROM adhkar_progress")
    fun deleteAllCounts()

    /** Settings: Delete Adhkar Record, all at once so no screen sees half of it. */
    @Transaction
    fun deleteRecord(firstUseFrom: String) {
        deleteRecord()
        deleteAllCounts()
        replaceFirstUse(AdhkarFirstUse(prayerDay = firstUseFrom))
    }
}

/**
 * The local database holding the Prayer Log, the first-use date, the past places of Prayer Days, and the Adhkar
 * progress and Adhkar Record (in this same file, so they share the Prayer Log's backup and device-transfer rules).
 * Every schema change bumps [version] with a migration (version 2 added `past_place`, version 3 the adhkar
 * tables, version 4 `adhkar_first_use`; all auto-migrations).
 */
@Database(
    entities = [
        PrayerLogEntry::class, FirstUse::class, PastPlaceEntry::class,
        AdhkarProgressEntry::class, AdhkarRecordEntry::class, AdhkarFirstUse::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class PrayerLogDatabase : RoomDatabase() {
    internal abstract fun dao(): PrayerLogDao

    internal abstract fun adhkarDao(): AdhkarDao

    companion object {
        const val FILE_NAME = "prayer_log.db"

        fun open(context: Context): PrayerLogDatabase =
            Room.databaseBuilder(context.applicationContext, PrayerLogDatabase::class.java, FILE_NAME).build()
    }
}
