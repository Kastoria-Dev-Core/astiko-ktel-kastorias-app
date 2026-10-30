package com.kastoriadevs.astikoktel.app

import android.app.Application
import com.kastoriadevs.astikoktel.app.data.AnnouncementsRepository
import com.kastoriadevs.astikoktel.app.data.LocationClient
import com.kastoriadevs.astikoktel.app.data.PrefsRepository
import com.kastoriadevs.astikoktel.app.data.TransitRepository
import com.kastoriadevs.astikoktel.app.domain.BusEstimator
import com.kastoriadevs.astikoktel.app.domain.Schedule
import org.osmdroid.config.Configuration
import java.io.File

/** Manual dependency container, created once per process */
class AppContainer(app: Application) {
    /** Lines, stops and timetables from assets */
    val transit = TransitRepository(app.assets)

    /** Made on first use, since it needs [transit] to have read the data */
    val schedule by lazy { Schedule(transit.data) }

    /** Where the buses are along their routes, estimated from [schedule] */
    val busEstimator by lazy { BusEstimator(transit.data, schedule) }

    /** Favourites, theme and voice */
    val prefs = PrefsRepository(app)

    /** The phone's position, for stops nearby */
    val location = LocationClient(app)

    /** The operator's announcements, from the feed with a copy on disk */
    val announcements = AnnouncementsRepository(
        feedUrl = BuildConfig.ANNOUNCEMENTS_URL,
        cacheFile = File(app.filesDir, "announcements.json"),
        userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}",
    )
}

/** The app's Application: makes the [AppContainer] and sets up osmdroid's tile cache */
class AstikaApp : Application() {
    /** Everything the screens need, reached through `(application as AstikaApp).container` */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Configuration.getInstance().apply {
            load(this@AstikaApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            // OSM tile servers require an identifying User-Agent and a local tile cache
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            // A few more tiles in memory so panning back doesn't reload them, and tiles on disk kept
            // a week past their expiry: the streets of Kastoria rarely change, and it saves data
            cacheMapTileCount = 12.toShort()
            expirationExtendedDuration = 7L * 24 * 60 * 60 * 1000
        }
    }
}
