package com.kastoriadevs.astikoktel.app.data

import android.content.res.AssetManager
import kotlinx.serialization.json.Json

/**
 * Loads the network from the JSON files in assets/.
 * Replace those files with the real data from astikoktelkastorias.gr; the format stays the same.
 */
class TransitRepository(
    /** Where stops.json, lines.json, timetables.json and meta.json are read from */
    private val assets: AssetManager,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val dataLazy = lazy {
        TransitData(
            stops = json.decodeFromString(read("stops.json")),
            lines = json.decodeFromString(read("lines.json")),
            timetables = json.decodeFromString(read("timetables.json")),
            meta = json.decodeFromString(read("meta.json")),
        )
    }

    /** The whole network, read from assets the first time it's asked for */
    val data: TransitData by dataLazy

    /** True once the JSON has been parsed (e.g. after the activity is recreated) */
    val isLoaded: Boolean get() = dataLazy.isInitialized()

    private fun read(name: String): String =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
