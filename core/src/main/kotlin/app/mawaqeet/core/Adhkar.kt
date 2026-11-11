package app.mawaqeet.core

import java.io.InputStream
import java.time.LocalDate

/** One dhikr of an [AdhkarSet]: its Arabic [text], how many times it is said, and a short [source] («رواه مسلم»). */
data class Dhikr(val id: String, val text: String, val count: Int, val source: String) {
    init {
        require(count >= 1) { "Dhikr $id: count must be at least 1" }
    }
}

/** Which Adhkar Set. [key] names it in the catalog file and in storage, so it never changes once shipped. */
enum class AdhkarSetId(val key: String) {
    MORNING("morning"),
    EVENING("evening"),

    /** Read after each Prayer, and recorded per Prayer: see [AdhkarSlot]. */
    AFTER_PRAYER("after-prayer"),
    ;

    companion object {
        fun ofKey(key: String): AdhkarSetId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One Adhkar Set as it is read and recorded on a Prayer Day: the morning, the evening, or the after-prayer adhkar
 * after one [prayer] (five slots a day). [key] names it in storage, so it never changes once shipped.
 */
data class AdhkarSlot(val set: AdhkarSetId, val prayer: Prayer? = null) {
    init {
        require((set == AdhkarSetId.AFTER_PRAYER) == (prayer != null)) { "Only after-prayer adhkar have a Prayer" }
    }

    val key: String get() = if (prayer == null) set.key else "${set.key}:${prayer.name.lowercase()}"

    companion object {
        val MORNING = AdhkarSlot(AdhkarSetId.MORNING)
        val EVENING = AdhkarSlot(AdhkarSetId.EVENING)

        fun afterPrayer(prayer: Prayer) = AdhkarSlot(AdhkarSetId.AFTER_PRAYER, prayer)

        /** Every slot of a Prayer Day, in list order: morning, evening, then after each Prayer. */
        val all: List<AdhkarSlot> = listOf(MORNING, EVENING) + Prayer.entries.map(::afterPrayer)

        fun ofKey(key: String): AdhkarSlot? = all.firstOrNull { it.key == key }

        /** The one slot of a morning or evening set. After-prayer adhkar have five: name the Prayer instead. */
        fun of(set: AdhkarSetId): AdhkarSlot {
            require(set != AdhkarSetId.AFTER_PRAYER) { "After-prayer adhkar need a Prayer: use afterPrayer(prayer)" }
            return AdhkarSlot(set)
        }
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
 * Prayer Log's); tests use an in-memory one. A set is named by its [AdhkarSlot.key]: one for the morning, one for the
 * evening, one per Prayer for the after-prayer adhkar. Implementations store what they are told; every rule lives
 * in [Adhkar].
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

    /** The Prayer Day of the first count: the Adhkar Record counts from it. Null before anything is counted. */
    fun firstUseDate(): LocalDate?

    fun recordFirstUseIfAbsent(prayerDay: LocalDate)

    /**
     * Removes the whole Adhkar Record and every count, and records [firstUseFrom] as the first-use date, replacing
     * any recorded one (Settings → Prayer Log → Delete Adhkar Record).
     */
    fun deleteRecord(firstUseFrom: LocalDate)
}

/** One slot of a Prayer Day in the Adhkar Record week grid. */
enum class AdhkarMark {
    /** Every dhikr said its full count, in the set's time. */
    COMPLETED,

    /** Its time arrived (or the day is past) and it was not completed. */
    NOT_COMPLETED,

    /** Today, before its time: the evening before Asr, an after-prayer slot before its Prayer. */
    NOT_YET_DUE,

    /** A Prayer Day before the first count: never shown as missed. */
    BEFORE_FIRST_USE,
}

/** One row of the Adhkar Record week: a Prayer Day with a mark per [AdhkarSlot]. */
data class AdhkarRecordDay(val date: LocalDate, val hijriDate: HijriDate, val marks: Map<AdhkarSlot, AdhkarMark>) {
    fun of(slot: AdhkarSlot): AdhkarMark = marks.getValue(slot)
}

/**
 * Seven consecutive Prayer Days of the Adhkar Record, oldest first, as the Adhkar tab's week grid shows them. Only
 * marks: no streaks, badges or scores.
 */
data class AdhkarWeek(
    val days: List<AdhkarRecordDay>,
    /** The first-use date (or the current Prayer Day if nothing was counted yet); earlier days are never missed. */
    val countedFrom: LocalDate,
)

/** One dhikr in a set being read, with how many times it has been said on this Prayer Day. */
data class DhikrProgress(val dhikr: Dhikr, val counted: Int) {
    val left: Int get() = dhikr.count - counted
    val isDone: Boolean get() = counted >= dhikr.count
}

/** A slot as read on [prayerDay]: each dhikr's count so far, the dhikr to say now, and the day's record. */
data class SetProgress(
    val slot: AdhkarSlot,
    val prayerDay: LocalDate,
    val items: List<DhikrProgress>,
    /** The Adhkar Record: every dhikr reached its count on this Prayer Day (a later restart keeps it). */
    val isComplete: Boolean,
    /**
     * Whether finishing the set now goes in the Adhkar Record: morning from Fajr to Dhuhr, evening from Asr to the
     * next Fajr, after-prayer once its Prayer's time has arrived. Outside it the set can still be read.
     */
    val isInTime: Boolean,
) {
    val set: AdhkarSetId get() = slot.set

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

    /** The slots of the bundled sets, in list order: morning, evening, then after each of the five Prayers. */
    val slots: List<AdhkarSlot> get() = catalog.sets.map { it.id }.let { ids -> AdhkarSlot.all.filter { it.set in ids } }

    /** [slot] as read on the current Prayer Day. */
    fun progress(slot: AdhkarSlot): SetProgress = progressOn(prayers.currentPrayerDay(), slot)

    /** Counts one repetition of the current dhikr; at its count the next dhikr becomes current. */
    fun count(slot: AdhkarSlot): SetProgress = synchronized(repo) {
        val day = prayers.currentPrayerDay()
        repo.clearCountsBefore(day.date)
        val before = progressOn(day, slot)
        val current = before.current ?: return before
        repo.recordFirstUseIfAbsent(day.date)
        repo.setCount(day.date, slot.key, current.dhikr.id, current.counted + 1)
        val after = progressOn(day, slot)
        if (after.currentIndex == null && !after.isComplete && after.isInTime) {
            repo.recordCompleted(day.date, slot.key)
            return after.copy(isComplete = true)
        }
        after
    }

    /** Starts [slot] again from its first dhikr; a completed day stays completed. */
    fun restart(slot: AdhkarSlot): SetProgress = synchronized(repo) {
        val day = prayers.currentPrayerDay()
        repo.clearCounts(day.date, slot.key)
        progressOn(day, slot)
    }

    /** Whether [slot] was completed on [prayerDay] (default: the current Prayer Day). */
    fun isComplete(slot: AdhkarSlot, prayerDay: LocalDate = today()): Boolean = slot.key in repo.completedOn(prayerDay)

    /** The morning or evening [set] (see [AdhkarSlot.of]). */
    fun progress(set: AdhkarSetId): SetProgress = progress(AdhkarSlot.of(set))

    fun count(set: AdhkarSetId): SetProgress = count(AdhkarSlot.of(set))

    fun restart(set: AdhkarSetId): SetProgress = restart(AdhkarSlot.of(set))

    /** Whether the morning or evening [set] was completed on [prayerDay]: what a Worship Reminder checks. */
    fun isComplete(set: AdhkarSetId, prayerDay: LocalDate = today()): Boolean = isComplete(AdhkarSlot.of(set), prayerDay)

    /**
     * The Prayer the after-prayer adhkar are recorded against unless the user picks another: the most recent
     * Prayer whose time has arrived on the current Prayer Day (Isha after midnight, Fajr from Fajr).
     */
    fun defaultAfterPrayer(): Prayer = prayers.currentPrayerDay().let { day ->
        day.prayerTimes.last { day.hasArrived(it.prayer) }.prayer
    }

    /**
     * The Adhkar Record of the seven Prayer Days ending with [lastDay] (the current Prayer Day by default), oldest
     * first. Browse older weeks with `lastDay = <previous lastDay>.minusDays(7)`.
     */
    fun week(lastDay: LocalDate? = null): AdhkarWeek {
        val today = prayers.currentPrayerDay()
        val end = lastDay ?: today.date
        val countedFrom = repo.firstUseDate() ?: today.date
        val days = (PrayerCore.WEEK_DAYS - 1 downTo 0L).map { back ->
            val date = end.minusDays(back)
            val day = if (date == today.date) today else prayers.prayerDay(date)
            val completed = repo.completedOn(date)
            val marks = AdhkarSlot.all.associateWith { slot ->
                when {
                    slot.key in completed -> AdhkarMark.COMPLETED
                    date < countedFrom -> AdhkarMark.BEFORE_FIRST_USE
                    !day.isDue(slot) -> AdhkarMark.NOT_YET_DUE
                    else -> AdhkarMark.NOT_COMPLETED
                }
            }
            AdhkarRecordDay(date, day.hijriDate, marks)
        }
        return AdhkarWeek(days, countedFrom)
    }

    /** Permanently deletes the Adhkar Record (after the user confirms); it then counts from the current Prayer Day. */
    fun deleteRecord() = synchronized(repo) { repo.deleteRecord(firstUseFrom = today()) }

    private fun today(): LocalDate = prayers.currentPrayerDay().date

    private fun progressOn(day: PrayerDay, slot: AdhkarSlot): SetProgress {
        val counts = repo.counts(day.date, slot.key)
        val items = catalog.set(slot.set).adhkar.map { DhikrProgress(it, (counts[it.id] ?: 0).coerceIn(0, it.count)) }
        return SetProgress(slot, day.date, items, isComplete(slot, day.date), isInTime(day, slot))
    }

    /** The current Prayer Day runs from its Fajr, so only the far end of each window needs checking. */
    private fun isInTime(day: PrayerDay, slot: AdhkarSlot): Boolean = when (slot.set) {
        AdhkarSetId.MORNING -> !day.hasArrived(Prayer.DHUHR)
        AdhkarSetId.EVENING -> day.hasArrived(Prayer.ASR)
        AdhkarSetId.AFTER_PRAYER -> day.hasArrived(checkNotNull(slot.prayer))
    }

    /** Whether [slot]'s time has arrived on this Prayer Day (the morning's at Fajr, so always). */
    private fun PrayerDay.isDue(slot: AdhkarSlot): Boolean = when (slot.set) {
        AdhkarSetId.MORNING -> true
        AdhkarSetId.EVENING -> hasArrived(Prayer.ASR)
        AdhkarSetId.AFTER_PRAYER -> hasArrived(checkNotNull(slot.prayer))
    }

    private fun PrayerDay.hasArrived(prayer: Prayer): Boolean = stateOf(prayer) != LogState.NOT_YET_DUE
}
