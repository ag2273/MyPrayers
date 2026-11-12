package app.mawaqeet.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate

/**
 * One stored setting's value, as the app's settings store holds it. [Whole] and [Decimal] stay apart so a restored
 * value goes back with its own type.
 */
sealed interface SettingValue {
    data class Flag(val value: Boolean) : SettingValue

    data class Whole(val value: Long) : SettingValue

    data class Decimal(val value: Double) : SettingValue

    data class Text(val value: String) : SettingValue

    data class TextSet(val value: Set<String>) : SettingValue
}

/**
 * The Prayer Log as a Backup File holds it: the first-use date (null before it is recorded), the prayed Prayers
 * of each Prayer Day (only days with at least one), and every [PastPlace].
 */
data class PrayerLogBackup(
    val firstUse: LocalDate?,
    val prayed: Map<LocalDate, Set<Prayer>>,
    val pastPlaces: List<PastPlace>,
)

/** The Adhkar Record as a Backup File holds it: its first-use date and the completed slots of each Prayer Day. */
data class AdhkarRecordBackup(
    val firstUse: LocalDate?,
    /** Slot keys as [AdhkarRepository] stores them (`morning`, `after-prayer:FAJR`, …). */
    val completed: Map<LocalDate, Set<String>>,
)

/**
 * Everything a Backup File carries. [settings] are the app's stored settings by their store key (every setting,
 * the location and Calculation Method included); the app leaves out what belongs to this phone only (Custom Adhan
 * files, the last export date, one-off notices).
 */
data class BackupSnapshot(
    val settings: Map<String, SettingValue>,
    val prayerLog: PrayerLogBackup,
    val adhkarRecord: AdhkarRecordBackup,
)

/**
 * The Backup File (#86): one UTF-8 JSON document the user saves wherever they choose. Its shape, version [VERSION]:
 *
 * ```
 * {
 *   "format": "mawaqeet-backup",
 *   "version": 1,
 *   "exportedAt": "2026-11-12T18:30:00Z",
 *   "settings": { "<store key>": true | 20 | 30.04 | "EGYPTIAN" | ["ASR", …], … },
 *   "prayerLog": {
 *     "firstUse": "2026-09-01" | null,
 *     "prayed": { "2026-11-08": ["FAJR", "ISHA"], … },
 *     "pastPlaces": [ { "until": "2026-10-15", "latitude": 21.42, "longitude": 39.82, "zone": "Asia/Riyadh" }, … ]
 *   },
 *   "adhkarRecord": {
 *     "firstUse": "2026-10-01" | null,
 *     "completed": { "2026-11-08": ["after-prayer:FAJR", "morning"], … }
 *   }
 * }
 * ```
 *
 * Dates are ISO Prayer Days, Prayers their enum names; keys, days and set members are sorted, so the same data
 * always gives the same file. A whole number in `settings` is a [SettingValue.Whole], one written with a decimal
 * point or exponent a [SettingValue.Decimal]. A change readers of this version cannot ignore bumps [VERSION];
 * fields added within a version are ignored by its readers.
 */
object Backup {
    const val FORMAT = "mawaqeet-backup"
    const val VERSION = 1

    /** The suggested file name for a Backup File exported on [date]: `mawaqeet-backup-2026-11-12.json`. */
    fun fileName(date: LocalDate): String = "mawaqeet-backup-$date.json"

    /** [snapshot] as a Backup File's bytes, stamped [exportedAt]. */
    fun export(snapshot: BackupSnapshot, exportedAt: Instant): ByteArray {
        val document = JsonObject(
            linkedMapOf(
                "format" to JsonPrimitive(FORMAT),
                "version" to JsonPrimitive(VERSION),
                "exportedAt" to JsonPrimitive(exportedAt.toString()),
                "settings" to JsonObject(snapshot.settings.toSortedMap().mapValues { (_, value) -> value.toJson() }),
                "prayerLog" to snapshot.prayerLog.toJson(),
                "adhkarRecord" to snapshot.adhkarRecord.toJson(),
            ),
        )
        return json.encodeToString(JsonElement.serializer(), document).toByteArray(Charsets.UTF_8)
    }

    private val json = Json { prettyPrint = true }

    private fun SettingValue.toJson(): JsonElement = when (this) {
        is SettingValue.Flag -> JsonPrimitive(value)
        is SettingValue.Whole -> JsonPrimitive(value)
        is SettingValue.Decimal -> JsonPrimitive(value)
        is SettingValue.Text -> JsonPrimitive(value)
        is SettingValue.TextSet -> strings(value)
    }

    private fun PrayerLogBackup.toJson() = JsonObject(
        linkedMapOf(
            "firstUse" to date(firstUse),
            "prayed" to days(prayed.filterValues { it.isNotEmpty() }) { prayers -> strings(prayers.sorted().map { it.name }) },
            "pastPlaces" to JsonArray(
                pastPlaces.sortedBy { it.until }.map { place ->
                    JsonObject(
                        linkedMapOf(
                            "until" to JsonPrimitive(place.until.toString()),
                            "latitude" to JsonPrimitive(place.location.latitude),
                            "longitude" to JsonPrimitive(place.location.longitude),
                            "zone" to JsonPrimitive(place.location.zone.id),
                        ),
                    )
                },
            ),
        ),
    )

    private fun AdhkarRecordBackup.toJson() = JsonObject(
        linkedMapOf(
            "firstUse" to date(firstUse),
            "completed" to days(completed.filterValues { it.isNotEmpty() }) { strings(it) },
        ),
    )

    private fun date(date: LocalDate?): JsonElement = date?.let { JsonPrimitive(it.toString()) } ?: JsonNull

    private fun <T> days(byDay: Map<LocalDate, T>, value: (T) -> JsonElement) =
        JsonObject(byDay.toSortedMap().entries.associate { (day, v) -> day.toString() to value(v) })

    private fun strings(values: Collection<String>) = JsonArray(values.sorted().map(::JsonPrimitive))
}
