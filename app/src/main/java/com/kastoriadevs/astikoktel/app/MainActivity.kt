package com.kastoriadevs.astikoktel.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kastoriadevs.astikoktel.app.data.ThemeMode
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.navigation.AstikaNavHost
import com.kastoriadevs.astikoktel.app.ui.splash.AnimatedSplash
import com.kastoriadevs.astikoktel.app.ui.theme.AstikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// AppCompatActivity (a ComponentActivity) so per-app language switching works on every Android version
class MainActivity : AppCompatActivity() {

    private val appVm: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent navigation bar, so the app's bottom bar shows through it.
        // The default puts a dark scrim behind 3-button navigation.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val container = (application as AstikaApp).container

        setContent {
            val mode by appVm.theme.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            // Parse the timetable JSON off the main thread while the splash plays
            val ready by produceState(initialValue = container.transit.isLoaded) {
                if (!value) {
                    withContext(Dispatchers.Default) { container.schedule }
                    value = true
                }
            }
            // Saved, so recreating the activity (e.g. a language change) doesn't replay the splash
            var splashDone by rememberSaveable { mutableStateOf(false) }

            // Navigation bar buttons follow the app theme, not the system one
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !dark
            }

            AstikaTheme(dark = dark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (ready) AstikaNavHost(appVm)
                    if (!splashDone) AnimatedSplash(ready = ready, onFinished = { splashDone = true })
                }
            }
        }
    }
}
