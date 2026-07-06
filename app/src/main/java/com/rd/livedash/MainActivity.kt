package com.rd.livedash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.rd.livedash.service.DashboardService
import com.rd.livedash.service.OverlayService
import com.rd.livedash.ui.screen.ModeSelectScreen
import com.rd.livedash.ui.screen.SenderScreen
import com.rd.livedash.ui.screen.ViewerScreen
import com.rd.livedash.ui.theme.LiveDashTheme
import com.rd.livedash.ui.theme.Rose
import com.rd.livedash.ui.theme.SurfaceCard
import com.rd.livedash.ui.theme.TextMuted
import com.rd.livedash.ui.theme.TextPrimary
import com.rd.livedash.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveDashTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var mode by remember { mutableStateOf<String?>(null) }
                    var showExitDialog by remember { mutableStateOf(false) }

                    // Show exit dialog when back is pressed on mode select screen
                    BackHandler(enabled = mode == null) { showExitDialog = true }

                    if (showExitDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitDialog = false },
                            containerColor = SurfaceCard,
                            title = { Text("Exit LiveDash?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                            text = { Text("All services will be stopped and the app will close completely.", color = TextSecondary) },
                            confirmButton = {
                                TextButton(onClick = {
                                    stopService(Intent(this@MainActivity, DashboardService::class.java))
                                    stopService(Intent(this@MainActivity, OverlayService::class.java))
                                    finishAndRemoveTask()
                                }) { Text("Exit", color = Rose) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExitDialog = false }) {
                                    Text("Cancel", color = TextMuted)
                                }
                            }
                        )
                    }

                    when (mode) {
                        "viewer" -> ViewerScreen(onBack = { mode = null })
                        "sender" -> SenderScreen(onBack = { mode = null })
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
