package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate

/** A plain Application, so the app's own settings-following scheduler doesn't add alarms of its own. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class AlertSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private val day = LocalDate.parse("2026-11-08")
    private fun alert(prayer: Prayer, at: String, mode: DeliveryMode = DeliveryMode.EXACT) = PlannedAlert(
        instant = Instant.parse(at),
        prayer = prayer,
        kind = AlertKind.ADHAN_ALERT,
        sound = AlertSound.ShortAdhan,
        deliveryMode = mode,
        prayerDay = day,
        isJumuah = false,
    )

    private val dhuhr = alert(Prayer.DHUHR, "2026-11-08T09:04:00Z")
    private val asr = alert(Prayer.ASR, "2026-11-08T12:17:00Z")
    private val maghrib = alert(Prayer.MAGHRIB, "2026-11-08T14:41:00Z", DeliveryMode.ALARM_CLOCK)
    private val isha = alert(Prayer.ISHA, "2026-11-08T16:11:00Z")
    private val midnight = Instant.parse("2026-11-08T21:00:00Z")

    private fun triggerTimes() = alarms.scheduledAlarms.map { Instant.ofEpochMilli(it.triggerAtTime) }.sorted()

    @Before
    fun allowExactAlarms() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @Test
    fun `schedules one alarm per planned alert plus local midnight, in the plan's delivery mode`() {
        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr, asr, maghrib), midnight))

        assertEquals(listOf(dhuhr.instant, asr.instant, maghrib.instant, midnight), triggerTimes())
        val byTime = alarms.scheduledAlarms.associateBy { Instant.ofEpochMilli(it.triggerAtTime) }
        for (exact in listOf(dhuhr, asr)) {
            val alarm = byTime.getValue(exact.instant)
            assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
            assertTrue("exact alerts fire while idle", alarm.allowWhileIdle)
            assertNull(alarm.alarmClockInfo)
        }
        assertNotNull("alarm-clock mode uses setAlarmClock", byTime.getValue(maghrib.instant).alarmClockInfo)
    }

    @Test
    fun `alarm-clock alerts carry the full-screen flag, so they open the alarm screen over the lock screen`() {
        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr, maghrib), midnight))

        fun fullScreen(alert: PlannedAlert) = shadowOf(alarms.scheduledAlarms.single { it.triggerAtTime == alert.instant.toEpochMilli() }.operation)
            .savedIntent.getBooleanExtra(AlertReceiver.EXTRA_FULL_SCREEN, false)
        assertTrue(fullScreen(maghrib))
        assertTrue(!fullScreen(dhuhr))
    }

    @Test
    fun `Try it schedules an Adhan Alert in 10 seconds, as an alarm in alarm-clock mode, with nothing to mark`() {
        val before = System.currentTimeMillis()
        assertTrue(AlertScheduler(context).scheduleTest(Prayer.ASR, isJumuah = false, alarmClockMode = true, sound = AlertSound.Silent))

        val alarm = alarms.scheduledAlarms.single()
        assertNotNull(alarm.alarmClockInfo)
        assertTrue(alarm.triggerAtTime in before + 9_000..System.currentTimeMillis() + 10_000)
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(AlertReceiver.ACTION_ALERT, intent.action)
        assertEquals("ASR", intent.getStringExtra(AlertReceiver.EXTRA_PRAYER))
        assertTrue(intent.getBooleanExtra(AlertReceiver.EXTRA_FULL_SCREEN, false))
        assertNull(AlertReceiver.prayerDay(intent))
    }

    @Test
    fun `an alarm-clock Adhan Alert notification has a full-screen intent, an ordinary one doesn't`() {
        val alarm = AdhanNotification(Prayer.FAJR, isJumuah = false, id = 7, fullScreen = true).builder(context).build()
        val plain = AdhanNotification(Prayer.FAJR, isJumuah = false, id = 8).builder(context).build()

        assertNotNull(alarm.fullScreenIntent)
        assertNull(plain.fullScreenIntent)
    }

    @Test
    fun `a Prayer Time without an Adhan Alert gets the display alarm instead of midnight, so widgets move on there`() {
        val unannouncedAsr = Instant.parse("2026-11-08T12:17:00Z")
        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr, maghrib), midnight, nextDisplayChange = unannouncedAsr))

        assertEquals(listOf(dhuhr.instant, unannouncedAsr, maghrib.instant), triggerTimes())
        val display = alarms.scheduledAlarms.single { Instant.ofEpochMilli(it.triggerAtTime) == unannouncedAsr }
        assertEquals(AlertReceiver.ACTION_MIDNIGHT, shadowOf(display.operation).savedIntent.action)
    }

    @Test
    fun `a new plan cancels alarms it no longer contains and adds the missing ones`() {
        val scheduler = AlertScheduler(context)
        scheduler.apply(AlertPlan(listOf(dhuhr, asr, maghrib), midnight))

        scheduler.apply(AlertPlan(listOf(asr, maghrib.copy(deliveryMode = DeliveryMode.EXACT), isha), midnight))

        assertEquals(listOf(asr.instant, maghrib.instant, isha.instant, midnight), triggerTimes())
        assertTrue(alarms.scheduledAlarms.all { it.alarmClockInfo == null })
    }

    @Test
    fun `after a reboot the stored plan re-arms the alerts still ahead, without recalculating`() {
        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr, asr, maghrib, isha), midnight))
        // A reboot clears every alarm.
        alarms.scheduledAlarms.toList().forEach { context.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }

        AlertScheduler(context).rearmStoredPlan(now = Instant.parse("2026-11-08T13:00:00Z"))

        assertEquals(listOf(maghrib.instant, isha.instant, midnight), triggerTimes())
    }

    @Test
    fun `a Custom Adhan keeps its file and the recording it replaces through the stored plan and the alarm intent`() {
        val custom = isha.copy(sound = AlertSound.CustomAdhan("content://docs/document/audio%3A42", AdhanRecording.FULL))
        AlertScheduler(context).apply(AlertPlan(listOf(custom), midnight))

        assertEquals(custom.sound, AlertPlanStore(context).read()!!.alerts.single().sound)
        assertEquals(custom.sound, decodeAlertSound(encodeAlertSound(custom.sound)))
    }

    @Test
    fun `the adhan volume choice survives the stored plan and rides on the alarm intent`() {
        val media = isha.copy(adhanVolume = app.mawaqeet.core.AdhanVolume.MEDIA)
        val scheduler = AlertScheduler(context)
        scheduler.apply(AlertPlan(listOf(media), midnight))

        assertEquals(app.mawaqeet.core.AdhanVolume.MEDIA, AlertPlanStore(context).read()!!.alerts.single().adhanVolume)
        assertEquals("MEDIA", scheduler.alarmIntent(media).getStringExtra(AlertReceiver.EXTRA_ADHAN_VOLUME))
    }

    @Test
    fun `without the exact-alarm permission nothing is scheduled`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr, asr), midnight))

        assertEquals(emptyList<Instant>(), triggerTimes())
    }

    private fun silence(prayer: Prayer, start: String, end: String) = SilenceWindow(
        prayer = prayer,
        prayerDay = day,
        start = Instant.parse(start),
        end = Instant.parse(end),
        mode = SilenceMode.SILENT,
        isJumuah = false,
    )

    private val dhuhrSilence = silence(Prayer.DHUHR, "2026-11-08T09:04:00Z", "2026-11-08T09:24:00Z")
    private val asrSilence = silence(Prayer.ASR, "2026-11-08T12:17:00Z", "2026-11-08T12:37:00Z")
    private val early = Instant.parse("2026-11-08T08:00:00Z")

    private fun silenceAlarms() = alarms.scheduledAlarms
        .map { shadowOf(it.operation).savedIntent.action to Instant.ofEpochMilli(it.triggerAtTime) }
        .filter { (action, _) -> action != AlertReceiver.ACTION_ALERT && action != AlertReceiver.ACTION_MIDNIGHT }
        .sortedBy { it.second }

    @Test
    fun `each Silence Window gets an exact alarm at its start and one at its end, even with no alert planned`() {
        AlertScheduler(context).apply(AlertPlan(emptyList(), midnight, silenceWindows = listOf(dhuhrSilence, asrSilence)), early)

        assertEquals(
            listOf(
                SilenceReceiver.ACTION_START to dhuhrSilence.start,
                SilenceReceiver.ACTION_END to dhuhrSilence.end,
                SilenceReceiver.ACTION_START to asrSilence.start,
                SilenceReceiver.ACTION_END to asrSilence.end,
            ),
            silenceAlarms(),
        )
        assertTrue(alarms.scheduledAlarms.all { it.allowWhileIdle && it.alarmClockInfo == null })
    }

    @Test
    fun `a Silence Window in progress gets only its end alarm, so a reschedule never silences the phone twice`() {
        AlertScheduler(context).apply(
            AlertPlan(emptyList(), midnight, silenceWindows = listOf(dhuhrSilence)),
            now = Instant.parse("2026-11-08T09:10:00Z"),
        )

        assertEquals(listOf(SilenceReceiver.ACTION_END to dhuhrSilence.end), silenceAlarms())
    }

    @Test
    fun `turning Prayer Silence off cancels its alarms`() {
        val scheduler = AlertScheduler(context)
        scheduler.apply(AlertPlan(listOf(dhuhr), midnight, silenceWindows = listOf(dhuhrSilence, asrSilence)), early)

        scheduler.apply(AlertPlan(listOf(dhuhr), midnight), early)

        assertEquals(emptyList<Pair<String?, Instant>>(), silenceAlarms())
        assertEquals(listOf(dhuhr.instant, midnight), triggerTimes())
    }

    @Test
    fun `Silence Windows survive the stored plan, so a reboot re-arms the ones not yet over`() {
        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr), midnight, silenceWindows = listOf(dhuhrSilence, asrSilence)), early)
        assertEquals(listOf(dhuhrSilence, asrSilence), AlertPlanStore(context).read()!!.silenceWindows)
        alarms.scheduledAlarms.toList().forEach { context.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }

        AlertScheduler(context).rearmStoredPlan(now = Instant.parse("2026-11-08T12:20:00Z"))

        assertEquals(listOf(SilenceReceiver.ACTION_END to asrSilence.end), silenceAlarms())
    }

    @Test
    fun `a Silence Window with an Adhan Alert is opened by that alert, with no start alarm of its own`() {
        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr, asr), midnight, silenceWindows = listOf(dhuhrSilence)), early)

        assertEquals(listOf(SilenceReceiver.ACTION_END to dhuhrSilence.end), silenceAlarms())
        val intents = alarms.scheduledAlarms.map { shadowOf(it.operation).savedIntent }.filter { it.action == AlertReceiver.ACTION_ALERT }
        val dhuhrAlarm = intents.single { it.getStringExtra(AlertReceiver.EXTRA_PRAYER) == "DHUHR" }
        val asrAlarm = intents.single { it.getStringExtra(AlertReceiver.EXTRA_PRAYER) == "ASR" }
        assertEquals(dhuhrSilence.end.toEpochMilli(), dhuhrAlarm.getLongExtra(AlertReceiver.EXTRA_SILENCE_END, 0))
        assertEquals(false, asrAlarm.hasExtra(AlertReceiver.EXTRA_SILENCE_END))
    }

    @Test
    fun `a Silence Window whose Prayer has no Adhan Alert still starts at the Prayer Time`() {
        AlertScheduler(context).apply(AlertPlan(listOf(asr), midnight, silenceWindows = listOf(dhuhrSilence)), early)

        assertEquals(
            listOf(SilenceReceiver.ACTION_START to dhuhrSilence.start, SilenceReceiver.ACTION_END to dhuhrSilence.end),
            silenceAlarms(),
        )
    }

    @Test
    fun `turning the Adhan Alert on drops the start alarm set while it was off`() {
        val scheduler = AlertScheduler(context)
        scheduler.apply(AlertPlan(emptyList(), midnight, silenceWindows = listOf(dhuhrSilence)), early)

        scheduler.apply(AlertPlan(listOf(dhuhr), midnight, silenceWindows = listOf(dhuhrSilence)), early)

        assertEquals(listOf(SilenceReceiver.ACTION_END to dhuhrSilence.end), silenceAlarms())
    }

    @Test
    fun `a plan stored by the previous version is still read, with the default digits and language`() {
        val file = java.io.File(context.createDeviceProtectedStorageContext().filesDir, AlertPlanStore.FILE_NAME)
        file.writeText(
            listOf(
                "alert-plan-v6",
                midnight.toEpochMilli().toString(),
                listOf(dhuhr.instant.toEpochMilli(), "DHUHR", "ADHAN_ALERT", "SHORT_ADHAN", "EXACT", day, false, dhuhr.instant.toEpochMilli(), false, true, "ALARM").joinToString("\t"),
                listOf("silence", "DHUHR", day, dhuhrSilence.start.toEpochMilli(), dhuhrSilence.end.toEpochMilli(), "SILENT", false).joinToString("\t"),
            ).joinToString("\n", postfix = "\n"),
        )

        val stored = AlertPlanStore(context)
        assertEquals(listOf(dhuhr), stored.read()!!.alerts)
        assertEquals(listOf(dhuhrSilence), stored.read()!!.silenceWindows)
        assertEquals(NotificationStyle.Default, stored.readStyle())
    }

    @Test
    fun `the digits and language survive the stored plan`() {
        val style = NotificationStyle(app.mawaqeet.core.DigitStyle.WESTERN, "en")
        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr), midnight, silenceWindows = listOf(dhuhrSilence)), early, style)

        assertEquals(style, AlertPlanStore(context).readStyle())
        assertEquals(listOf(dhuhrSilence), AlertPlanStore(context).read()!!.silenceWindows)
    }

    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val during = Instant.parse("2026-11-08T09:10:00Z")

    /** The start alarm has fired by then, so only the end alarms count. */
    private fun endAlarms() = silenceAlarms().filter { it.first == SilenceReceiver.ACTION_END }

    /** The phone on normal sound, silenced by [window]'s start after [scheduler] planned it. */
    private fun silencedBy(window: SilenceWindow, scheduler: AlertScheduler) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        scheduler.apply(AlertPlan(emptyList(), midnight, silenceWindows = listOf(window)), early)
        PrayerSilencer(context).start(window)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test
    fun `turning Prayer Silence off during a window brings the sound back at once`() {
        val scheduler = AlertScheduler(context)
        silencedBy(dhuhrSilence, scheduler)

        scheduler.apply(AlertPlan(emptyList(), midnight), during)

        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertEquals(emptyList<Pair<String?, Instant>>(), endAlarms())
    }

    @Test
    fun `a reschedule that moves a window's end to before now brings the sound back at once`() {
        val scheduler = AlertScheduler(context)
        silencedBy(dhuhrSilence, scheduler)

        val shorter = dhuhrSilence.copy(end = Instant.parse("2026-11-08T09:09:00Z"))
        scheduler.apply(AlertPlan(emptyList(), midnight, silenceWindows = listOf(shorter)), during)

        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertEquals(emptyList<Pair<String?, Instant>>(), endAlarms())
    }

    @Test
    fun `a reschedule that moves a window's end later keeps the phone silent until the new end`() {
        val scheduler = AlertScheduler(context)
        silencedBy(dhuhrSilence, scheduler)

        val longer = dhuhrSilence.copy(end = Instant.parse("2026-11-08T09:34:00Z"))
        scheduler.apply(AlertPlan(emptyList(), midnight, silenceWindows = listOf(longer)), during)

        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(listOf(SilenceReceiver.ACTION_END to longer.end), endAlarms())
        PrayerSilencer(context).end(longer)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }
}
