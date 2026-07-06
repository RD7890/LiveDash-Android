package com.rd.livedash.ui.screen

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
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
import com.rd.livedash.data.SenderInfo
import com.rd.livedash.data.ScreenshotEntry
import com.rd.livedash.service.DashboardService
import com.rd.livedash.ui.theme.*
import com.rd.livedash.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: AppViewModel = viewModel(), onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val serverRunning by vm.serverRunning.collectAsState()
    val screenshots by vm.screenshots.collectAsState()
    val chatMessages by vm.chatMessages.collectAsState()
    val senders by vm.senders.collectAsState()
    val latestFrames by vm.latestFrames.collectAsState()
    val perSenderChat by vm.perSenderChat.collectAsState()
    val localIp = remember { vm.getLocalIp() }

    var chatText by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(0) }
    var selectedSender by remember { mutableStateOf<SenderInfo?>(null) }

    // Back from sender detail → main tabs
    BackHandler(enabled = selectedSender != null) { selectedSender = null }
    // Back from viewer → mode select
    BackHandler(enabled = selectedSender == null) { onBack() }

    if (selectedSender != null) {
        val sender = selectedSender!!
        SenderDetailView(
            sender = sender,
            frame = latestFrames[sender.id],
            messages = perSenderChat[sender.id] ?: emptyList(),
            onSendChat = { text -> vm.sendViewerChatToSender(sender.id, text) },
            onBack = { selectedSender = null }
        )
        return
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(if (serverRunning) Emerald else TextMuted, CircleShape))
                        Text("Dashboard", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
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
                listOf("Live" to Icons.Default.Image, "Chat" to Icons.Default.Chat, "Devices" to Icons.Default.Devices)
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
                0 -> LiveFramesTab(senders, latestFrames) { selectedSender = it }
                1 -> ChatTab(chatMessages, chatText, { chatText = it }) {
                    if (it.isNotBlank()) { vm.sendViewerChat(it); chatText = "" }
                }
                2 -> DevicesTab(senders) { selectedSender = it }
            }
        }
    }
}

@Composable
private fun LiveFramesTab(
    senders: List<SenderInfo>,
    latestFrames: Map<String, ScreenshotEntry>,
    onSelectSender: (SenderInfo) -> Unit
) {
    if (senders.isEmpty()) {
        EmptyState(Icons.Default.Screenshot, "Waiting for live feeds", "Start the server and connect senders")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(senders) { sender ->
            val frame = latestFrames[sender.id]
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                modifier = Modifier.fillMaxWidth().clickable { onSelectSender(sender) }
            ) {
                Column {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (frame != null) {
                            val bmp = remember(frame.dataBase64) {
                                try {
                                    val bytes = Base64.decode(frame.dataBase64, Base64.NO_WRAP)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                } catch (_: Exception) { null }
                            }
                            if (bmp != null) {
                                Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            } else {
                                Icon(Icons.Default.Image, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.HourglassEmpty, null, tint = TextMuted, modifier = Modifier.size(32.dp))
                                Text("Waiting for feed…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(8.dp).background(Emerald, CircleShape))
                        Text(sender.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        if (frame != null) Text(formatTime(frame.timestamp), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
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
private fun DevicesTab(senders: List<SenderInfo>, onSelect: (SenderInfo) -> Unit) {
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
            Surface(
                shape = RoundedCornerShape(14.dp), color = SurfaceCard,
                modifier = Modifier.fillMaxWidth().clickable { onSelect(sender) }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(IndigoContainer, CircleShape), Alignment.Center) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = Indigo, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sender.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Connected ${formatTime(sender.connectedAt)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun SenderDetailView(
    sender: SenderInfo,
    frame: ScreenshotEntry?,
    messages: List<com.rd.livedash.data.ChatMessage>,
    onSendChat: (String) -> Unit,
    onBack: () -> Unit
) {
    var chatText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize().background(Background)) {
        // Header
        Surface(color = Background) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
                Box(Modifier.size(8.dp).background(Emerald, CircleShape))
                Text(sender.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            }
        }

        // Live frame
        Box(
            Modifier.fillMaxWidth().weight(0.48f).padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp)).background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            if (frame != null) {
                val bmp = remember(frame.dataBase64) {
                    try {
                        val bytes = Base64.decode(frame.dataBase64, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                }
                if (bmp != null) {
                    Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.6f)) {
                            Text("● LIVE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.HourglassEmpty, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                        Text("Decoding…", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ScreenShare, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Text("Waiting for live feed", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        }

        // Per-sender chat
        Text("Chat with ${sender.name}", style = MaterialTheme.typography.labelMedium, color = TextMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

        LazyColumn(
            state = listState, modifier = Modifier.weight(0.52f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                    Text("No messages yet — start the conversation", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
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
                    value = chatText, onValueChange = { chatText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message ${sender.name}…", color = TextMuted) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Indigo, unfocusedBorderColor = OutlineColor,
                        focusedContainerColor = SurfaceElevated, unfocusedContainerColor = SurfaceElevated,
                        cursorColor = Indigo, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 3, singleLine = false
                )
                IconButton(
                    onClick = { if (chatText.isNotBlank()) { onSendChat(chatText); chatText = "" } },
                    modifier = Modifier.size(48.dp).background(Indigo, CircleShape)
                ) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
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
