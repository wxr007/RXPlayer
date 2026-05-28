package com.rxplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rxplayer.app.navigation.RXPlayerNavHost
import com.rxplayer.app.ui.theme.RXPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RXPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RXPlayerNavHost()
                }
            }
        }
    }
}
