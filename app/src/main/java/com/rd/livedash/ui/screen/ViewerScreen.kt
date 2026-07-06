package com.rd.livedash.ui.screen

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rd.livedash.data.ScreenshotEntry
import com.rd.livedash.service.DashboardService
import com.rd.livedash.ui.theme.*
import com.rd.livedash.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val serverRunning by vm.serverRunning.collectAsState()
    val screenshots by vm.screenshots.collectAsState()
    val chatMessages by vm.chatMessages.collectAsState()
    val senders by vm.senders.collectAsState()
    val localIp = remember { vm.getLocalIp() }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var chatText by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(0) }  // 0=Screenshots 1=Chat 2=Devices

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(8.dp).background(
                                if (serverRunning) Emerald else TextMuted, CircleShape
                            )
                        )
                        Text("Dashboard", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (!serverRunning) {
                        Button(
                            onClick = {
                                ctx.startForegroundService(
                                    Intent(ctx, DashboardService::class.java).apply { putExtra(DashboardService.EXTRA_PORT, 8765) }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text("Start Server", style = MaterialTheme.typography.labelLarge) }
                    } else {
                        IconButton(onClick = {
                            ctx.startService(Intent(ctx, DashboardService::class.java).apply { action = DashboardService.ACTION_STOP })
                        }) { Icon(Icons.Default.Stop, null, tint = Rose) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceCard, tonalElevation = 0.dp) {
                listOf("Screenshots" to Icons.Default.Image, "Chat" to Icons.Default.Chat, "Devices" to Icons.Default.Devices)
                    .forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = activeTab == i, onClick = { activeTab = i },
                            icon = { Icon(icon, null) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Indigo, selectedTextColor = Indigo,
                                indicatorColor = IndigoContainer
                            )
                        )
                    }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // IP Banner
            if (serverRunning) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = IndigoContainer, shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.WifiTethering, null, tint = Indigo, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Server IP (share with senders)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("$localIp : 8765", style = MaterialTheme.typography.titleMedium, color = Indigo, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${senders.size} connected", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }

            when (activeTab) {
                0 -> ScreenshotsTab(screenshots, selectedIndex) { selectedIndex = it }
                1 -> ChatTab(chatMessages, chatText, { chatText = it }) {
                    if (it.isNotBlank()) { vm.sendViewerChat(it); chatText = "" }
                }
                2 -> DevicesTab(senders)
            }
        }
    }
}

@Composable
private fun ScreenshotsTab(
    screenshots: List<ScreenshotEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    if (screenshots.isEmpty()) {
        EmptyState(Icons.Default.Screenshot, "Waiting for screenshots", "Start the server and connect senders")
        return
    }
    Column(Modifier.fillMaxSize()) {
        // Main viewer
        val entry = screenshots.getOrNull(selectedIndex)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            if (entry != null) {
                val bmp = remember(entry.dataBase64) {
                    try {
                        val bytes = Base64.decode(entry.dataBase64, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                }
                if (bmp != null) {
                    Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                // Meta overlay
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(12.dp)
                ) {
                    Column {
                        Text(entry.senderName, style = MaterialTheme.typography.labelLarge, color = Color.White)
                        Text(formatTime(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                    }
                }
            }
        }

        // History strip
        Text("History", style = MaterialTheme.typography.labelSmall, color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(screenshots) { i, entry ->
                val bmp = remember(entry.dataBase64) {
                    try {
                        val bytes = Base64.decode(entry.dataBase64, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .border(if (i == selectedIndex) BorderStroke(2.dp, Indigo) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(10.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Image, null, tint = TextMuted, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatTab(
    messages: List<com.rd.livedash.data.ChatMessage>,
    input: String,
    onInput: (String) -> Unit,
    onSend: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                    Text("No messages yet", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }}
            }
            items(messages) { msg ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 14.dp, topEnd = 14.dp,
                            bottomStart = if (msg.outgoing) 14.dp else 4.dp,
                            bottomEnd = if (msg.outgoing) 4.dp else 14.dp
                        ),
                        color = if (msg.outgoing) Indigo else SurfaceElevated,
                        modifier = Modifier.widthIn(max = 260.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (!msg.outgoing && msg.senderName.isNotEmpty())
                                Text(msg.senderName, style = MaterialTheme.typography.labelSmall, color = Emerald)
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium,
                                color = if (msg.outgoing) Color.White else TextPrimary)
                            Text(formatTime(msg.timestamp), style = MaterialTheme.typography.labelSmall,
                                color = if (msg.outgoing) Color.White.copy(0.6f) else TextMuted,
                                modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
        }
        Surface(color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(12.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input, onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message senders…", color = TextMuted) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Indigo, unfocusedBorderColor = OutlineColor,
                        focusedContainerColor = SurfaceElevated, unfocusedContainerColor = SurfaceElevated,
                        cursorColor = Indigo, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 3, singleLine = false
                )
                IconButton(
                    onClick = { onSend(input) },
                    modifier = Modifier.size(48.dp).background(Indigo, CircleShape)
                ) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun DevicesTab(senders: List<com.rd.livedash.data.SenderInfo>) {
    if (senders.isEmpty()) {
        EmptyState(Icons.Default.PhoneAndroid, "No devices connected", "Start the server and launch the Sender app on another phone")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(senders) { sender ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(IndigoContainer, CircleShape), Alignment.Center) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = Indigo, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sender.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Connected ${formatTime(sender.connectedAt)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Box(Modifier.size(8.dp).background(Emerald, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(64.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

private fun formatTime(ts: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
private val CircleShape = androidx.compose.foundation.shape.CircleShape
