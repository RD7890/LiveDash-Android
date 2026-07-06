package com.rd.livedash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.rd.livedash.ui.screen.ModeSelectScreen
import com.rd.livedash.ui.screen.SenderScreen
import com.rd.livedash.ui.screen.ViewerScreen
import com.rd.livedash.ui.theme.LiveDashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveDashTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var mode by remember { mutableStateOf<String?>(null) }
                    when (mode) {
                        "viewer" -> ViewerScreen()
                        "sender" -> SenderScreen()
                        else -> ModeSelectScreen(
                            onViewerSelected = { mode = "viewer" },
                            onSenderSelected = { mode = "sender" }
                        )
                    }
                }
            }
        }
    }
}
