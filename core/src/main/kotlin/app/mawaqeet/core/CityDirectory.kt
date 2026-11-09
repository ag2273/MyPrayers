package app.mawaqeet.core

import java.io.InputStream
import java.text.Normalizer
import java.time.DateTimeException
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A bundled city: GeoNames id, English name, Arabic name (null when the sources have none), country, zone. */
data class City(
    val id: Long,
    val name: String,
    val arabicName: String?,
    /** ISO 3166-1 alpha-2, upper case: feeds [CountryDefaults]. */
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
) {
    /** The city centre as a location for Prayer Times. */
    val location: Location get() = Location(latitude, longitude, zone)

    /** The name to show: Arabic when asked for and known, otherwise the English name. */
    fun displayName(arabic: Boolean): String = if (arabic) arabicName ?: name else name

    /** This city as stored with a [SavedLocation]. */
    fun ref(isNear: Boolean = false): CityRef = CityRef(id, name, arabicName, countryCode, isNear)
}

/**
 * The bundled city closest to a point. [location] is the *point itself* with that city's time zone: how manual
 * coordinates (and any other point) get a zone and a country offline.
 */
data class NearestCity(val city: City, val distanceKm: Double, val location: Location) {
    /** The point as a manual [SavedLocation], named "near <city>". */
    fun toSavedLocation(source: LocationSource = LocationSource.MANUAL): SavedLocation =
        SavedLocation(location, source, city.ref(isNear = true))
}

/**
 * The offline city list (app asset `cities.tsv`, written by tools/cities/generate_cities.py): search by Arabic
 * or English name, look up by id, and find the nearest city to a point. Reading it takes a moment; build one
 * per process, off the main thread.
 */
class CityDirectory(cities: List<City>) {

    /** In dataset order: largest population first. */
    val cities: List<City> = cities

    private val keys: List<List<String>> = cities.map { city ->
        val arabic = city.arabicName?.let(::normalize)
        listOfNotNull(normalize(city.name), arabic, arabic?.takeIf { it.startsWith("ال") }?.substring(2))
    }
    private val byId: Map<Long, City> = cities.associateBy { it.id }

    /**
     * Cities whose English or Arabic name matches [query], best first: whole name, then name prefix, then the
     * start of any word; Arabic «ال» may be omitted; larger cities first within each. Ignores case, Latin and
     * Arabic diacritics, hamza forms, ة/ه and ى/ي. A blank query matches nothing.
     */
    fun search(query: String, limit: Int = 20): List<City> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val ranked = arrayOf(ArrayList<City>(), ArrayList<City>(), ArrayList<City>())
        cities.forEachIndexed { i, city ->
            val rank = keys[i].minOf { rank(it, q) }
            if (rank < ranked.size && ranked[rank].size < limit) ranked[rank].add(city)
        }
        return ranked.flatMap { it }.take(limit)
    }

    fun byId(id: Long): City? = byId[id]

    /** The nearest bundled city to a point, by great-circle distance. */
    fun nearest(latitude: Double, longitude: Double): NearestCity {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Not a point: $latitude, $longitude" }
        var best = cities.first()
        var bestKm = Double.MAX_VALUE
        for (city in cities) {
            val km = distanceKm(latitude, longitude, city.latitude, city.longitude)
            if (km < bestKm) {
                best = city
                bestKm = km
            }
        }
        return NearestCity(best, bestKm, Location(latitude, longitude, best.zone))
    }

    private fun rank(key: String, q: String): Int = when {
        key == q -> 0
        key.startsWith(q) -> 1
        " $key".contains(" $q") || " $key".contains(" ال$q") -> 2
        else -> 3
    }

    companion object {
        private const val HEADER = "# mawaqeet-cities-v1"

        /** Reads the `cities.tsv` format. Rows whose time zone this device's tz database lacks are skipped. */
        fun read(input: InputStream): CityDirectory {
            val zones = HashMap<String, ZoneId?>()
            val lines = input.bufferedReader(Charsets.UTF_8).readLines()
            require(lines.firstOrNull()?.startsWith(HEADER) == true) { "Not a $HEADER file" }
            val cities = lines.asSequence().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
                val c = line.split('\t')
                val zone = zones.getOrPut(c[6]) {
                    try { ZoneId.of(c[6]) } catch (_: DateTimeException) { null }
                } ?: return@mapNotNull null
                City(c[0].toLong(), c[1], c[2].ifEmpty { null }, c[3], c[4].toDouble(), c[5].toDouble(), zone)
            }.toList()
            return CityDirectory(cities)
        }

        private val MARKS = Regex("\\p{Mn}+")
        private val SEPARATORS = Regex("[\\s\\-_,.]+")
        private val DROPPED = Regex("['’‘`ʻʼـ]")

        /** Search folding: lower case, no marks (this also turns أ إ آ into ا, ؤ into و, ئ into ي), ة→ه, ى→ي. */
        internal fun normalize(text: String): String =
            MARKS.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
                .replace(DROPPED, "")
                .replace('ı', 'i').replace('ة', 'ه').replace('ى', 'ي')
                .replace(SEPARATORS, " ")
                .trim()
    }
}

/** Great-circle distance in km between two points. */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(h.coerceAtMost(1.0)))
}
