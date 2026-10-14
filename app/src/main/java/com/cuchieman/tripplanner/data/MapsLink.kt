package com.cuchieman.tripplanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

/**
 * Turns what people paste into a place: a Google Maps link (short or long) or plain
 * coordinates. For places the geocoder cannot find, such as a hermitage or a reserve's
 * visitor centre, a link from Google Maps is the most reliable way in.
 */
object MapsLink {
    private val coords = Regex("""^\s*(-?\d{1,2}\.\d+)\s*[,\s]\s*(-?\d{1,3}\.\d+)\s*$""")
    private val pin = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val query = Regex("""[?&](?:q|query|ll|destination)=(-?\d+\.\d+)(?:,|%2C)(-?\d+\.\d+)""", RegexOption.IGNORE_CASE)
    private val viewport = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val placeName = Regex("""/maps/place/([^/@?]+)""")
    private val consentContinue = Regex("""[?&]continue=([^&]+)""")
    private val googleHost = Regex("""(www\.)?google\.[a-z.]+""")

    /** "38.15328, -0.6903343" (comma or space). Decimals required; whole numbers are more likely a typo. */
    fun coordinates(text: String): Pair<Double, Double>? {
        val m = coords.matchEntire(text) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lng = m.groupValues[2].toDouble()
        return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
    }

    private val urlInText = Regex("""https?://[^\s<>"«»]+""")
    private val coordsInText = Regex("""(-?\d{1,2}\.\d{4,})\s*,\s*(-?\d{1,3}\.\d{4,})""")

    /**
     * The first Google Maps link inside any text. "Compartir" in the Maps app copies
     * "Name\nAddress\nhttps://maps.app.goo.gl/…?g_st=aw", not just the link.
     */
    fun findLink(text: String): String? =
        urlInText.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')') }
            .firstOrNull { isLink(it) }

    /** The first non-blank line before the link: the place's name in what the Maps app shares. */
    fun labelBefore(text: String): String? {
        val link = urlInText.find(text) ?: return null
        return text.substring(0, link.range.first)
            .lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && coordinates(it) == null }
    }

    /** Coordinates inside a longer text (a shared dropped pin). Needs 4+ decimals, so times like 7.00 are not taken. */
    fun coordinatesIn(text: String): Pair<Double, Double>? {
        val m = coordsInText.find(text) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lng = m.groupValues[2].toDouble()
        return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
    }

    /**
     * Anything pasted, turned into a place: plain coordinates, a Maps link on its own or inside
     * shared text (followed to its pin), or coordinates inside text. Null when none of those.
     */
    suspend fun fromText(text: String, lookup: (suspend (String) -> GeoResult?)? = null): GeoResult? {
        coordinates(text)?.let { (lat, lng) -> return GeoResult(pointName(lat, lng), null, lat, lng) }
        findLink(text)?.let { link ->
            val result = resolve(link, lookup) ?: return null
            // A dropped pin has no name in the URL; the shared text usually does.
            val label = labelBefore(text)
            return if (label != null && result.name == pointName(result.lat, result.lng)) result.copy(name = label) else result
        }
        coordinatesIn(text)?.let { (lat, lng) -> return GeoResult(labelBefore(text) ?: pointName(lat, lng), null, lat, lng) }
        return null
    }

    /** True when [text] is meant as a location (coordinates or a Maps link somewhere in it), not a name. */
    fun looksLikeLocation(text: String): Boolean =
        coordinates(text) != null || findLink(text) != null || coordinatesIn(text) != null

    /** A Google Maps link worth resolving: maps.app.goo.gl, goo.gl/maps, google.TLD/maps or maps.google.TLD. */
    fun isLink(text: String): Boolean {
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        return host == "maps.app.goo.gl" ||
            (host == "goo.gl" && path.startsWith("/maps")) ||
            host.startsWith("maps.google.") ||
            (googleHost.matches(host) && path.startsWith("/maps"))
    }

    /** Reads a full Google Maps URL. The pin (!3d…!4d…) wins over the map's centre (@…). */
    fun parse(url: String): GeoResult? {
        val u = url.trim()
        if (u.contains("consent.google.")) {
            val inner = consentContinue.find(u)?.groupValues?.get(1) ?: return null
            return parse(URLDecoder.decode(inner, "UTF-8"))
        }
        val m = pin.find(u) ?: query.find(u) ?: viewport.find(u) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lng = m.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        val name = nameIn(u) ?: pointName(lat, lng)
        return GeoResult(name = name, address = null, lat = lat, lng = lng)
    }

    fun pointName(lat: Double, lng: Double): String = String.format(Locale.ROOT, "Punto %.5f, %.5f", lat, lng)

    /** The place's own id in a Maps URL (…!1s0xd604f3e4c0a9675:0x70ce4962f896a560…). */
    private val featureId = Regex("""!1s(0x[0-9a-f]+:0x[0-9a-f]+)""")
    private val detailsHref = Regex("""href="(/maps/preview/place\?[^"]+)"""")
    /** In the details, the pin comes right before the place's id: [null,null,lat,lng],"0x…:0x…". */
    private val pinBeforeId = Regex("""\[null,null,(-?\d+\.\d+),(-?\d+\.\d+)\],"(0x[0-9a-f]+:0x[0-9a-f]+)"""")
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) TripPlanner"

    /** The place name in a Maps URL, if it has one. */
    fun nameIn(url: String): String? = placeName.find(url)?.groupValues?.get(1)
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it).trim() }
        ?.takeIf { it.isNotBlank() && coordinates(it) == null }

    /**
     * Reads the pin out of a place's details response, preferring the entry with [id].
     * Null when the response does not have the expected shape.
     */
    fun pinInDetails(details: String, id: String?): Pair<Double, Double>? {
        val pins = pinBeforeId.findAll(details).toList()
        val m = pins.firstOrNull { it.groupValues[3] == id } ?: pins.firstOrNull() ?: return null
        val lat = m.groupValues[1].toDouble()
        val lng = m.groupValues[2].toDouble()
        return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
    }

    /**
     * Follows a short link's redirects until a URL with coordinates appears. Needs network.
     *
     * Links shared from the Maps app now lead to a place URL with a name and an id but no
     * coordinates (Chrome adds them later, with JavaScript). Then the page's own details
     * request gives the pin, and if that fails, [lookup] searches the name.
     * Returns null when the link leads nowhere useful.
     */
    suspend fun resolve(url: String, lookup: (suspend (String) -> GeoResult?)? = null): GeoResult? =
        withContext(Dispatchers.IO) {
            var current = url.trim()
            var page: String? = null
            for (hop in 0 until 8) {
                parse(current)?.let { return@withContext it }
                val conn = open(current)
                try {
                    val code = conn.responseCode
                    val location = conn.getHeaderField("Location")
                    if (location != null) {
                        current = URL(URL(current), location).toString()
                    } else {
                        if (code == HttpURLConnection.HTTP_OK) page = conn.inputStream.bufferedReader().use { it.readText() }
                        break
                    }
                } finally {
                    conn.disconnect()
                }
            }
            parse(current)?.let { return@withContext it }

            val name = nameIn(current)
            val pin = page?.let { runCatching { pinFromPage(it, featureId.find(current)?.groupValues?.get(1)) }.getOrNull() }
            if (pin != null) return@withContext GeoResult(name ?: pointName(pin.first, pin.second), null, pin.first, pin.second)
            if (name != null && lookup != null) lookup(name)?.let { return@withContext it.copy(name = name) }
            null
        }

    private fun pinFromPage(page: String, id: String?): Pair<Double, Double>? {
        val href = detailsHref.find(page)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        val conn = open("https://www.google.com$href")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            return pinInDetails(conn.inputStream.bufferedReader().use { it.readText() }, id)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 8_000
        readTimeout = 8_000
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept-Language", "es")
    }
}
