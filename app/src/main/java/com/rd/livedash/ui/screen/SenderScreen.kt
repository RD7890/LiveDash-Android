package com.rd.livedash.ui.screen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rd.livedash.service.OverlayService
import com.rd.livedash.ui.theme.*
import com.rd.livedash.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val connected by vm.senderConnected.collectAsState()
    val chatMessages by vm.senderChat.collectAsState()

    var serverIp by remember { mutableStateOf("192.168.43.1") }
    var port by remember { mutableStateOf("8765") }
    var senderName by remember { mutableStateOf("Phone ${Build.MODEL}") }
    var chatInput by remember { mutableStateOf("") }
    var overlayPermGranted by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var showPermSheet by remember { mutableStateOf(false) }

    val projectionManager = remember { ctx.getSystemService(MediaProjectionManager::class.java) }

    val captureResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ctx.startForegroundService(
                Intent(ctx, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                    putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(OverlayService.EXTRA_DATA, result.data)
                    putExtra(OverlayService.EXTRA_SERVER_IP, serverIp)
                    putExtra(OverlayService.EXTRA_SERVER_PORT, port.toIntOrNull() ?: 8765)
                    putExtra(OverlayService.EXTRA_SENDER_NAME, senderName)
                }
            )
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Sender Setup", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Status card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (connected) Emerald.copy(alpha = 0.12f) else SurfaceCard,
                border = BorderStroke(1.dp, if (connected) Emerald.copy(0.4f) else OutlineColor)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(10.dp).background(if (connected) Emerald else TextMuted, CircleShape))
                    Text(
                        if (connected) "Connected to Dashboard" else "Not connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (connected) Emerald else TextSecondary
                    )
                }
            }

            // Config card
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, OutlineColor)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)

                    LabeledField("Viewer IP (Hotspot IP)") {
                        OutlinedTextField(
                            value = serverIp, onValueChange = { serverIp = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("192.168.43.1", color = TextMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(12.dp),
                            colors = ldTextFieldColors()
                        )
                    }
                    LabeledField("Port") {
                        OutlinedTextField(
                            value = port, onValueChange = { port = it },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = ldTextFieldColors()
                        )
                    }
                    LabeledField("Your Name") {
                        OutlinedTextField(
                            value = senderName, onValueChange = { senderName = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ldTextFieldColors()
                        )
                    }
                }
            }

            // Permissions card
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, OutlineColor)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    PermissionRow(
                        label = "Draw Over Other Apps",
                        granted = overlayPermGranted,
                        onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        }
                    )
                }
            }

            // Start button
            Button(
                onClick = {
                    overlayPermGranted = Settings.canDrawOverlays(ctx)
                    if (!overlayPermGranted) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        return@Button
                    }
                    captureResultLauncher.launch(projectionManager.createScreenCaptureIntent())
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) {
                Icon(Icons.Default.Launch, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Overlay & Connect", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            // Stop button
            if (connected) {
                OutlinedButton(
                    onClick = {
                        ctx.startService(Intent(ctx, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Rose.copy(0.4f))
                ) {
                    Icon(Icons.Default.Stop, null, tint = Rose, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Overlay", color = Rose)
                }
            }

            // Chat section
            AnimatedVisibility(visible = connected) {
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, OutlineColor)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Chat", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            chatMessages.takeLast(30).forEach { msg ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (msg.outgoing) IndigoContainer else SurfaceElevated
                                ) {
                                    Text(
                                        "${if (msg.outgoing) "You" else "Dashboard"}: ${msg.text}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (msg.outgoing) Indigo else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = chatInput, onValueChange = { chatInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Reply…", color = TextMuted) },
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                colors = ldTextFieldColors()
                            )
                            IconButton(
                                onClick = { if (chatInput.isNotBlank()) { vm.sendSenderChat(chatInput); chatInput = "" } },
                                modifier = Modifier.size(48.dp).background(Indigo, CircleShape)
                            ) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        content()
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null, tint = if (granted) Emerald else TextMuted, modifier = Modifier.size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        if (!granted) TextButton(onClick = onClick) { Text("Grant", color = Indigo) }
    }
}

@Composable
private fun ldTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Indigo, unfocusedBorderColor = OutlineColor,
    focusedContainerColor = SurfaceElevated, unfocusedContainerColor = SurfaceElevated,
    cursorColor = Indigo, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
)

private val CircleShape = androidx.compose.foundation.shape.CircleShape
