package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.Prayer
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
    fun `without the exact-alarm permission nothing is scheduled`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        AlertScheduler(context).apply(AlertPlan(listOf(dhuhr, asr), midnight))

        assertEquals(emptyList<Instant>(), triggerTimes())
    }
}
