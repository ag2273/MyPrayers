package app.mawaqeet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.HighLatitudeRule
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.CityRef
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.Location
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.SavedLocation
import app.mawaqeet.core.PrayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "settings.preferences_pb")

    /** Opens the settings file as a freshly started process would, then closes it again. */
    private fun <T> withRepository(block: suspend (SettingsRepository, DataStore<Preferences>) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            return runBlocking { block(SettingsRepository(store), store) }
        } finally {
            runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }
        }
    }

    @Test
    fun `before any choice the settings are the defaults`() {
        assertEquals(PrayerSettings.Default, withRepository { repo, _ -> repo.settings.first() })
    }

    @Test
    fun `a manual location survives an app restart and becomes the Prayer Times location`() {
        // Cairo's city centre (GeoNames 360630).
        val cairo = SavedLocation(Location(30.0626, 31.2497, ZoneId.of("Africa/Cairo")), LocationSource.MANUAL,
            CityRef(360630L, "Cairo", "القاهرة", "EG", isNear = false))
        withRepository { repo, _ ->
            assertEquals(null, repo.savedLocation.first())
            repo.setLocation(cairo)
        }

        withRepository { repo, _ ->
            assertEquals(cairo, repo.savedLocation.first())
            assertEquals(cairo.location, repo.settings.first().location)
        }
    }

    @Test
    fun `typed coordinates are stored with the nearest city, and a later location without one drops it`() {
        val point = SavedLocation(Location(30.01, 31.21, ZoneId.of("Africa/Cairo")), LocationSource.MANUAL,
            CityRef(360630L, "Cairo", "القاهرة", "EG", isNear = true))
        withRepository { repo, _ -> repo.setLocation(point) }
        assertEquals(point, withRepository { repo, _ -> repo.savedLocation.first() })
        val bare = point.copy(city = null)
        withRepository { repo, _ -> repo.setLocation(bare) }
        assertEquals(bare, withRepository { repo, _ -> repo.savedLocation.first() })
        withRepository { repo, _ -> repo.clearLocation() }
        assertEquals(PrayerSettings.Default, withRepository { repo, _ -> repo.settings.first() })
    }

    @Test
    fun `the Calculation Method and Asr Convention survive an app restart`() {
        withRepository { repo, _ ->
            repo.setCalculationMethod(CalculationMethod.EGYPTIAN)
            repo.setAsrConvention(AsrConvention.HANAFI)
        }

        val restored = withRepository { repo, _ -> repo.settings.first() }

        assertEquals(CalculationMethod.EGYPTIAN, restored.method)
        assertEquals(AsrConvention.HANAFI, restored.asrConvention)
    }

    @Test
    fun `Prayer Time adjustments and the high-latitude rule survive an app restart`() {
        withRepository { repo, _ ->
            repo.setAdjustment(Prayer.FAJR, 3)
            repo.setAdjustment(Prayer.ISHA, -30)
            repo.setAdjustment(Prayer.DHUHR, 4)
            repo.setAdjustment(Prayer.DHUHR, 0)
            repo.setHighLatitudeRule(HighLatitudeRule.TWILIGHT_ANGLE)
        }

        val restored = withRepository { repo, _ -> repo.settings.first() }

        assertEquals(mapOf(Prayer.FAJR to 3, Prayer.ISHA to -30), restored.adjustments)
        assertEquals(HighLatitudeRule.TWILIGHT_ANGLE, restored.highLatitudeRule)
    }

    @Test
    fun `stepping an adjustment stays within the allowed range and clears at zero`() = withRepository { repo, _ ->
        repo.setAdjustment(Prayer.ASR, 29)
        repo.stepAdjustment(Prayer.ASR, +1)
        repo.stepAdjustment(Prayer.ASR, +1)
        assertEquals(30, repo.settings.first().adjustmentOf(Prayer.ASR))

        repo.stepAdjustment(Prayer.MAGHRIB, -1)
        repo.stepAdjustment(Prayer.MAGHRIB, +1)
        assertEquals(mapOf(Prayer.ASR to 30), repo.settings.first().adjustments)
    }

    @Test
    fun `the Hijri offset and digit style survive an app restart`() {
        assertEquals(DigitStyle.AUTOMATIC, withRepository { repo, _ -> repo.digitStyle.first() })
        withRepository { repo, _ ->
            repo.setHijriOffsetDays(-1)
            repo.setDigitStyle(DigitStyle.ARABIC_INDIC)
        }

        withRepository { repo, _ ->
            assertEquals(-1, repo.settings.first().hijriOffsetDays)
            assertEquals(DigitStyle.ARABIC_INDIC, repo.digitStyle.first())
        }
    }

    @Test
    fun `the chosen app language is kept for widgets and receivers, and empty means the system language`() {
        assertEquals("", withRepository { repo, _ -> repo.appLanguage.first() })
        withRepository { repo, _ -> repo.setAppLanguage("en") }

        assertEquals("en", withRepository { repo, _ -> repo.appLanguage.first() })
        withRepository { repo, _ -> repo.setAppLanguage("") }
    }

    @Test
    fun `a stored Hijri offset outside the allowed range reads as no offset`() {
        withRepository { _, store -> store.edit { it[intPreferencesKey("hijri_offset_days")] = 5 } }

        assertEquals(0, withRepository { repo, _ -> repo.settings.first() }.hijriOffsetDays)
    }

    @Test
    fun `a stored method this build doesn't know falls back to the default`() {
        withRepository { _, store -> store.edit { it[stringPreferencesKey("calculation_method")] = "TEHRAN" } }

        val restored = withRepository { repo, _ -> repo.settings.first() }

        assertEquals(PrayerSettings.Default.method, restored.method)
    }

    @Test
    fun `alert settings survive an app restart and reach the core's settings`() {
        val fajr = app.mawaqeet.core.PrayerAlertSettings(
            sound = app.mawaqeet.core.AdhanSoundChoice.FULL,
            reminder = true,
            reminderMinutes = 20,
        )
        val maghrib = app.mawaqeet.core.PrayerAlertSettings(adhanAlert = false)
        withRepository { repo, _ ->
            repo.setPrayerAlert(app.mawaqeet.core.Prayer.FAJR, fajr)
            repo.setPrayerAlert(app.mawaqeet.core.Prayer.MAGHRIB, maghrib)
            repo.setAlarmClockMode(true)
            repo.setPlayOnSilent(true)
            repo.setVibration(false)
            repo.setAdhanVolume(app.mawaqeet.core.AdhanVolume.MEDIA)
        }

        val alerts = withRepository { repo, _ -> repo.settings.first() }.alerts

        assertEquals(
            app.mawaqeet.core.AlertSettings(
                alarmClockMode = true,
                playOnSilent = true,
                vibration = false,
                adhanVolume = app.mawaqeet.core.AdhanVolume.MEDIA,
            )
                .with(app.mawaqeet.core.Prayer.FAJR, fajr)
                .with(app.mawaqeet.core.Prayer.MAGHRIB, maghrib),
            alerts,
        )
    }

    @Test
    fun `Prayer Silence is off by default, and turning it on survives an app restart with the other alert settings`() {
        assertEquals(false, withRepository { repo, _ -> repo.settings.first() }.alerts.prayerSilence.enabled)
        withRepository { repo, _ ->
            repo.setAlertsEnabled(false)
            repo.setPrayerSilenceEnabled(true)
        }

        val alerts = withRepository { repo, _ -> repo.settings.first() }.alerts

        assertEquals(app.mawaqeet.core.PrayerSilence(enabled = true), alerts.prayerSilence)
        assertEquals(false, alerts.enabled)
    }

    @Test
    fun `Prayer Silence's mode, Prayers and durations survive an app restart`() {
        withRepository { repo, _ ->
            repo.setPrayerSilenceMode(app.mawaqeet.core.SilenceMode.VIBRATE)
            repo.setPrayerSilencePrayer(Prayer.FAJR, false)
            repo.stepPrayerSilenceMinutes(jumuah = false, step = +2)
            repo.stepPrayerSilenceMinutes(jumuah = true, step = -1)
        }

        val silence = withRepository { repo, _ -> repo.settings.first() }.alerts.prayerSilence

        assertEquals(app.mawaqeet.core.SilenceMode.VIBRATE, silence.mode)
        assertEquals(Prayer.entries.toSet() - Prayer.FAJR, silence.prayers)
        // One step is five minutes: 20 + 10 and 45 - 5.
        assertEquals(30, silence.minutes)
        assertEquals(40, silence.jumuahMinutes)
    }

    @Test
    fun `Prayer Silence durations stop at the ends of their ranges, and a stored one out of range reads as the default`() {
        withRepository { repo, _ ->
            repo.stepPrayerSilenceMinutes(jumuah = false, step = -100)
            repo.stepPrayerSilenceMinutes(jumuah = true, step = +100)
        }
        val silence = withRepository { repo, _ -> repo.settings.first() }.alerts.prayerSilence
        assertEquals(app.mawaqeet.core.PrayerSilence.MINUTES_RANGE.first, silence.minutes)
        assertEquals(app.mawaqeet.core.PrayerSilence.JUMUAH_MINUTES_RANGE.last, silence.jumuahMinutes)

        withRepository { _, store -> store.edit { it[intPreferencesKey("prayer_silence_minutes")] = 1 } }
        assertEquals(20, withRepository { repo, _ -> repo.settings.first() }.alerts.prayerSilence.minutes)
    }

    @Test
    fun `a stored reminder lead time below the minimum reads as the default instead of failing`() {
        withRepository { _, store -> store.edit { it[intPreferencesKey("alert_dhuhr_reminder_minutes")] = 5 } }

        val dhuhr = withRepository { repo, _ -> repo.settings.first() }.alerts.of(app.mawaqeet.core.Prayer.DHUHR)

        assertEquals(15, dhuhr.reminderMinutes)
    }

    @Test
    fun `a Custom Adhan per recording survives an app restart, and a Custom Full Adhan leaves Fajr's unchanged`() {
        withRepository { repo, _ ->
            repo.setCustomAdhan(app.mawaqeet.core.AdhanRecording.FULL, "content://docs/full")
            repo.setCustomAdhan(app.mawaqeet.core.AdhanRecording.SHORT, "content://docs/short")
        }

        val alerts = withRepository { repo, _ -> repo.settings.first() }.alerts

        assertEquals(
            mapOf(
                app.mawaqeet.core.AdhanRecording.FULL to "content://docs/full",
                app.mawaqeet.core.AdhanRecording.SHORT to "content://docs/short",
            ),
            alerts.customAdhans,
        )
        assertEquals(null, alerts.customAdhan(app.mawaqeet.core.AdhanRecording.FAJR_FULL))
    }

    @Test
    fun `replacing or clearing a Custom Adhan reports the files no recording uses any more`() {
        withRepository { repo, _ ->
            assertEquals(emptySet<String>(), repo.setCustomAdhan(app.mawaqeet.core.AdhanRecording.FULL, "content://docs/a"))
            assertEquals(emptySet<String>(), repo.setCustomAdhan(app.mawaqeet.core.AdhanRecording.FAJR_FULL, "content://docs/a"))
            // Still used by Fajr's Full Adhan, so its permission stays.
            assertEquals(emptySet<String>(), repo.setCustomAdhan(app.mawaqeet.core.AdhanRecording.FULL, "content://docs/b"))
            assertEquals(setOf("content://docs/a"), repo.setCustomAdhan(app.mawaqeet.core.AdhanRecording.FAJR_FULL, null))
            assertEquals(setOf("content://docs/b"), repo.setCustomAdhan(app.mawaqeet.core.AdhanRecording.FULL, null))
            assertEquals(emptyMap<app.mawaqeet.core.AdhanRecording, String>(), repo.settings.first().alerts.customAdhans)
        }
    }

    @Test
    fun `the widget appearance survives an app restart, and a broken stored value reads as the default`() {
        assertEquals(app.mawaqeet.widget.WidgetAppearance(), withRepository { repo, _ -> repo.widgetAppearance.first() })

        withRepository { repo, _ ->
            repo.setWidgetTransparency(70)
            repo.setWidgetColors(app.mawaqeet.widget.WidgetColors.APP)
        }
        assertEquals(
            app.mawaqeet.widget.WidgetAppearance(70, app.mawaqeet.widget.WidgetColors.APP),
            withRepository { repo, _ -> repo.widgetAppearance.first() },
        )

        withRepository { _, store ->
            store.edit {
                it[intPreferencesKey("widget_transparency")] = 35
                it[stringPreferencesKey("widget_colors")] = "NEON"
            }
        }
        assertEquals(app.mawaqeet.widget.WidgetAppearance(), withRepository { repo, _ -> repo.widgetAppearance.first() })
    }

    @Test
    fun `salawat reminders are off every 2 hours by default, and switching them on every 3 hours survives an app restart`() {
        assertEquals(
            app.mawaqeet.core.SalawatReminder(enabled = false, intervalHours = 2),
            withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.salawat,
        )
        withRepository { repo, _ ->
            repo.setSalawatReminder(true)
            repo.setSalawatInterval(3)
        }

        val salawat = withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.salawat

        assertEquals(app.mawaqeet.core.SalawatReminder(enabled = true, intervalHours = 3), salawat)
    }

    @Test
    fun `a stored salawat interval that is not 1, 2 or 3 hours reads as the default`() {
        withRepository { _, store -> store.edit { it[intPreferencesKey("worship_salawat_interval_hours")] = 5 } }

        assertEquals(2, withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.salawat.intervalHours)
    }

    @Test
    fun `adhkar reminders are off 30 minutes after their Prayer by default, and each keeps its own choice after an app restart`() {
        val off = app.mawaqeet.core.AdhkarReminder(enabled = false, minutes = 30)
        val before = withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders
        assertEquals(off, before.morningAdhkar)
        assertEquals(off, before.eveningAdhkar)
        withRepository { repo, _ ->
            repo.setAdhkarReminder(app.mawaqeet.core.AdhkarSetId.MORNING, true)
            repo.setAdhkarReminderMinutes(app.mawaqeet.core.AdhkarSetId.MORNING, 60)
            repo.setAdhkarReminderMinutes(app.mawaqeet.core.AdhkarSetId.EVENING, 15)
        }

        val after = withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders

        assertEquals(app.mawaqeet.core.AdhkarReminder(enabled = true, minutes = 60), after.morningAdhkar)
        assertEquals(app.mawaqeet.core.AdhkarReminder(enabled = false, minutes = 15), after.eveningAdhkar)
    }

    @Test
    fun `a stored adhkar reminder minutes that is not offered reads as the default`() {
        withRepository { _, store -> store.edit { it[intPreferencesKey("worship_morning_adhkar_minutes")] = 20 } }

        assertEquals(30, withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.morningAdhkar.minutes)
    }

    @Test
    fun `the Kahf reminder is off by default, and switching it on survives an app restart`() {
        assertEquals(
            app.mawaqeet.core.KahfReminder(enabled = false),
            withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.kahf,
        )
        withRepository { repo, _ -> repo.setKahfReminder(true) }

        val kahf = withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.kahf

        assertEquals(app.mawaqeet.core.KahfReminder(enabled = true), kahf)
    }

    @Test
    fun `Imsak is 10 minutes before Fajr by default, and a stepped choice survives an app restart within 0 to 30`() {
        assertEquals(10, withRepository { repo, _ -> repo.settings.first() }.alerts.ramadan.imsakMinutes)

        withRepository { repo, _ -> repo.stepImsakMinutes(+2) }
        assertEquals(20, withRepository { repo, _ -> repo.settings.first() }.alerts.ramadan.imsakMinutes)

        withRepository { repo, _ -> repo.stepImsakMinutes(+100) }
        assertEquals(30, withRepository { repo, _ -> repo.settings.first() }.alerts.ramadan.imsakMinutes)

        withRepository { _, store -> store.edit { it[intPreferencesKey("ramadan_imsak_minutes")] = 45 } }
        assertEquals(10, withRepository { repo, _ -> repo.settings.first() }.alerts.ramadan.imsakMinutes)
    }

    @Test
    fun `the Ramadan reminders are off by default, and their switches and the suhoor minutes survive an app restart`() {
        val before = withRepository { repo, _ -> repo.settings.first() }.alerts.ramadan
        assertEquals(app.mawaqeet.core.SuhoorReminder(enabled = false, minutes = 60), before.suhoorReminder)
        assertEquals(false, before.imsakAlert)

        withRepository { repo, _ ->
            repo.setSuhoorReminder(true)
            repo.setSuhoorReminderMinutes(90)
            repo.setImsakAlert(true)
        }

        val after = withRepository { repo, _ -> repo.settings.first() }.alerts.ramadan
        assertEquals(app.mawaqeet.core.SuhoorReminder(enabled = true, minutes = 90), after.suhoorReminder)
        assertEquals(true, after.imsakAlert)
        assertEquals(10, after.imsakMinutes)
    }

    @Test
    fun `the Sunnah Fast reminders are off by default, and each switch survives an app restart and a backup`() {
        assertEquals(emptySet<app.mawaqeet.core.SunnahFast>(), withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.fasts.on)

        val backup = withRepository { repo, _ ->
            repo.setFastReminder(app.mawaqeet.core.SunnahFast.ARAFAH, true)
            repo.setFastReminder(app.mawaqeet.core.SunnahFast.SIX_OF_SHAWWAL, true)
            repo.setFastReminder(app.mawaqeet.core.SunnahFast.MONDAY_THURSDAY, true)
            repo.setFastReminder(app.mawaqeet.core.SunnahFast.MONDAY_THURSDAY, false)
            repo.backupSettings()
        }

        val on = setOf(app.mawaqeet.core.SunnahFast.ARAFAH, app.mawaqeet.core.SunnahFast.SIX_OF_SHAWWAL)
        assertEquals(on, withRepository { repo, _ -> repo.settings.first() }.alerts.worshipReminders.fasts.on)
        // Through a Backup File into a phone with none on, the same switches come back.
        fun snapshot(settings: Map<String, app.mawaqeet.core.SettingValue>) = app.mawaqeet.core.BackupSnapshot(
            settings, app.mawaqeet.core.PrayerLogBackup(null, emptyMap(), emptyList()), app.mawaqeet.core.AdhkarRecordBackup(null, emptyMap()),
        )
        val file = app.mawaqeet.core.Backup.export(snapshot(backup), java.time.Instant.parse("2026-11-13T00:00:00Z"))
        val restored = (app.mawaqeet.core.Backup.restore(file, snapshot(emptyMap())) as app.mawaqeet.core.RestoreResult.Restored).snapshot
        withRepository { repo, _ ->
            repo.restoreSettings(emptyMap())
            assertEquals(emptySet<app.mawaqeet.core.SunnahFast>(), repo.settings.first().alerts.worshipReminders.fasts.on)
            repo.restoreSettings(restored.settings)
            assertEquals(on, repo.settings.first().alerts.worshipReminders.fasts.on)
        }
    }

    @Test
    fun `stored suhoor minutes outside the choices read as the hour before Fajr`() {
        withRepository { _, store -> store.edit { it[intPreferencesKey("ramadan_suhoor_minutes")] = 50 } }

        assertEquals(60, withRepository { repo, _ -> repo.settings.first() }.alerts.ramadan.suhoorReminder.minutes)
    }
}
