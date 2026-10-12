package com.michadasis.astikaktel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One post from astikoktelkastorias.gr, as served by the astiko-ktel-announcements feed */
@Serializable
data class Announcement(
    val id: Long,
    val title: String,
    /** Often empty: most announcements are a poster image with no text */
    val text: String = "",
    /** "service" or "corporate" (balance sheets, shareholder meetings) */
    val kind: String = "service",
    /** ISO instant */
    val published: String,
    val url: String,
    val image: String? = null,
    val attachments: List<String> = emptyList(),
    /** "YYYY-MM-DD" dates mentioned in the title/text */
    val dates: List<String> = emptyList(),
    /** Last of [dates], null when the announcement mentions none */
    val validUntil: String? = null,
) {
    val publishedAt: Instant get() = Instant.parse(published)

    /**
     * Still relevant: its last mentioned date hasn't passed, or, when it mentions
     * no date, it was published within the last [UNDATED_DAYS] days.
     */
    fun isActive(today: LocalDate, now: Instant): Boolean =
        validUntil?.let { !LocalDate.parse(it).isBefore(today) }
            ?: publishedAt.isAfter(now.minus(Duration.ofDays(UNDATED_DAYS)))

    companion object {
        const val UNDATED_DAYS = 7L
    }
}

@Serializable
data class AnnouncementFeed(
    val fetchedAt: String,
    val announcements: List<Announcement>,
)

/**
 * Downloads the announcements feed and keeps the last good copy on disk, so the
 * list still shows offline and the first frame doesn't wait for the network.
 */
class AnnouncementsRepository(
    private val feedUrl: String,
    private val cacheFile: File,
    private val userAgent: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastFetch: Instant = Instant.EPOCH

    private val _announcements = MutableStateFlow<List<Announcement>?>(null)

    /** null until the cache or the network has answered */
    val announcements: StateFlow<List<Announcement>?> = _announcements.asStateFlow()

    private val _failed = MutableStateFlow(false)

    /** The last refresh couldn't reach the feed */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Fetches the feed unless it was fetched in the last [MIN_INTERVAL], or [force] */
    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_announcements.value == null) readCache()
            if (!force && Duration.between(lastFetch, Instant.now()) < MIN_INTERVAL) return@withLock

            try {
                val body = download()
                val feed = json.decodeFromString<AnnouncementFeed>(body)
                _announcements.value = feed.announcements
                cacheFile.writeText(body)
                lastFetch = Instant.now()
                _failed.value = false
            } catch (e: IOException) {
                _failed.value = true
            } catch (e: SerializationException) {
                _failed.value = true
            }
            // Show an empty list rather than loading forever when there's no cache either
            if (_announcements.value == null) _announcements.value = emptyList()
        }
    }

    private fun readCache() {
        if (!cacheFile.exists()) return
        try {
            _announcements.value = json.decodeFromString<AnnouncementFeed>(cacheFile.readText()).announcements
        } catch (e: Exception) {
            cacheFile.delete()
        }
    }

    private fun download(): String {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val MIN_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}
