package app.mawaqeet.alerts

import android.content.Context
import androidx.core.util.AtomicFile
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import app.mawaqeet.core.WorshipReminderKind
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.time.LocalDate

/**
 * The last alert plan the scheduler applied, kept in **device-protected storage** (file `files/alert_plan.txt`
 * under `createDeviceProtectedStorageContext()`) so a locked boot can re-arm alarms before the first unlock,
 * when settings in credential-encrypted DataStore are unreadable. One tab-separated line per alert, then one per
 * Silence Window, whose first field is `silence`, then one per Worship Reminder, whose first field is `worship`, then
 * the [NotificationStyle] (`style digits language`, v7), which the alarms' receivers use for their notifications
 * instead of reading settings.
 */
internal class AlertPlanStore(context: Context) {
    private val file = AtomicFile(File(context.createDeviceProtectedStorageContext().filesDir, FILE_NAME))

    fun read(): AlertPlan? = readLines()?.let { lines ->
        try {
            decode(lines.filterNot { it.startsWith(STYLE + "\t") })
        } catch (_: IllegalArgumentException) {
            null // Unreadable (e.g. an older format): treated as no stored plan; the next full reschedule rewrites it.
        }
    }

    /** The digits and language stored with the plan; [NotificationStyle.Default] for a plan from before v7 or none. */
    fun readStyle(): NotificationStyle = readLines()
        ?.firstOrNull { it.startsWith(STYLE + "\t") }
        ?.split('\t')
        ?.let { f -> runCatching { NotificationStyle(DigitStyle.valueOf(f[1]), f.getOrElse(2) { "" }) }.getOrNull() }
        ?: NotificationStyle.Default

    private fun readLines(): List<String>? = try {
        file.readFully().decodeToString().lines().filter { it.isNotEmpty() }
    } catch (_: FileNotFoundException) {
        null
    }

    fun write(plan: AlertPlan, style: NotificationStyle = NotificationStyle.Default) {
        val out = file.startWrite()
        try {
            out.write(encode(plan, style).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun encode(plan: AlertPlan, style: NotificationStyle): String = buildString {
        appendLine(VERSION)
        appendLine(plan.nextLocalMidnight.toEpochMilli())
        for (a in plan.alerts) {
            appendLine(
                listOf(
                    a.instant.toEpochMilli(), a.prayer.name, a.kind.name, encodeAlertSound(a.sound),
                    a.deliveryMode.name, a.prayerDay, a.isJumuah, a.prayerTime.toEpochMilli(), a.playOnSilent, a.vibration,
                    a.adhanVolume.name,
                ).joinToString("\t"),
            )
        }
        for (w in plan.silenceWindows) {
            appendLine(
                listOf(SILENCE, w.prayer.name, w.prayerDay, w.start.toEpochMilli(), w.end.toEpochMilli(), w.mode.name, w.isJumuah)
                    .joinToString("\t"),
            )
        }
        for (r in plan.worshipReminders) {
            appendLine(listOf(WORSHIP, r.kind.name, r.prayerDay, r.instant.toEpochMilli(), r.slot).joinToString("\t"))
        }
        appendLine(listOf(STYLE, style.digits.name, style.language).joinToString("\t"))
    }

    private fun decode(lines: List<String>): AlertPlan {
        // v6 differs only in having no style line.
        require(lines.firstOrNull() == VERSION || lines.firstOrNull() == PREVIOUS_VERSION)
        val midnight = Instant.ofEpochMilli(lines[1].toLong())
        val (worshipLines, otherLines) = lines.drop(2).partition { it.startsWith(WORSHIP + "\t") }
        val (silenceLines, alertLines) = otherLines.partition { it.startsWith(SILENCE + "\t") }
        val worshipReminders = worshipLines.map { line ->
            val f = line.split('\t')
            require(f.size == 5)
            PlannedWorshipReminder(
                kind = WorshipReminderKind.valueOf(f[1]),
                prayerDay = LocalDate.parse(f[2]),
                instant = Instant.ofEpochMilli(f[3].toLong()),
                slot = f[4].toInt(),
            )
        }
        val silenceWindows = silenceLines.map { line ->
            val f = line.split('\t')
            require(f.size == 7)
            SilenceWindow(
                prayer = Prayer.valueOf(f[1]),
                prayerDay = LocalDate.parse(f[2]),
                start = Instant.ofEpochMilli(f[3].toLong()),
                end = Instant.ofEpochMilli(f[4].toLong()),
                mode = SilenceMode.valueOf(f[5]),
                isJumuah = f[6].toBooleanStrict(),
            )
        }
        val alerts = alertLines.map { line ->
            val f = line.split('\t')
            require(f.size == 11)
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
                adhanVolume = AdhanVolume.valueOf(f[10]),
            )
        }
        return AlertPlan(alerts, midnight, silenceWindows = silenceWindows, worshipReminders = worshipReminders)
    }

    companion object {
        const val FILE_NAME = "alert_plan.txt"
        private const val VERSION = "alert-plan-v7"
        private const val PREVIOUS_VERSION = "alert-plan-v6"

        /** First field of a Silence Window's line (#61). */
        private const val SILENCE = "silence"

        /** First field of a Worship Reminder's line (#63): `worship kind prayerDay instant slot`. */
        private const val WORSHIP = "worship"

        /** First field of the [NotificationStyle] line (v7): `style digits language`. */
        private const val STYLE = "style"
    }
}

/**
 * The app's digits and language (an empty language: the phone's), stored with the plan so the alarms' receivers can
 * write their notifications in them before the first unlock, and without reading settings in a receiver.
 */
data class NotificationStyle(val digits: DigitStyle, val language: String) {
    companion object {
        val Default = NotificationStyle(DigitStyle.AUTOMATIC, "")
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
