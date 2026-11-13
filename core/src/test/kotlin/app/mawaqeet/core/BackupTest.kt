package app.mawaqeet.core

import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupTest {
    private val snapshot = BackupSnapshot(
        settings = mapOf(
            "calculation_method" to SettingValue.Text("EGYPTIAN"),
            "location_latitude" to SettingValue.Decimal(30.0444),
            "location_longitude" to SettingValue.Decimal(31.0),
            "location_city_id" to SettingValue.Whole(360630),
            "location_city_name_ar" to SettingValue.Text("القاهرة"),
            "alert_fajr_reminder_minutes" to SettingValue.Whole(20),
            "alarm_clock_mode" to SettingValue.Flag(true),
            "prayer_silence_off" to SettingValue.TextSet(setOf("MAGHRIB", "ASR")),
        ),
        prayerLog = PrayerLogBackup(
            firstUse = LocalDate.parse("2026-09-01"),
            prayed = mapOf(
                LocalDate.parse("2026-11-08") to setOf(Prayer.ISHA, Prayer.FAJR),
                LocalDate.parse("2026-11-07") to setOf(Prayer.DHUHR),
            ),
            pastPlaces = listOf(
                PastPlace(LocalDate.parse("2026-10-15"), Location(21.4225, 39.8262, ZoneId.of("Asia/Riyadh"))),
            ),
        ),
        adhkarRecord = AdhkarRecordBackup(
            firstUse = LocalDate.parse("2026-10-01"),
            completed = mapOf(LocalDate.parse("2026-11-08") to setOf("morning", "after-prayer:FAJR")),
        ),
    )

    @Test
    fun `an export is the documented versioned JSON, with every section in a fixed order`() {
        val bytes = Backup.export(snapshot, exportedAt = Instant.parse("2026-11-12T18:30:00Z"))

        // The shape documented on Backup: written by hand from the spec, not by the code.
        val expected = """
            {
              "format": "mawaqeet-backup",
              "version": 1,
              "exportedAt": "2026-11-12T18:30:00Z",
              "settings": {
                "alarm_clock_mode": true,
                "alert_fajr_reminder_minutes": 20,
                "calculation_method": "EGYPTIAN",
                "location_city_id": 360630,
                "location_city_name_ar": "القاهرة",
                "location_latitude": 30.0444,
                "location_longitude": 31.0,
                "prayer_silence_off": ["ASR", "MAGHRIB"]
              },
              "prayerLog": {
                "firstUse": "2026-09-01",
                "prayed": {
                  "2026-11-07": ["DHUHR"],
                  "2026-11-08": ["FAJR", "ISHA"]
                },
                "pastPlaces": [
                  { "until": "2026-10-15", "latitude": 21.4225, "longitude": 39.8262, "zone": "Asia/Riyadh" }
                ]
              },
              "adhkarRecord": {
                "firstUse": "2026-10-01",
                "completed": {
                  "2026-11-08": ["after-prayer:FAJR", "morning"]
                }
              }
            }
        """.trimIndent()
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)))
        // Keys in order, so two exports of the same data are the same file.
        assertEquals(
            listOf("format", "version", "exportedAt", "settings", "prayerLog", "adhkarRecord"),
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).let { (it as kotlinx.serialization.json.JsonObject).keys.toList() },
        )
    }

    @Test
    fun `nothing recorded yet exports null first-use dates and empty sections`() {
        val empty = BackupSnapshot(emptyMap(), PrayerLogBackup(null, emptyMap(), emptyList()), AdhkarRecordBackup(null, emptyMap()))

        val json = Backup.export(empty, Instant.parse("2026-11-12T00:00:00Z")).toString(Charsets.UTF_8)

        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "format": "mawaqeet-backup", "version": 1, "exportedAt": "2026-11-12T00:00:00Z", "settings": {},
                  "prayerLog": { "firstUse": null, "prayed": {}, "pastPlaces": [] },
                  "adhkarRecord": { "firstUse": null, "completed": {} }
                }
                """.trimIndent(),
            ),
            Json.parseToJsonElement(json),
        )
    }

    @Test
    fun `the file is named after the day it was exported`() {
        assertEquals("mawaqeet-backup-2026-11-12.json", Backup.fileName(LocalDate.parse("2026-11-12")))
    }

    private val nothing = BackupSnapshot(emptyMap(), PrayerLogBackup(null, emptyMap(), emptyList()), AdhkarRecordBackup(null, emptyMap()))

    private fun restored(bytes: ByteArray, current: BackupSnapshot = nothing): BackupSnapshot =
        (Backup.restore(bytes, current) as RestoreResult.Restored).snapshot

    private fun file(json: String) = json.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun `restoring an export onto a phone with nothing recorded gives back exactly what was exported`() {
        val bytes = Backup.export(snapshot, Instant.parse("2026-11-12T18:30:00Z"))

        assertEquals(snapshot, restored(bytes))
    }

    private val day7 = LocalDate.parse("2026-11-07")
    private val day8 = LocalDate.parse("2026-11-08")
    private val day9 = LocalDate.parse("2026-11-09")

    @Test
    fun `a Prayer or Adhkar Set marked in the file or on the phone stays marked, and nothing is unmarked`() {
        val phone = nothing.copy(
            prayerLog = PrayerLogBackup(null, mapOf(day8 to setOf(Prayer.ASR), day9 to setOf(Prayer.FAJR)), emptyList()),
            adhkarRecord = AdhkarRecordBackup(null, mapOf(day8 to setOf("evening"))),
        )

        val merged = restored(Backup.export(snapshot, Instant.EPOCH), current = phone)

        assertEquals(
            mapOf(day7 to setOf(Prayer.DHUHR), day8 to setOf(Prayer.FAJR, Prayer.ASR, Prayer.ISHA), day9 to setOf(Prayer.FAJR)),
            merged.prayerLog.prayed,
        )
        assertEquals(mapOf(day8 to setOf("after-prayer:FAJR", "morning", "evening")), merged.adhkarRecord.completed)
    }

    @Test
    fun `each first-use date is the earlier of the file's and the phone's, and a missing one never wins`() {
        // The file: Prayer Log from 2026-09-01, Adhkar Record from 2026-10-01.
        val phone = nothing.copy(
            prayerLog = PrayerLogBackup(LocalDate.parse("2026-08-15"), emptyMap(), emptyList()),
            adhkarRecord = AdhkarRecordBackup(LocalDate.parse("2026-11-01"), emptyMap()),
        )
        val merged = restored(Backup.export(snapshot, Instant.EPOCH), current = phone)
        assertEquals(LocalDate.parse("2026-08-15"), merged.prayerLog.firstUse)
        assertEquals(LocalDate.parse("2026-10-01"), merged.adhkarRecord.firstUse)

        val fileWithout = snapshot.copy(prayerLog = snapshot.prayerLog.copy(firstUse = null))
        assertEquals(LocalDate.parse("2026-08-15"), restored(Backup.export(fileWithout, Instant.EPOCH), phone).prayerLog.firstUse)
    }

    @Test
    fun `the file's settings replace the phone's wholesale, but its Custom Adhan files and last export date are never restored`() {
        val phone = nothing.copy(
            settings = mapOf(
                "calculation_method" to SettingValue.Text("UMM_AL_QURA"),
                "worship_kahf_enabled" to SettingValue.Flag(true),
            ),
        )
        // Written by hand: an export never holds these, but a file edited elsewhere might.
        val bytes = file(
            """
            {
              "format": "mawaqeet-backup", "version": 1, "exportedAt": "2026-11-12T00:00:00Z",
              "settings": {
                "calculation_method": "EGYPTIAN",
                "alert_fajr_reminder_minutes": 20,
                "location_latitude": 30.0444,
                "custom_adhan_full": "content://elsewhere/adhan.mp3",
                "backup_last_export": 1762900000000,
                "onboarding_done": true
              },
              "prayerLog": { "firstUse": null, "prayed": {}, "pastPlaces": [] },
              "adhkarRecord": { "firstUse": null, "completed": {} }
            }
            """,
        )

        assertEquals(
            mapOf(
                "calculation_method" to SettingValue.Text("EGYPTIAN"),
                "alert_fajr_reminder_minutes" to SettingValue.Whole(20),
                "location_latitude" to SettingValue.Decimal(30.0444),
            ),
            restored(bytes, current = phone).settings,
        )
    }

    private val minimal = """
        "settings": {},
        "prayerLog": { "firstUse": null, "prayed": {}, "pastPlaces": [] },
        "adhkarRecord": { "firstUse": null, "completed": {} }
    """

    @Test
    fun `a file from a newer app version is refused`() {
        val bytes = file("""{ "format": "mawaqeet-backup", "version": 2, "exportedAt": "2027-01-01T00:00:00Z", $minimal }""")

        assertEquals(RestoreResult.NewerVersion, Backup.restore(bytes, snapshot))
    }

    @Test
    fun `fields added later within the same version are ignored`() {
        val bytes = file(
            """{ "format": "mawaqeet-backup", "version": 1, "exportedAt": "2026-11-12T00:00:00Z", "qibla": { "x": 1 }, $minimal }""",
        )

        assertEquals(nothing, restored(bytes))
    }

    @Test
    fun `a damaged file or one that isn't a Mawaqeet backup is refused`() {
        val refused = listOf(
            "",
            "{ \"format\": \"mawaqeet-backup\", \"version\": 1, \"settings\": {",
            "not json at all",
            "[1, 2, 3]",
            """{ "format": "other-app", "version": 1, $minimal }""",
            """{ "version": 1, $minimal }""",
            """{ "format": "mawaqeet-backup", "version": "1", $minimal }""",
            """{ "format": "mawaqeet-backup", "version": 1, "settings": {} }""",
            """{ "format": "mawaqeet-backup", "version": 1, "settings": { "vibration": null },
                 "prayerLog": { "firstUse": null, "prayed": {}, "pastPlaces": [] },
                 "adhkarRecord": { "firstUse": null, "completed": {} } }""",
            """{ "format": "mawaqeet-backup", "version": 1, "settings": {},
                 "prayerLog": { "firstUse": null, "prayed": { "2026-11-08": ["TAHAJJUD"] }, "pastPlaces": [] },
                 "adhkarRecord": { "firstUse": null, "completed": {} } }""",
            """{ "format": "mawaqeet-backup", "version": 1, "settings": {},
                 "prayerLog": { "firstUse": "yesterday", "prayed": {}, "pastPlaces": [] },
                 "adhkarRecord": { "firstUse": null, "completed": {} } }""",
            """{ "format": "mawaqeet-backup", "version": 1, "settings": {},
                 "prayerLog": { "firstUse": null, "prayed": {},
                   "pastPlaces": [ { "until": "2026-10-15", "latitude": 21.4, "longitude": 39.8, "zone": "Mars/Olympus" } ] },
                 "adhkarRecord": { "firstUse": null, "completed": {} } }""",
        )
        for (text in refused) {
            assertEquals(RestoreResult.Damaged, Backup.restore(file(text), snapshot), text)
        }
        assertEquals(RestoreResult.Damaged, Backup.restore(byteArrayOf(0xFF.toByte(), 0x00, 0x7B), snapshot))
    }

    private val makkah = Location(21.4225, 39.8262, ZoneId.of("Asia/Riyadh"))
    private val cairo = Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo"))
    private val dubai = Location(25.2048, 55.2708, ZoneId.of("Asia/Dubai"))
    private val riyadh = Location(24.7136, 46.6753, ZoneId.of("Asia/Riyadh"))

    private fun withPlaces(vararg places: PastPlace) = nothing.copy(prayerLog = PrayerLogBackup(null, emptyMap(), places.toList()))

    @Test
    fun `the file's past places fill only the time before the phone's own earliest place`() {
        val file = withPlaces(
            PastPlace(LocalDate.parse("2026-06-01"), makkah),
            PastPlace(LocalDate.parse("2026-08-01"), dubai),
            PastPlace(LocalDate.parse("2026-10-01"), riyadh),
        )
        // The phone was in Cairo until 2026-09-01: its own record wins from there on, the file's only before it.
        val phone = withPlaces(PastPlace(LocalDate.parse("2026-09-01"), cairo), PastPlace(LocalDate.parse("2026-11-01"), dubai))

        val merged = restored(Backup.export(file, Instant.EPOCH), current = phone)

        assertEquals(
            listOf(
                PastPlace(LocalDate.parse("2026-06-01"), makkah),
                PastPlace(LocalDate.parse("2026-08-01"), dubai),
                PastPlace(LocalDate.parse("2026-09-01"), cairo),
                PastPlace(LocalDate.parse("2026-11-01"), dubai),
            ),
            merged.prayerLog.pastPlaces.sortedBy { it.until },
        )
    }

    @Test
    fun `a phone that never moved takes every past place in the file`() {
        val file = withPlaces(PastPlace(LocalDate.parse("2026-06-01"), makkah), PastPlace(LocalDate.parse("2026-10-01"), riyadh))

        assertEquals(file.prayerLog.pastPlaces, restored(Backup.export(file, Instant.EPOCH)).prayerLog.pastPlaces.sortedBy { it.until })
    }
}
