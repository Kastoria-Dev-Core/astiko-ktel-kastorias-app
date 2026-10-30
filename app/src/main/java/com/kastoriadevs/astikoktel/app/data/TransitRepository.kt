package com.kastoriadevs.astikoktel.app.data

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The whole network in one file, as published next to the GTFS feed by scripts/bundle_transit.py.
 * The app downloads it so a timetable change reaches users without a new release.
 */
@Serializable
data class TransitBundle(
    /** Format version: a bundle with another one is ignored, so an old app never reads a format it doesn't know */
    val schema: Int,
    /** stops.json */
    val stops: List<Stop>,
    /** lines.json */
    val lines: List<Line>,
    /** timetables.json */
    val timetables: List<Timetable>,
    /** meta.json; [Meta.updated] decides whether this copy is newer than the app's own */
    val meta: Meta,
) {
    /** Complete enough to run the app on: nothing empty and nothing pointing at a stop or line that isn't there */
    fun isUsable(): Boolean {
        if (stops.isEmpty() || lines.isEmpty() || timetables.isEmpty()) return false
        val stopIds = stops.map { it.id }.toSet()
        val lineIds = lines.map { it.id }.toSet()
        return lines.all { line -> line.directions.all { d -> d.stops.all { it.stop in stopIds } } } &&
            timetables.all { it.line in lineIds }
    }

    /** The same data in the form the rest of the app uses */
    fun toData() = TransitData(stops, lines, timetables, meta)

    companion object {
        /** The bundle format this app reads */
        const val SCHEMA = 1
    }
}

/**
 * Loads the network: the copy downloaded from [dataUrl] when it's newer than the one in assets/,
 * otherwise the JSON files in assets/.
 *
 * [refresh] downloads a new copy when the published one has changed. It's used from the next start
 * of the app, so the screens never see the data change under them.
 */
class TransitRepository(
    /** Where stops.json, lines.json, timetables.json and meta.json are read from */
    private val assets: AssetManager,
    /** Where the last good downloaded bundle is kept */
    private val cacheFile: File,
    /** The published bundle, BuildConfig.TRANSIT_DATA_URL */
    private val dataUrl: String,
    /** Sent as the User-Agent header: the app's id and version */
    private val userAgent: String,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val etagFile = File(cacheFile.path + ".etag")

    private val dataLazy = lazy {
        val bundledMeta = json.decodeFromString<Meta>(read("meta.json"))
        // A downloaded copy older than the app's own (e.g. after an app update) is left unused
        readCache()?.takeIf { it.meta.updated > bundledMeta.updated }?.toData()
            ?: TransitData(
                stops = json.decodeFromString(read("stops.json")),
                lines = json.decodeFromString(read("lines.json")),
                timetables = json.decodeFromString(read("timetables.json")),
                meta = bundledMeta,
            )
    }

    /** The whole network, read the first time it's asked for */
    val data: TransitData by dataLazy

    /** True once the JSON has been parsed (e.g. after the activity is recreated) */
    val isLoaded: Boolean get() = dataLazy.isInitialized()

    /**
     * Downloads the published bundle if it changed since the last download, and keeps it for the
     * next start. Fails quietly: without a network or with a bad file, the current data stays.
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val connection = URL(dataUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", userAgent)
                if (cacheFile.exists() && etagFile.exists()) {
                    connection.setRequestProperty("If-None-Match", etagFile.readText())
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val bundle = json.decodeFromString<TransitBundle>(body)
                if (bundle.schema != TransitBundle.SCHEMA || !bundle.isUsable()) return@withContext
                // Written aside and moved into place, so a crash halfway never leaves half a file
                val tmp = File(cacheFile.path + ".tmp")
                tmp.writeText(body)
                if (!tmp.renameTo(cacheFile)) {
                    tmp.delete()
                    return@withContext
                }
                connection.getHeaderField("ETag")?.let { etagFile.writeText(it) } ?: etagFile.delete()
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            // Offline or the server is down: try again next start
        } catch (e: SerializationException) {
            // A file this version can't read, e.g. a service day it doesn't know: keep the current data
        } catch (e: IllegalArgumentException) {
            // Same, for values the JSON decoder rejects
        }
    }

    private fun readCache(): TransitBundle? {
        if (!cacheFile.exists()) return null
        val bundle = try {
            json.decodeFromString<TransitBundle>(cacheFile.readText())
        } catch (e: Exception) {
            null
        }
        if (bundle != null && bundle.schema == TransitBundle.SCHEMA && bundle.isUsable()) return bundle
        // Unreadable or from an older format: drop it, the next refresh downloads a good one
        cacheFile.delete()
        etagFile.delete()
        return null
    }

    private fun read(name: String): String =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
