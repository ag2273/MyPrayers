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
}
