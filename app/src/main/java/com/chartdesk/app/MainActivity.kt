package com.chartdesk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.data.settings.AppSettings
import com.chartdesk.app.data.settings.SettingsRepository
import com.chartdesk.app.ui.nav.ChartDeskRoot
import com.chartdesk.app.ui.theme.ChartDeskTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                "light" -> false
                "system" -> systemDark
                else -> true
            }
            ChartDeskTheme(darkTheme = dark, colorBlind = settings.colorBlind) {
                ChartDeskRoot()
            }
        }
    }
}
