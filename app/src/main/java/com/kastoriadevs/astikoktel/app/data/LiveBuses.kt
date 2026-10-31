package com.kastoriadevs.astikoktel.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A bus that sent where it is, from the phone kept in it (astiko-ktel-odigoi) */
@Serializable
data class LiveBus(
    /** The bus, e.g. "12" */
    val vehicle: String,
    /** [Line.id] */
    val line: String,
    /** [Direction.id] */
    val direction: String,
    /** Latitude, WGS84 */
    val lat: Double,
    /** Longitude, WGS84 */
    val lng: Double,
    /** Direction of travel, degrees clockwise from north, when the phone knew it */
    val bearing: Double? = null,
    /** Metres per second, when the phone knew it */
    val speed: Double? = null,
    /** When the phone got the position, ISO instant */
    val t: String,
)

/** What the server answers */
@Serializable
data class LiveFeed(
    /** When the server made the answer, ISO instant */
    val updated: String,
    val buses: List<LiveBus>,
)

/**
 * Where the buses really are, from the drivers' phones through the astiko-ktel-live server. Asks at
 * most every [MIN_INTERVAL_MS]; with no signal the last answer is kept for [KEEP_MS], then nothing,
 * so the map goes back to the timetable's estimates instead of showing buses where they were.
 */
class LiveBusesRepository(private val url: String, private val userAgent: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var last: List<LiveBus> = emptyList()
    private var lastAskedAt = 0L
    private var lastGoodAt = 0L

    /** The buses on the road now; empty when none are sending or the server can't be reached */
    suspend fun buses(): List<LiveBus> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now - lastAskedAt >= MIN_INTERVAL_MS) {
            lastAskedAt = now
            try {
                last = withContext(Dispatchers.IO) { json.decodeFromString<LiveFeed>(get()).buses }
                lastGoodAt = now
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Kept below; dropped once too old
            }
        }
        if (now - lastGoodAt > KEEP_MS) emptyList() else last
    }

    private fun get(): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /** The server's answer is cached for 10 seconds anyway */
        const val MIN_INTERVAL_MS = 10_000L
        /** With no answer for this long, the buses are no longer shown as live */
        const val KEEP_MS = 60_000L
    }
}
