package app.mawaqeet.ui.qibla

import org.junit.Assert.assertEquals
import org.junit.Test

/** The dial turns the short way to each new heading, so passing north never spins it round. */
class HeadingSmoothingTest {
    @Test
    fun `passing north is a small step, not a full turn`() {
        assertEquals(361.0, unwrapToward(359.0, 1.0), 1e-9)
        assertEquals(-1.0, unwrapToward(1.0, 359.0), 1e-9)
    }

    @Test
    fun `after many turns the next heading is still the nearest angle`() {
        assertEquals(750.0, unwrapToward(730.0, 30.0), 1e-9)
        assertEquals(-350.0, unwrapToward(-355.0, 10.0), 1e-9)
    }
}
