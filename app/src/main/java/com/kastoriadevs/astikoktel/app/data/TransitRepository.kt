package com.kastoriadevs.astikoktel.app.data

import android.content.res.AssetManager
import kotlinx.serialization.json.Json

/**
 * Loads the network from the JSON files in assets/.
 * Replace those files with the real data from astikaktelkastorias.gr; the format stays the same.
 */
class TransitRepository(private val assets: AssetManager) {

    private val json = Json { ignoreUnknownKeys = true }

    private val dataLazy = lazy {
        TransitData(
            stops = json.decodeFromString(read("stops.json")),
            lines = json.decodeFromString(read("lines.json")),
            timetables = json.decodeFromString(read("timetables.json")),
            meta = json.decodeFromString(read("meta.json")),
        )
    }

    val data: TransitData by dataLazy

    /** True once the JSON has been parsed (e.g. after the activity is recreated) */
    val isLoaded: Boolean get() = dataLazy.isInitialized()

    private fun read(name: String): String =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
