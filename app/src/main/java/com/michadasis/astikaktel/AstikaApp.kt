package com.michadasis.astikaktel

import android.app.Application
import com.michadasis.astikaktel.data.AnnouncementsRepository
import com.michadasis.astikaktel.data.LocationClient
import com.michadasis.astikaktel.data.PrefsRepository
import com.michadasis.astikaktel.data.TransitRepository
import com.michadasis.astikaktel.domain.Schedule
import org.osmdroid.config.Configuration
import java.io.File

/** Manual dependency container, created once per process */
class AppContainer(app: Application) {
    val transit = TransitRepository(app.assets)
    val schedule by lazy { Schedule(transit.data) }
    val prefs = PrefsRepository(app)
    val location = LocationClient(app)
    val announcements = AnnouncementsRepository(
        feedUrl = BuildConfig.ANNOUNCEMENTS_URL,
        cacheFile = File(app.filesDir, "announcements.json"),
        userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}",
    )
}

class AstikaApp : Application() {
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
        }
    }
}
