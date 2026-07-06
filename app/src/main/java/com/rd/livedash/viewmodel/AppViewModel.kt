package com.rd.livedash.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rd.livedash.data.ChatMessage
import com.rd.livedash.data.ScreenshotEntry
import com.rd.livedash.service.DashboardState
import com.rd.livedash.service.OverlayState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val serverRunning = DashboardState.serverRunning
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val screenshots = DashboardState.screenshots
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chatMessages = DashboardState.chatMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val senders = DashboardState.senders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val latestFrames = DashboardState.latestFrames
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val perSenderChat = DashboardState.perSenderChat
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val senderConnected = OverlayState.connected
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val senderChat = OverlayState.chatMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val capturing = OverlayState.capturing
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun getLocalIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (!iface.isLoopback && iface.isUp) {
                    iface.inetAddresses.toList().forEach { addr ->
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: ""
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "0.0.0.0"
    }

    fun sendViewerChat(text: String) {
        val msg = ChatMessage(UUID.randomUUID().toString(), text, System.currentTimeMillis(), outgoing = true, senderName = "You")
        DashboardState.addChat(msg)
        DashboardState.server?.sendChatToSenders(text)
    }

    fun sendViewerChatToSender(senderId: String, text: String) {
        val msg = ChatMessage(UUID.randomUUID().toString(), text, System.currentTimeMillis(), outgoing = true, senderName = "You", senderId = senderId)
        DashboardState.addPerSenderChat(senderId, msg)
        DashboardState.server?.sendChatToSender(senderId, text)
    }

    fun sendSenderChat(text: String) {
        val msg = ChatMessage(UUID.randomUUID().toString(), text, System.currentTimeMillis(), outgoing = true, senderName = "You")
        OverlayState.addChat(msg)
        OverlayState.client?.sendChat(text)
    }
}
