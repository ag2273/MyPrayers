package app.mawaqeet.core

import java.io.InputStream
import java.time.LocalDate

/** One dhikr of an [AdhkarSet]: its Arabic [text], how many times it is said, and a short [source] («رواه مسلم»). */
data class Dhikr(val id: String, val text: String, val count: Int, val source: String) {
    init {
        require(count >= 1) { "Dhikr $id: count must be at least 1" }
    }
}

/**
 * Which Adhkar Set. [key] names it in the catalog file and in storage, so it never changes once shipped.
 * The evening and after-prayer sets join here.
 */
enum class AdhkarSetId(val key: String) {
    MORNING("morning"),
    ;

    companion object {
        fun ofKey(key: String): AdhkarSetId? = entries.firstOrNull { it.key == key }
    }
}

/** An ordered list of adhkar read together, e.g. the morning adhkar. */
data class AdhkarSet(val id: AdhkarSetId, val adhkar: List<Dhikr>)

/**
 * The bundled adhkar (app asset `adhkar.tsv`), given to the core as data.
 *
 * The file format: a first line starting with `# mawaqeet-adhkar-v1`, then one dhikr per line, tab-separated:
 * set key, dhikr id (unique within its set, stable: progress is stored by it), repeat count, source, text. In the
 * text `\n` is a line break. Blank lines and lines starting with `#` are skipped; a set's adhkar keep file order.
 * Rows of a set key this version doesn't know are skipped, so a newer file still reads.
 */
class AdhkarCatalog(sets: List<AdhkarSet>) {
    private val byId: Map<AdhkarSetId, AdhkarSet> = sets.associateBy { it.id }

    val sets: List<AdhkarSet> = AdhkarSetId.entries.mapNotNull { byId[it] }

    fun set(id: AdhkarSetId): AdhkarSet = byId[id] ?: AdhkarSet(id, emptyList())

    companion object {
        private const val HEADER = "# mawaqeet-adhkar-v1"

        fun read(input: InputStream): AdhkarCatalog = parse(input.bufferedReader(Charsets.UTF_8).readText())

        fun parse(text: String): AdhkarCatalog {
            val lines = text.lines()
            require(lines.firstOrNull()?.startsWith(HEADER) == true) { "Not a $HEADER file" }
            val rows = lines.drop(1).filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                val c = line.split('\t')
                require(c.size == 5) { "Expected 5 tab-separated columns: $line" }
                val set = AdhkarSetId.ofKey(c[0].trim()) ?: return@mapNotNull null
                set to Dhikr(c[1].trim(), c[4].trim().replace("\\n", "\n"), c[2].trim().toInt(), c[3].trim())
            }
            val sets = rows.groupBy({ it.first }, { it.second }).map { (id, adhkar) ->
                require(adhkar.map { it.id }.toSet().size == adhkar.size) { "Duplicate dhikr id in ${id.key}" }
                AdhkarSet(id, adhkar)
            }
            return AdhkarCatalog(sets)
        }
    }
}

/**
 * Where Adhkar progress and the Adhkar Record live. The Android app implements it with the local database (the
 * Prayer Log's); tests use an in-memory one. A set is named by its slot: today the [AdhkarSetId.key]; after-prayer
 * adhkar, recorded per Prayer, will use one slot per Prayer. Implementations store what they are told; every rule
 * lives in [Adhkar].
 */
interface AdhkarRepository {
    /** How many times each dhikr (by id) of [slot] was counted on [prayerDay]. Uncounted adhkar are absent. */
    fun counts(prayerDay: LocalDate, slot: String): Map<String, Int>

    fun setCount(prayerDay: LocalDate, slot: String, dhikrId: String, count: Int)

    /** Forgets the counts of [slot] on [prayerDay] (a restart). The Adhkar Record is kept. */
    fun clearCounts(prayerDay: LocalDate, slot: String)

    /** Forgets every count of Prayer Days before [prayerDay]: progress lasts only for its Prayer Day. */
    fun clearCountsBefore(prayerDay: LocalDate)

    /** The slots completed on [prayerDay]: the Adhkar Record of that day. */
    fun completedOn(prayerDay: LocalDate): Set<String>

    fun recordCompleted(prayerDay: LocalDate, slot: String)
}

/** One dhikr in a set being read, with how many times it has been said on this Prayer Day. */
data class DhikrProgress(val dhikr: Dhikr, val counted: Int) {
    val left: Int get() = dhikr.count - counted
    val isDone: Boolean get() = counted >= dhikr.count
}

/** A set as read on [prayerDay]: each dhikr's count so far, the dhikr to say now, and the day's record. */
data class SetProgress(
    val set: AdhkarSetId,
    val prayerDay: LocalDate,
    val items: List<DhikrProgress>,
    /** The Adhkar Record: every dhikr reached its count on this Prayer Day (a later restart keeps it). */
    val isComplete: Boolean,
) {
    /** The first dhikr not yet said its full count ("dhikr N of M" is this plus one), or null when all are. */
    val currentIndex: Int? = items.indexOfFirst { !it.isDone }.takeIf { it >= 0 }

    val current: DhikrProgress? get() = currentIndex?.let(items::get)
}

/**
 * The Adhkar core area: reading a set dhikr by dhikr with a counter, and the Adhkar Record per Prayer Day. The
 * Prayer Day (and so when progress resets) comes from [prayers], which owns the clock. Its methods call the
 * repository: off the main thread on Android.
 */
class Adhkar(
    private val catalog: AdhkarCatalog,
    private val prayers: PrayerCore,
    private val repo: AdhkarRepository,
) {
    val sets: List<AdhkarSet> get() = catalog.sets

    /** [set] as read on the current Prayer Day. */
    fun progress(set: AdhkarSetId): SetProgress = progressOn(today(), set)

    /** Counts one repetition of the current dhikr; at its count the next dhikr becomes current. */
    fun count(set: AdhkarSetId): SetProgress = synchronized(repo) {
        val day = today()
        repo.clearCountsBefore(day)
        val before = progressOn(day, set)
        val current = before.current ?: return before
        repo.setCount(day, set.key, current.dhikr.id, current.counted + 1)
        val after = progressOn(day, set)
        if (after.currentIndex == null && !after.isComplete) {
            repo.recordCompleted(day, set.key)
            return after.copy(isComplete = true)
        }
        after
    }

    /** Starts [set] again from its first dhikr; a completed day stays completed. */
    fun restart(set: AdhkarSetId): SetProgress = synchronized(repo) {
        val day = today()
        repo.clearCounts(day, set.key)
        progressOn(day, set)
    }

    /** Whether [set] was completed on [prayerDay] (default: the current Prayer Day). */
    fun isComplete(set: AdhkarSetId, prayerDay: LocalDate = today()): Boolean = set.key in repo.completedOn(prayerDay)

    private fun today(): LocalDate = prayers.currentPrayerDay().date

    private fun progressOn(day: LocalDate, set: AdhkarSetId): SetProgress {
        val counts = repo.counts(day, set.key)
        val items = catalog.set(set).adhkar.map { DhikrProgress(it, (counts[it.id] ?: 0).coerceIn(0, it.count)) }
        return SetProgress(set, day, items, isComplete(set, day))
    }
}
