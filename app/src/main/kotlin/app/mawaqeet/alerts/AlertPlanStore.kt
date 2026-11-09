package app.mawaqeet.alerts

import android.content.Context
import androidx.core.util.AtomicFile
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.Prayer
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.time.LocalDate

/**
 * The last alert plan the scheduler applied, kept in **device-protected storage** (file `files/alert_plan.txt`
 * under `createDeviceProtectedStorageContext()`) so a locked boot can re-arm alarms before the first unlock,
 * when settings in credential-encrypted DataStore are unreadable. One tab-separated line per alert.
 */
internal class AlertPlanStore(context: Context) {
    private val file = AtomicFile(File(context.createDeviceProtectedStorageContext().filesDir, FILE_NAME))

    fun read(): AlertPlan? = try {
        decode(file.readFully().decodeToString())
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null // Unreadable (e.g. an older format): treated as no stored plan; the next full reschedule rewrites it.
    }

    fun write(plan: AlertPlan) {
        val out = file.startWrite()
        try {
            out.write(encode(plan).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun encode(plan: AlertPlan): String = buildString {
        appendLine(VERSION)
        appendLine(plan.nextLocalMidnight.toEpochMilli())
        for (a in plan.alerts) {
            appendLine(
                listOf(
                    a.instant.toEpochMilli(), a.prayer.name, a.kind.name, encodeAlertSound(a.sound),
                    a.deliveryMode.name, a.prayerDay, a.isJumuah, a.prayerTime.toEpochMilli(), a.playOnSilent, a.vibration,
                ).joinToString("\t"),
            )
        }
    }

    private fun decode(text: String): AlertPlan {
        val lines = text.lines().filter { it.isNotEmpty() }
        require(lines.firstOrNull() == VERSION)
        val midnight = Instant.ofEpochMilli(lines[1].toLong())
        val alerts = lines.drop(2).map { line ->
            val f = line.split('\t')
            require(f.size == 10)
            PlannedAlert(
                instant = Instant.ofEpochMilli(f[0].toLong()),
                prayer = Prayer.valueOf(f[1]),
                kind = AlertKind.valueOf(f[2]),
                sound = decodeAlertSound(f[3]),
                deliveryMode = DeliveryMode.valueOf(f[4]),
                prayerDay = LocalDate.parse(f[5]),
                isJumuah = f[6].toBooleanStrict(),
                prayerTime = Instant.ofEpochMilli(f[7].toLong()),
                playOnSilent = f[8].toBooleanStrict(),
                vibration = f[9].toBooleanStrict(),
            )
        }
        return AlertPlan(alerts, midnight)
    }

    companion object {
        const val FILE_NAME = "alert_plan.txt"
        private const val VERSION = "alert-plan-v3"
    }
}

private const val CUSTOM_PREFIX = "CUSTOM:"

/**
 * Text form of an [AlertSound], shared by the stored plan and the alarm intents' extras. A Custom Adhan is
 * `CUSTOM:<AdhanRecording name>:<reference>`, e.g. `CUSTOM:FULL:content://…`.
 */
internal fun encodeAlertSound(sound: AlertSound): String = when (sound) {
    AlertSound.ShortAdhan -> "SHORT_ADHAN"
    AlertSound.FullAdhan -> "FULL_ADHAN"
    AlertSound.FajrFullAdhan -> "FAJR_FULL_ADHAN"
    AlertSound.Silent -> "SILENT"
    AlertSound.ReminderTone -> "REMINDER_TONE"
    is AlertSound.CustomAdhan -> CUSTOM_PREFIX + sound.replaces.name + ":" + sound.reference
}

internal fun decodeAlertSound(s: String): AlertSound = when {
    s == "SHORT_ADHAN" -> AlertSound.ShortAdhan
    s == "FULL_ADHAN" -> AlertSound.FullAdhan
    s == "FAJR_FULL_ADHAN" -> AlertSound.FajrFullAdhan
    s == "SILENT" -> AlertSound.Silent
    s == "REMINDER_TONE" -> AlertSound.ReminderTone
    s.startsWith(CUSTOM_PREFIX) -> {
        val rest = s.removePrefix(CUSTOM_PREFIX)
        val recording = AdhanRecording.valueOf(rest.substringBefore(':')) // IllegalArgumentException if unknown.
        require(':' in rest)
        AlertSound.CustomAdhan(rest.substringAfter(':'), recording)
    }
    else -> throw IllegalArgumentException(s)
}
