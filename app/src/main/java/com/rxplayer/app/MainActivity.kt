package com.rxplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.rxplayer.app.data.settings.SettingsManager
import com.rxplayer.app.navigation.RXPlayerNavHost
import com.rxplayer.app.ui.theme.RXPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsManager.themeMode.collectAsState()
            RXPlayerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RXPlayerNavHost()
                }
            }
        }
    }
}
