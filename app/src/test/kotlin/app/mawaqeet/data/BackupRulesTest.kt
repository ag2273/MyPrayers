package app.mawaqeet.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** The Prayer Log and the location stay off cloud backup but move with a device-to-device transfer (#25). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupRulesTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val database = PrayerLogDatabase.FILE_NAME
    private val settings = "datastore/${SettingsRepository.FILE_NAME}.preferences_pb"

    /** (section, tag, domain, path, requireFlags) of every rule in [xml], in file order. */
    private fun rules(xml: Int): List<List<String?>> {
        val parser = ctx.resources.getXml(xml)
        val rules = mutableListOf<List<String?>>()
        var section: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "include", "exclude" -> rules += listOf(
                    section, parser.name,
                    parser.getAttributeValue(null, "domain"),
                    parser.getAttributeValue(null, "path"),
                    parser.getAttributeValue(null, "requireFlags"),
                )
            }
        }
        return rules
    }

    @Test
    fun `Android 12 and later exclude the Prayer Log and location from cloud backup only`() {
        val rules = rules(R.xml.data_extraction_rules)

        listOf("database" to database, "database" to "$database-wal", "file" to settings).forEach { (domain, path) ->
            assertTrue("$path excluded from cloud backup", listOf("cloud-backup", "exclude", domain, path, null) in rules)
        }
        // Nothing in device-transfer: a direct transfer carries every file.
        assertTrue(rules.none { it[0] == "device-transfer" })
    }

    @Test
    fun `Android 8 to 11 back up the Prayer Log and location only in a device-to-device transfer`() {
        val rules = rules(R.xml.backup_rules)

        listOf("database" to database, "file" to settings).forEach { (domain, path) ->
            val rule = rules.single { it[2] == domain && it[3] == path }
            assertEquals(listOf(null, "include", domain, path, "deviceToDeviceTransfer"), rule)
        }
    }
}
