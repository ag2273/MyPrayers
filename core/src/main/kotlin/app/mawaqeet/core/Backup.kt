package app.mawaqeet.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

/** What [Backup.restore] made of a Backup File. */
sealed interface RestoreResult {
    /** The file was read: [snapshot] is what the phone should hold now (see [Backup.restore]). */
    data class Restored(val snapshot: BackupSnapshot) : RestoreResult

    /** Not a Backup File, or a damaged one: nothing may change. */
    data object Damaged : RestoreResult

    /** A Backup File from a newer app version this one can't read: nothing may change, the app needs updating. */
    data object NewerVersion : RestoreResult
}

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

    /** Settings keys of a Custom Adhan file (`custom_adhan_full`, …): a content URI on one phone, never in a file. */
    const val CUSTOM_ADHAN_PREFIX = "custom_adhan_"

    /**
     * Settings keys about one phone or install, never in a Backup File or taken from one: the last export date, the
     * possibly-stale location hint, onboarding, and the Fix Alerts steps (they depend on the phone).
     */
    val PHONE_ONLY_KEYS = setOf("backup_last_export", "location_maybe_stale", "onboarding_done", "fix_alerts_done")

    /** Whether the setting stored under [key] travels in a Backup File. */
    fun restorable(key: String): Boolean = key !in PHONE_ONLY_KEYS && !key.startsWith(CUSTOM_ADHAN_PREFIX)

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

    /**
     * Reads a Backup File's [bytes] and merges it with what the phone holds now, [current]. The result is what the
     * phone should hold after the restore; [current] itself is never changed.
     */
    fun restore(bytes: ByteArray, current: BackupSnapshot): RestoreResult {
        val file = try {
            read(bytes)
        } catch (_: NewerVersionException) {
            return RestoreResult.NewerVersion
        } catch (_: IllegalArgumentException) {
            // Kotlinx's SerializationException, an unknown Prayer and our own require() all land here.
            return RestoreResult.Damaged
        } catch (_: DateTimeException) {
            return RestoreResult.Damaged
        }
        return RestoreResult.Restored(merge(file, current))
    }

    private fun merge(file: BackupSnapshot, phone: BackupSnapshot) = BackupSnapshot(
        settings = file.settings.filterKeys(::restorable),
        prayerLog = PrayerLogBackup(
            firstUse = earlier(file.prayerLog.firstUse, phone.prayerLog.firstUse),
            prayed = union(file.prayerLog.prayed, phone.prayerLog.prayed),
            pastPlaces = pastPlaces(file.prayerLog.pastPlaces, phone.prayerLog.pastPlaces),
        ),
        adhkarRecord = AdhkarRecordBackup(
            firstUse = earlier(file.adhkarRecord.firstUse, phone.adhkarRecord.firstUse),
            completed = union(file.adhkarRecord.completed, phone.adhkarRecord.completed),
        ),
    )

    /**
     * The phone's own past places all stay; the file's fill only the Prayer Days before the phone's earliest one ends
     * (each [PastPlace] covers the days before its `until`). A phone that never moved takes them all.
     */
    private fun pastPlaces(file: List<PastPlace>, phone: List<PastPlace>): List<PastPlace> {
        val phoneEarliest = phone.minOfOrNull { it.until } ?: return file
        return file.filter { it.until < phoneEarliest } + phone
    }

    private fun earlier(a: LocalDate?, b: LocalDate?): LocalDate? = listOfNotNull(a, b).minOrNull()

    /** Every day's members from both sides: marked on either stays marked. */
    private fun <T> union(a: Map<LocalDate, Set<T>>, b: Map<LocalDate, Set<T>>): Map<LocalDate, Set<T>> =
        (a.keys + b.keys).associateWith { day -> a[day].orEmpty() + b[day].orEmpty() }.filterValues { it.isNotEmpty() }

    private class NewerVersionException : Exception()

    /** The file's own snapshot; throws for anything that isn't a Backup File this version can read. */
    private fun read(bytes: ByteArray): BackupSnapshot {
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).obj()
        require(root["format"].text() == FORMAT) { "not a Backup File" }
        val version = requireNotNull((root["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull) { "no version" }
        if (version > VERSION) throw NewerVersionException()
        require(version >= 1) { "unknown version" }

        val log = root["prayerLog"].obj()
        val adhkar = root["adhkarRecord"].obj()
        return BackupSnapshot(
            settings = root["settings"].obj().mapValues { (_, value) -> value.setting() },
            prayerLog = PrayerLogBackup(
                firstUse = log["firstUse"].dateOrNull(),
                prayed = log["prayed"].days { Prayer.valueOf(it) },
                pastPlaces = log["pastPlaces"].array().map { element ->
                    val place = element.obj()
                    PastPlace(
                        LocalDate.parse(place["until"].text()),
                        Location(place["latitude"].number(), place["longitude"].number(), ZoneId.of(place["zone"].text())),
                    )
                },
            ),
            adhkarRecord = AdhkarRecordBackup(
                firstUse = adhkar["firstUse"].dateOrNull(),
                completed = adhkar["completed"].days { it },
            ),
        )
    }

    private fun JsonElement?.obj(): JsonObject = requireNotNull(this as? JsonObject) { "expected an object" }

    private fun JsonElement?.array(): JsonArray = requireNotNull(this as? JsonArray) { "expected an array" }

    private fun JsonElement?.text(): String =
        requireNotNull((this as? JsonPrimitive)?.takeIf { it.isString }?.content) { "expected a string" }

    private fun JsonElement?.number(): Double =
        requireNotNull((this as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull) { "expected a number" }

    private fun JsonElement?.dateOrNull(): LocalDate? = if (this == null || this is JsonNull) null else LocalDate.parse(text())

    private fun <T> JsonElement?.days(member: (String) -> T): Map<LocalDate, Set<T>> =
        obj().entries
            .associate { (day, members) -> LocalDate.parse(day) to members.array().map { member(it.text()) }.toSet() }
            .filterValues { it.isNotEmpty() }

    /** A whole number is a [SettingValue.Whole]; one written with a point or exponent is a [SettingValue.Decimal]. */
    private fun JsonElement.setting(): SettingValue {
        if (this is JsonArray) return SettingValue.TextSet(map { it.text() }.toSet())
        require(this is JsonPrimitive && this !is JsonNull) { "not a setting value" }
        if (isString) return SettingValue.Text(content)
        booleanOrNull?.let { return SettingValue.Flag(it) }
        val whole = content.none { it == '.' || it == 'e' || it == 'E' }
        if (whole) longOrNull?.let { return SettingValue.Whole(it) }
        return SettingValue.Decimal(requireNotNull(doubleOrNull) { "not a number" })
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
