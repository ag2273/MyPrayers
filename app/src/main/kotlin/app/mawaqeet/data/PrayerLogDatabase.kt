package app.mawaqeet.data

import android.content.Context
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

/** Blocking queries: call them off the main thread (the core's repository interface is synchronous). */
@Dao
internal interface PrayerLogDao {
    @Query("SELECT prayer FROM prayer_log WHERE prayer_day = :prayerDay")
    fun prayedOn(prayerDay: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entry: PrayerLogEntry)

    @Query("DELETE FROM prayer_log WHERE prayer_day = :prayerDay AND prayer = :prayer")
    fun delete(prayerDay: String, prayer: String)

    @Query("SELECT prayer_day FROM first_use WHERE id = 0")
    fun firstUse(): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFirstUse(firstUse: FirstUse)
}

/** The local database holding the Prayer Log and the first-use date. */
@Database(entities = [PrayerLogEntry::class, FirstUse::class], version = 1, exportSchema = true)
abstract class PrayerLogDatabase : RoomDatabase() {
    internal abstract fun dao(): PrayerLogDao

    companion object {
        const val FILE_NAME = "prayer_log.db"

        fun open(context: Context): PrayerLogDatabase =
            Room.databaseBuilder(context.applicationContext, PrayerLogDatabase::class.java, FILE_NAME).build()
    }
}
