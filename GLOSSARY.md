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
An optional setting, off by default, that puts the phone on silent at each Prayer and restores its previous sound mode afterwards. It needs Do Not Disturb access, works even with every app alert off, and leaves a phone that is already silent, on vibrate or on Do Not Disturb alone.
_Avoid_: Auto-silent, mute mode, prayer mode

**Silence Window**:
One planned stretch of Prayer Silence: from a Prayer's Prayer Time to a fixed end (20 minutes later by default), identified by its Prayer Day and Prayer.
_Avoid_: Silent period, mute window

**Widget Variant**:
One of the distinct home-screen widget designs the app offers, each with its own layout and content.
_Avoid_: Widget size, widget type, skin

**Dhikr**:
One remembrance of an Adhkar Set: its Arabic text, how many times it is said, and a short source line (e.g. «رواه مسلم»). Always shown in Arabic. Plural: adhkar.
_Avoid_: Zikr, prayer (a dhikr is never a Prayer), dua (in code)

**Adhkar Set**:
An ordered list of adhkar read together from Hisn al-Muslim: the morning adhkar, the evening adhkar, the after-prayer adhkar. Read one dhikr at a time with a counter; its progress lasts until the end of the Prayer Day, and a restart begins it again.
_Avoid_: Adhkar list, wird, routine

**Adhkar Record**:
Which Adhkar Sets the user completed (every dhikr said its full count) on each Prayer Day. It is not the Prayer Log, and a restart never removes a completed day. A set counts only when finished in its time: the morning adhkar from Fajr to Dhuhr, the evening adhkar from Asr to the next Fajr, the after-prayer adhkar once their Prayer's time has arrived. The after-prayer adhkar are recorded per Prayer, against the most recent Prayer whose time has arrived unless the user picks another before starting. Any set can be read at any time.
_Avoid_: Adhkar log, streak, score

**Clock Widget**:
A Widget Variant built around a big home-screen clock that also shows the interval between two Stops: the previous and next Stop with their times, a countdown in hours and minutes, a bar for how much of the interval has passed, and the Hijri and Gregorian dates. Big Clock «ساعة كبيرة», Gold Clock «ساعة ذهبية» and Clock & Prayer «ساعة وصلاة» are Clock Widgets.
_Avoid_: Clock, digital clock widget

**Stop**:
One end of a Clock Widget's interval: one of the five Prayers or Sunrise (Fajr → Sunrise → Dhuhr). Before Fajr the previous Stop is the previous day's Isha; after Isha the next Stop is the next day's Fajr. Sunrise being a Stop never makes it a Prayer: it still never alerts and is never logged.
_Avoid_: Waypoint, milestone, prayer (for Sunrise)
