package app.mawaqeet.ui.adhkar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Misbaha's string: beads cross the thumb's gap one at a time, never overlap, and settle where they belong. */
class BeadStringTest {
    private val r = 30f

    /** Runs the springs at 60 frames a second until nothing moves (or [maxMs]); checks no two beads ever overlap (beyond 3%, too little to see). */
    private fun BeadString.settle(from: Long = 0L, maxMs: Long = 4_000): Long {
        var now = from
        while (now - from < maxMs) {
            now += 16
            val busy = step(now, .016f)
            assertNoOverlap()
            if (!busy) return now
        }
        throw AssertionError("still moving after $maxMs ms")
    }

    private fun BeadString.assertNoOverlap() {
        beads.zipWithNext().forEach { (up, lo) ->
            if (lo.position != up.position - 1) return@forEach
            val half = { b: BeadString.Bead -> if (b.isDivider) r * BeadString.DIVIDER_LENGTH else r }
            assertTrue("beads ${up.position} (y ${up.y}) and ${lo.position} (y ${lo.y}) overlap", lo.y - up.y >= (half(up) + half(lo)) * .97f)
        }
    }

    private fun BeadString.above() = beads.filter { it.y < thumbY }.map { it.position }
    private fun BeadString.below() = beads.filter { it.y > thumbY }.map { it.position }

    @Test
    fun `a divider follows every 33 beads`() {
        assertEquals(0, BeadString.cursorOf(0))
        assertEquals(32, BeadString.cursorOf(32))
        assertEquals(34, BeadString.cursorOf(33))
        assertTrue(BeadString.isDivider(33))
        assertFalse(BeadString.isDivider(34))
        assertEquals(68, BeadString.cursorOf(66))
        assertTrue(BeadString.isDivider(67))
    }

    @Test
    fun `uncounted beads wait above the thumb and counted ones hang below`() {
        val string = BeadString(r, 900f, 10)
        assertEquals(10, string.above().min())
        assertEquals(9, string.below().max())
    }

    @Test
    fun `a count moves the bead under the thumb across the gap, and it settles without overlapping`() {
        val string = BeadString(r, 900f, 10)
        var strikes = 0
        string.onStrike = { strikes++ }
        string.count(11, now = 0L)
        string.settle()
        assertEquals(11, string.above().min())
        assertEquals(10, string.below().max())
        assertEquals("the bead strikes the pile once", 1, strikes)
    }

    @Test
    fun `the 33rd bead takes the divider over with it`() {
        val string = BeadString(r, 900f, 32)
        string.count(33, now = 0L)
        string.settle()
        assertEquals(34, string.above().min())
        assertTrue("the divider is below the thumb", 33 in string.below())
    }

    @Test
    fun `counting fast keeps every bead apart`() {
        val string = BeadString(r, 900f, 0)
        var now = 0L
        for (n in 1..40) {
            string.count(n, now)
            repeat(4) { now += 16; string.step(now, .016f); string.assertNoOverlap() }
        }
        string.settle(now)
        assertEquals(BeadString.cursorOf(40), string.above().min())
    }

    @Test
    fun `undo brings the bead back up`() {
        val string = BeadString(r, 900f, 5)
        string.count(4, now = 0L)
        string.settle()
        assertEquals(4, string.above().min())
    }

    @Test
    fun `a short pull springs back, a long pull counts`() {
        val string = BeadString(r, 900f, 3)
        string.drag(10f, 50f)
        string.step(16, .016f)
        assertFalse(string.release(0L))
        string.settle(16)
        assertEquals(3, string.above().min())

        string.drag(string.gap, 900f)
        string.step(32, .016f)
        assertTrue(string.release(0L))
    }

    @Test
    fun `a reset snaps the string back to the start`() {
        val string = BeadString(r, 900f, 64)
        string.count(0, now = 0L)
        assertFalse(string.step(16, .016f))
        assertEquals(0, string.above().min())
        assertTrue(string.below().isEmpty())
    }
}
