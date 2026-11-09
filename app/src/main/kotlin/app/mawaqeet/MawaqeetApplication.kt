package app.mawaqeet

import android.app.Application
import app.mawaqeet.data.PrayerLogDatabase
import app.mawaqeet.data.RoomPrayerLogRepository

/** Holds the app-wide singletons that screens, widgets and alarm receivers share. */
class MawaqeetApplication : Application() {
    /** The one Prayer Log repository in the process, so its [RoomPrayerLogRepository.changes] reaches every observer. */
    val prayerLog: RoomPrayerLogRepository by lazy { RoomPrayerLogRepository(PrayerLogDatabase.open(this)) }
}
