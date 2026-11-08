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
The day a set of Prayer Times belongs to, at the location and time zone the user was in that day. A Prayer marked after midnight still belongs to the Prayer Day its Prayer Time came from.
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

**Prayer Reminder**:
An optional alert a chosen number of minutes before a Prayer Time.
_Avoid_: Pre-alert, warning

**Adhan Alert**:
The alert raised when a Prayer Time arrives, optionally playing the full adhan sound.
_Avoid_: Alarm, prayer notification

**Widget Variant**:
One of the distinct home-screen widget designs the app offers, each with its own layout and content.
_Avoid_: Widget size, widget type, skin
