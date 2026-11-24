package com.chartdesk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chartdesk.app.ui.nav.ChartDeskRoot
import com.chartdesk.app.ui.theme.ChartDeskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme preference is wired to DataStore in feature 4 (settings).
            val darkTheme = true
            val colorBlind = false
            ChartDeskTheme(darkTheme = darkTheme, colorBlind = colorBlind) {
                ChartDeskRoot()
            }
        }
    }
}
