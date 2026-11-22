package com.daniel.nextasks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.daniel.nextasks.data.AppSettings
import com.daniel.nextasks.data.SettingsRepository
import com.daniel.nextasks.ui.AppIcon
import com.daniel.nextasks.ui.NexTasksApp
import com.daniel.nextasks.ui.theme.NexTasksTheme

class MainActivity : ComponentActivity() {
    // Uma instância só: o DataStore não aceita duas aberturas do mesmo arquivo.
    private val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())

            // O ícone da gaveta (e o da splash) segue a cor escolhida.
            LaunchedEffect(settings.accent) {
                AppIcon.sync(applicationContext, settings.accent)
            }

            NexTasksTheme(themeMode = settings.themeMode, accent = settings.accent) {
                NexTasksApp(settingsRepository = settingsRepository)
            }
        }
    }
}
