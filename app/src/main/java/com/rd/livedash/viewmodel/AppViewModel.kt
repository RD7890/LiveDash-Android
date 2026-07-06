package com.rd.livedash.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rd.livedash.service.DashboardState
import com.rd.livedash.service.OverlayState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.net.Inet4Address
import java.net.NetworkInterface

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val serverRunning = DashboardState.serverRunning
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val screenshots = DashboardState.screenshots
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chatMessages = DashboardState.chatMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val senders = DashboardState.senders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        DashboardState.server?.sendChatToSenders(text)
    }

    fun sendSenderChat(text: String) {
        OverlayState.client?.sendChat(text)
    }
}
