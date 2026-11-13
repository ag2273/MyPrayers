# MyPrayers

An Android app and home-screen widget that shows the daily prayer times for the user's location, reminds them when each prayer is due, and lets them record whether they prayed.

## Language

**Prayer**:
One of the five obligatory daily prayers: Fajr, Dhuhr, Asr, Maghrib, Isha.
_Avoid_: Salah (in code and UI copy, pick one; we use Prayer), namaz

**Prayer Time**:
The moment a given Prayer becomes due on a given day at a given location.
_Avoid_: Adhan time, schedule

**Prayer Times**:
The full set of five Prayer Times for one day at one location.
_Avoid_: Timetable, schedule

**Prayer Day**:
The span from one Fajr to the next, to which a set of Prayer Times belongs, at the location and time zone the user was in that day. Isha marked after midnight but before Fajr still belongs to the previous Prayer Day.
_Avoid_: Date, calendar day

**Prayer Log**:
The user's record, per Prayer Day and per Prayer, of whether they prayed it. Each entry is either prayed or not marked.
_Avoid_: Tracker, history, journal

**Sunrise**:
The end of the Fajr window. Shown alongside the Prayer Times but is not a Prayer and is never logged.
_Avoid_: Shuruq (in code), sixth prayer

**Calculation Method**:
The named convention of sun angles and rules used to derive Prayer Times (e.g. Muslim World League, Umm al-Qura).
_Avoid_: Calculation authority, organisation, algorithm

**Asr Convention**:
Which shadow-length rule sets the Asr Prayer Time: Standard (Shafi'i, Maliki, Hanbali) or Hanafi.
_Avoid_: Madhab, juristic method, school

**High-Latitude Rule**:
How Fajr and Isha are bounded when the sun doesn't get low enough for the Calculation Method's angles (middle of the night, seventh of the night, or twilight angle; Automatic picks the seventh above about 48°). Where the sun doesn't rise or set at all, Prayer Times come from the nearest latitude where they can be calculated.
_Avoid_: Polar mode, angle-based method

**Prayer Time Adjustment**:
Minutes (−30 to +30) the user adds to one Prayer's calculated time to match their mosque. Sunrise is never adjusted.
_Avoid_: Offset (that word is for the Hijri date), correction

**Manual Location**:
A location the user set by picking a city or typing coordinates. It never changes by itself; the opposite is an Automatic Location, taken from the device's approximate position.
_Avoid_: Fixed location, custom location

**Country Defaults**:
The Calculation Method and Asr Convention suggested for the country the user's location is in, offered with the reason and always overridable (ADR 0003).
_Avoid_: Auto method, region preset

**Prayer Reminder**:
An optional alert a chosen number of minutes before a Prayer Time.
_Avoid_: Pre-alert, warning

**Worship Reminder**:
An optional, gentle reminder for daily worship beyond the five Prayers, under «تذكيرات العبادة» in Settings → Alerts: sending salawat on the Prophet ﷺ every 1, 2 or 3 hours from Sunrise until Isha; reading Surat Al-Kahf on Friday at Sunrise + 1 hour (its «قرأتها» only dismisses it: no Quran text, no Kahf log); and the morning and evening adhkar 15, 30 (default), 45 or 60 minutes after Fajr and Asr, which open that Adhkar Set and are skipped once it is complete for that Prayer Day. Each is off by default, uses the normal notification sound, never an adhan, and is silenced by «كل تنبيهات التطبيق». Not a Prayer Reminder, which comes before a Prayer Time.
_Avoid_: Dhikr reminder, nudge, Sunnah alert

**Ramadan Mode**:
How the app behaves in Ramadan, by the app's Hijri date with the user's Hijri offset: it turns on by itself for every Fasting Day and the suhoor before it, and off after the last one. Today's Next Prayer card counts down to Iftar while fasting and to the end of Suhoor after Maghrib, keeping the next Prayer as one line inside.
_Avoid_: Ramadan setting, fasting mode

**Fasting Day**:
A Prayer Day whose Hijri date (with the user's Hijri offset) is in Ramadan. Its fast runs from Imsak to Maghrib.
_Avoid_: Ramadan day (in code), sawm day

**Imsak**:
The moment a Fasting Day's fast begins and Suhoor ends: Fajr minus a chosen number of minutes (10 by default, 0 to 30), set under «رمضان» in «تذكيرات العبادة».
_Avoid_: Fajr (Imsak comes before it), dawn

**Iftar**:
Breaking the fast at Maghrib on a Fasting Day; the fasting card counts down to it from Imsak.
_Avoid_: Sunset meal, Maghrib alert

**Suhoor**:
The pre-dawn meal before a Fasting Day: from Maghrib (or, on the Fasting Day itself, midnight) until that day's Imsak, and only when the next day is a Fasting Day, so never on the night before Eid.
_Avoid_: Sahur, sehri

**Adhan Alert**:
The alert raised when a Prayer Time arrives, playing either the Short Adhan (default) or the Full Adhan.
_Avoid_: Alarm, prayer notification

**Short Adhan**:
The opening «الله أكبر، الله أكبر» of an adhan recording, used as the default Adhan Alert sound.
_Avoid_: Beep, chime, takbir clip

**Full Adhan**:
A complete adhan recording, which the user can choose to play for an Adhan Alert. Fajr has its own Full Adhan including «الصلاة خير من النوم».
_Avoid_: Long adhan, adhan audio

**Custom Adhan**:
An audio file the user picks from their own phone to play as the Short Adhan, the Full Adhan or Fajr's Full Adhan instead of the bundled recording. Each is chosen separately: a Custom Adhan for the Full Adhan never changes Fajr, which keeps its own adhan with «الصلاة خير من النوم» unless the user picks a Custom Adhan for Fajr's Full Adhan.
_Avoid_: Imported sound, user ringtone

**Prayer Silence**:
An optional setting, off by default, that puts the phone on silent («صامت») or vibrate («اهتزاز») at each Prayer the user keeps on (all five once enabled) and restores its previous sound mode afterwards. It needs Do Not Disturb access, works even with every app alert off, and leaves a phone that is already silent, on vibrate or on Do Not Disturb alone.
_Avoid_: Auto-silent, mute mode, prayer mode

**Silence Window**:
One planned stretch of Prayer Silence: from a Prayer's Prayer Time to a fixed end (20 minutes later by default, 45 for Jumu'ah; both adjustable), carrying the chosen silent or vibrate mode, identified by its Prayer Day and Prayer. The phone is silenced once that Prayer's adhan stops (finished, stopped or not played), or at the Prayer Time when there is no Adhan Alert; the end stays fixed, and a restart during the window still restores the sound at the end (at once if the end has passed).
_Avoid_: Silent period, mute window

**Jumu'ah**:
Dhuhr on a Friday. It is announced as Jumu'ah, and its Silence Window has its own duration, long enough for the khutbah.
_Avoid_: Friday prayer (in code), Friday Dhuhr (as a separate Prayer)

**Widget Variant**:
One of the distinct home-screen widget designs the app offers, each with its own layout and content.
_Avoid_: Widget size, widget type, skin

**Dhikr**:
One remembrance of an Adhkar Set: its Arabic text, how many times it is said, and a short source line (e.g. «رواه مسلم»). Always shown in Arabic. Plural: adhkar.
_Avoid_: Zikr, prayer (a dhikr is never a Prayer), dua (in code)

**Adhkar Set**:
An ordered list of adhkar read together from Hisn al-Muslim: the morning adhkar, the evening adhkar, the after-prayer adhkar. Read one dhikr at a time with a counter; its progress lasts until the end of the Prayer Day, and a restart begins it again.
_Avoid_: Adhkar list, wird, routine

**Tasbeeh**:
The free counter at the top of the Adhkar tab, for the user's own dhikr: each tap counts one with a light vibration. It keeps today's total for the Prayer Day (zero again at the next Fajr) and has a reset. It is not an Adhkar Set and never appears in the Adhkar Record.
_Avoid_: Sebha, misbaha, tally, score

**Adhkar Record**:
Which Adhkar Sets the user completed (every dhikr said its full count) on each Prayer Day. It is not the Prayer Log, and a restart never removes a completed day. A set counts only when finished in its time: the morning adhkar from Fajr to Dhuhr, the evening adhkar from Asr to the next Fajr, the after-prayer adhkar once their Prayer's time has arrived. The after-prayer adhkar are recorded per Prayer, against the most recent Prayer whose time has arrived unless the user picks another before starting. Any set can be read at any time. It is shown as a week grid (a row per Prayer Day, a mark for the morning, the evening and after each Prayer) that counts only from the Adhkar first-use date, the Prayer Day of the first count; days before it are never shown as missed. It shares the Prayer Log's backup rules and is deleted separately («حذف سجل الأذكار»).
_Avoid_: Adhkar log, streak, score

**Backup File**:
One JSON file «mawaqeet-backup-YYYY-MM-DD.json» the user exports from Settings → Backup «النسخة الاحتياطية» and saves wherever they choose with the system file picker. It holds every setting (alerts, reminders, display, widgets), the location and Calculation Method, the Prayer Log (with its first-use date and past places) and the Adhkar Record (with its first-use date); never Custom Adhan files, which the user picks again. It is made only when the user asks and the app never sends it anywhere. It is not Android's cloud backup or a device-to-device transfer, which follow the backup rules.
_Avoid_: Backup (alone, for the file), export, archive, sync

**Clock Widget**:
A Widget Variant built around a big home-screen clock that also shows the interval between two Stops: the previous and next Stop with their times, a countdown in hours and minutes, a bar for how much of the interval has passed, and the Hijri and Gregorian dates. Big Clock «ساعة كبيرة», Gold Clock «ساعة ذهبية» and Clock & Prayer «ساعة وصلاة» are Clock Widgets.
_Avoid_: Clock, digital clock widget

**Stop**:
One end of a Clock Widget's interval: one of the five Prayers or Sunrise (Fajr → Sunrise → Dhuhr). Before Fajr the previous Stop is the previous day's Isha; after Isha the next Stop is the next day's Fajr. Sunrise being a Stop never makes it a Prayer: it still never alerts and is never logged.
_Avoid_: Waypoint, milestone, prayer (for Sunrise)

**Qibla**:
The direction of the Kaaba in Makkah (21.4225°N, 39.8262°E) from the app's stored location: the initial great-circle bearing in degrees from true north, with the great-circle distance to it. The Qibla screen («القبلة») turns a compass dial with the phone, corrected from magnetic to true north, and says when the phone faces it (within 3°). It never asks for a location of its own.
_Avoid_: Kiblah, Mecca direction, prayer direction

**Tools Row**:
The single row of compact buttons on Today, under the Next Prayer card, that opens the app's tools: «القبلة», «التقويم», «المساجد». It is not a tab: the bottom bar stays Today, Prayer Log, Adhkar, Settings.
_Avoid_: Shortcuts, quick actions, toolbar

**Hijri Calendar**:
The Umm al-Qura calendar shifted by the user's Hijri offset, day by day, with each day's Occasions, Sunnah Fasts and whether it is a Forbidden Fast Day. Its screen («التقويم», not a tab) opens from the Tools Row and from the Hijri date on Today; «القادم» lists the Occasions of the next 12 months with only the nearest Monday/Thursday fast and the next white days. Dates may differ by a day from local moon sighting, and the screen says so.
_Avoid_: Islamic calendar (in code), moon calendar, events

**Occasion**:
A fixed day, or run of days, of the Hijri year shown on the Hijri Calendar: Hijri New Year (1 Muharram), Tasu'a and Ashura (9–10 Muharram), the Mawlid (12 Rabi' al-Awwal), Isra and Mi'raj (27 Rajab), the start of Ramadan, the last ten nights (21 Ramadan to its end), Eid al-Fitr (1 Shawwal), the first ten of Dhu al-Hijjah (1–10), Arafah (9), Eid al-Adha (10) and the days of Tashreeq (11–13). The Mawlid and Isra and Mi'raj are dates only, with no reminder.
_Avoid_: Event, holiday, feast

**Sunnah Fast**:
A voluntary fast of the Sunnah, by kind: Mondays and Thursdays, the white days (13–15 of each Hijri month), Arafah, Tasu'a with Ashura, and six of Shawwal (marked on 2–7 Shawwal). Never on a Forbidden Fast Day, and Mondays, Thursdays and the white days never in Ramadan.
_Avoid_: Voluntary fast (in code), nafl fast, optional fast

**Forbidden Fast Day**:
A day on which fasting is forbidden: Eid al-Fitr, Eid al-Adha and the three days of Tashreeq (1 Shawwal, 10–13 Dhu al-Hijjah). It is never a Sunnah Fast, so 13 Dhu al-Hijjah is no white day and a Monday of Tashreeq no Monday fast.
_Avoid_: Eid days (Tashreeq is included), haram day
