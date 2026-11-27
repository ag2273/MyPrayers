package app.mawaqeet.ui.adhkar

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The Misbaha's string (1.5): where every bead sits and how it moves, with no drawing in it.
 *
 * The string hangs vertically. Beads not yet counted rest above the thumb's gap, counted beads below it. The string
 * is endless: round k holds beads at positions `k*34 .. k*34+32` and its divider at `k*34+33`, so a divider passes
 * after every 33rd bead. [count] moves the bead under the thumb across the gap: it slides, strikes the pile below and
 * settles; the beads above drop after it one by one. Beads are solid: one never passes into the next, and a moving
 * bead pushes the one it meets. A bead can also be pulled down by the thumb ([drag], [release]).
 *
 * Sizes are in pixels and time in milliseconds; [step] advances the springs and reports whether anything still moves.
 */
internal class BeadString(
    /** A round bead's radius. The divider is longer along the string. */
    val radius: Float,
    /** The height of the string's view; the thumb's gap is in its middle. */
    val height: Float,
    count: Int,
) {
    class Bead internal constructor(val position: Int) {
        val isDivider: Boolean get() = isDivider(position)
        var y = 0f; internal set
        internal var velocity = 0f
        internal var target = 0f
        internal var stiffness = 160f
        internal var damping = .8f
        internal var startAt = 0L
        internal var struck = false
        internal var pulled = false
    }

    /** Where the thumb rests on the string: the centre of the gap. */
    val thumbY: Float get() = height / 2
    val gap: Float = radius * 2.7f
    private val pitch = radius * 2.02f
    private val span = ceil(height / pitch / 2).toInt() + 3
    private val items = sortedMapOf<Int, Bead>()
    private var drag: Float? = null
    private var dragVelocity = 0f

    /** Called once when the counted bead strikes the pile below (a divider knocks lower). */
    var onStrike: (divider: Boolean) -> Unit = {}

    var count: Int = count
        private set

    /** The beads on screen, from the top of the string to the bottom. */
    val beads: List<Bead> get() = items.values.reversed()

    init {
        place(snap = true, moved = 0, now = 0L)
    }

    /** Moves to [newCount]: one bead down for a count, back up for an undo; a bigger jump (a reset) snaps. */
    fun count(newCount: Int, now: Long) {
        val moved = newCount - count
        count = newCount.coerceAtLeast(0)
        place(snap = abs(moved) != 1, moved = moved, now = now)
    }

    /** The thumb pulls the next bead down by [dy] pixels (upwards barely moves it), moving at [velocity] px/s. */
    fun drag(dy: Float, velocity: Float) {
        drag = if (dy < 0) dy * .15f else min(dy, gap + pitch)
        dragVelocity = velocity
    }

    /**
     * The thumb lets go. Returns whether that counts a bead: pulled far or fast enough. The caller then calls [count];
     * otherwise the bead springs back.
     */
    fun release(now: Long): Boolean {
        val pulledBy = drag ?: return false
        drag = null
        items[cursorOf(count)]?.let { it.velocity = dragVelocity; it.startAt = now; it.pulled = false }
        return pulledBy > gap * .35f || dragVelocity > 500f
    }

    /** Advances every spring by [dt] seconds at time [now]; returns whether anything still moves. */
    fun step(now: Long, dt: Float): Boolean {
        val dtc = min(dt, .032f)
        var busy = drag != null
        val cursor = cursorOf(count)
        for (b in items.values) {
            val pulled = drag
            if (pulled != null && b.position == cursor) {
                b.y = b.target + pulled; b.velocity = dragVelocity; b.pulled = true
                continue
            }
            b.pulled = false
            if (now < b.startAt) { busy = true; continue }
            val c = 2 * b.damping * sqrt(b.stiffness)
            b.velocity += (-b.stiffness * (b.y - b.target) - c * b.velocity) * dtc
            b.y += b.velocity * dtc
            if (abs(b.y - b.target) > .05f || abs(b.velocity) > .5f) busy = true else { b.y = b.target; b.velocity = 0f }
        }
        // Solid beads: wherever two touch, push them apart (a pulled bead holds its place) and share their speed like a
        // soft knock. Passes up and down the string settle a whole chain.
        val ordered = beads
        for (pass in 0 until 40) {
            var touched = false
            // alternate the direction so a push travels both up and down a chain in a few passes
            for (k in 1 until ordered.size) {
                val i = if (pass % 2 == 0) k else ordered.size - k
                val up = ordered[i - 1]
                val lo = ordered[i]
                if (lo.position != up.position - 1) continue
                val overlap = halfLength(up) + halfLength(lo) - (lo.y - up.y)
                if (overlap <= .01f) continue
                touched = true
                when {
                    up.pulled -> lo.y += overlap
                    lo.pulled -> up.y -= overlap
                    else -> { up.y -= overlap / 2; lo.y += overlap / 2 }
                }
                val closing = up.velocity - lo.velocity
                if (closing > 0) {
                    if (up.stiffness == STRUCK_STIFFNESS && !lo.struck) { lo.struck = true; onStrike(up.isDivider) }
                    val shared = (up.velocity + lo.velocity) / 2
                    if (!up.pulled) up.velocity = shared - closing * RESTITUTION / 2
                    lo.velocity = shared + closing * RESTITUTION / 2
                }
                up.startAt = min(up.startAt, now); lo.startAt = min(lo.startAt, now)
            }
            if (!touched) break
            busy = true
        }
        return busy
    }

    private fun halfLength(b: Bead) = (if (b.isDivider) radius * DIVIDER_LENGTH else radius) * .995f

    private fun place(snap: Boolean, moved: Int, now: Long) {
        val cursor = cursorOf(count)
        val targets = HashMap<Int, Float>()
        var y = thumbY - gap / 2
        for (p in cursor until cursor + span) {
            val h = if (isDivider(p)) radius * DIVIDER_LENGTH else radius
            y -= h; targets[p] = y; y -= h + pitch - 2 * radius
        }
        y = thumbY + gap / 2
        for (p in cursor - 1 downTo max(0, cursor - span)) {
            val h = if (isDivider(p)) radius * DIVIDER_LENGTH else radius
            y += h; targets[p] = y; y += h + pitch - 2 * radius
        }
        if (snap) items.clear()
        items.keys.retainAll(targets.keys)
        for ((p, ty) in targets) {
            val b = items.getOrPut(p) {
                Bead(p).also { it.y = if (snap || moved == 0) ty else ty + if (moved > 0) -pitch else pitch }
            }
            b.target = ty
            when {
                snap -> { b.y = ty; b.velocity = 0f }
                moved > 0 && p == cursor - 1 -> { b.stiffness = STRUCK_STIFFNESS; b.damping = .5f; b.startAt = now }
                moved > 0 && p >= cursor -> { b.stiffness = 150f; b.damping = .62f; b.startAt = now + 40 + (p - cursor) * 26L }
                moved > 0 -> { b.stiffness = 170f; b.damping = .7f; b.startAt = now + 160 + (cursor - 2 - p) * 14L; b.struck = false }
                else -> { b.stiffness = 160f; b.damping = .75f; b.startAt = now + abs(p - cursor) * 14L }
            }
        }
    }

    companion object {
        const val BEADS_PER_ROUND = 33
        /** A divider's half length, in round-bead radii. */
        const val DIVIDER_LENGTH = 1.45f
        private const val STRUCK_STIFFNESS = 210f
        /** How much of the closing speed bounces back when two beads knock. */
        private const val RESTITUTION = .25f

        /** The string position of the bead under the thumb after [count] beads (each round adds a divider). */
        fun cursorOf(count: Int): Int = count / BEADS_PER_ROUND * (BEADS_PER_ROUND + 1) + count % BEADS_PER_ROUND

        fun isDivider(position: Int): Boolean = position % (BEADS_PER_ROUND + 1) == BEADS_PER_ROUND
    }
}
