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
    ) = AlertPlayback.decide(sound, DeviceAudio(ringer, inCall), late)

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
