package app.mawaqeet.core

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class AlertPlaybackTest {
    private fun decide(
        sound: AlertSound,
        ringer: RingerMode = RingerMode.NORMAL,
        inCall: Boolean = false,
        late: Duration = Duration.ZERO,
        playOnSilent: Boolean = false,
        vibration: Boolean = true,
    ) = AlertPlayback.decide(sound, DeviceAudio(ringer, inCall), late, playOnSilent, vibration)

    @Test
    fun `with play-on-silent the adhan plays in silent mode, still without vibrating`() {
        assertEquals(AlertPlayback(play = AlertSound.FullAdhan, vibrate = false), decide(AlertSound.FullAdhan, RingerMode.SILENT, playOnSilent = true))
    }

    @Test
    fun `with play-on-silent the adhan plays in vibrate mode and vibrates`() {
        assertEquals(AlertPlayback(play = AlertSound.ShortAdhan, vibrate = true), decide(AlertSound.ShortAdhan, RingerMode.VIBRATE, playOnSilent = true))
    }

    @Test
    fun `play-on-silent never overrides the call and lateness rules or a silent alert`() {
        assertEquals(null, decide(AlertSound.FullAdhan, RingerMode.SILENT, inCall = true, playOnSilent = true).play)
        assertEquals(null, decide(AlertSound.ShortAdhan, RingerMode.SILENT, late = Duration.ofMinutes(11), playOnSilent = true).play)
        assertEquals(null, decide(AlertSound.Silent, RingerMode.SILENT, playOnSilent = true).play)
    }

    @Test
    fun `with vibration turned off the phone never vibrates`() {
        assertEquals(AlertPlayback(play = AlertSound.ShortAdhan, vibrate = false), decide(AlertSound.ShortAdhan, vibration = false))
        assertEquals(AlertPlayback(play = null, vibrate = false), decide(AlertSound.ShortAdhan, RingerMode.VIBRATE, vibration = false))
    }

    @Test
    fun `a Prayer Reminder chimes with the ringer on, even during a call, but play-on-silent does not apply to it`() {
        assertEquals(AlertPlayback(play = AlertSound.ReminderTone, vibrate = true), decide(AlertSound.ReminderTone, inCall = true))
        assertEquals(AlertPlayback(play = null, vibrate = false), decide(AlertSound.ReminderTone, RingerMode.SILENT, playOnSilent = true))
        assertEquals(AlertPlayback(play = null, vibrate = true), decide(AlertSound.ReminderTone, RingerMode.VIBRATE, playOnSilent = true))
    }

    @Test
    fun `with the ringer on, the Short Adhan plays with a short vibration`() {
        assertEquals(AlertPlayback(play = AlertSound.ShortAdhan, vibrate = true), decide(AlertSound.ShortAdhan))
    }

    @Test
    fun `in silent mode the adhan neither sounds nor vibrates, although the alarm stream would play`() {
        assertEquals(AlertPlayback(play = null, vibrate = false), decide(AlertSound.FullAdhan, RingerMode.SILENT))
    }

    @Test
    fun `in vibrate mode the adhan is silent but the phone vibrates`() {
        assertEquals(AlertPlayback(play = null, vibrate = true), decide(AlertSound.ShortAdhan, RingerMode.VIBRATE))
    }

    @Test
    fun `during a phone call the Full Adhan is skipped but the phone still vibrates`() {
        assertEquals(AlertPlayback(play = null, vibrate = true), decide(AlertSound.FullAdhan, inCall = true))
        assertEquals(AlertPlayback(play = null, vibrate = true), decide(AlertSound.FajrFullAdhan, inCall = true))
    }

    @Test
    fun `during a phone call the Short Adhan still plays`() {
        assertEquals(AlertPlayback(play = AlertSound.ShortAdhan, vibrate = true), decide(AlertSound.ShortAdhan, inCall = true))
    }

    @Test
    fun `during a phone call a Custom Adhan is skipped, since its length is unknown`() {
        assertEquals(AlertPlayback(play = null, vibrate = true), decide(AlertSound.CustomAdhan("content://x"), inCall = true))
    }

    @Test
    fun `a silent alert only vibrates`() {
        assertEquals(AlertPlayback(play = null, vibrate = true), decide(AlertSound.Silent))
    }

    @Test
    fun `an alert delivered more than 10 minutes late, e g after the phone was off, is never replayed aloud`() {
        assertEquals(AlertPlayback(play = null, vibrate = true), decide(AlertSound.ShortAdhan, late = Duration.ofMinutes(11)))
        assertEquals(
            AlertPlayback(play = AlertSound.ShortAdhan, vibrate = true),
            decide(AlertSound.ShortAdhan, late = Duration.ofMinutes(9)),
        )
    }
}
