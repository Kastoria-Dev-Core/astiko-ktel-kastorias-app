package com.kastoriadevs.astikoktel.app

import android.app.Application
import com.kastoriadevs.astikoktel.app.data.LocationClient
import com.kastoriadevs.astikoktel.app.data.PrefsRepository
import com.kastoriadevs.astikoktel.app.data.TransitRepository
import com.kastoriadevs.astikoktel.app.domain.Schedule
import org.osmdroid.config.Configuration
import java.io.File

/** Manual dependency container, created once per process */
class AppContainer(app: Application) {
    val transit = TransitRepository(app.assets)
    val schedule by lazy { Schedule(transit.data) }
    val prefs = PrefsRepository(app)
    val location = LocationClient(app)
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
