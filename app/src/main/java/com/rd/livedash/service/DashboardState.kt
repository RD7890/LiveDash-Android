package com.rd.livedash.service

import com.rd.livedash.data.ChatMessage
import com.rd.livedash.data.ScreenshotEntry
import com.rd.livedash.data.SenderInfo
import com.rd.livedash.network.DashboardServer
import kotlinx.coroutines.flow.MutableStateFlow

object DashboardState {
    val serverRunning = MutableStateFlow(false)
    val screenshots = MutableStateFlow<List<ScreenshotEntry>>(emptyList())
    val chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val senders = MutableStateFlow<List<SenderInfo>>(emptyList())
    var server: DashboardServer? = null

    fun addScreenshot(entry: ScreenshotEntry) {
        val current = screenshots.value.toMutableList()
        current.add(0, entry)
        if (current.size > 40) current.removeAt(current.size - 1)
        screenshots.value = current
    }

    fun addChat(msg: ChatMessage) {
        val current = chatMessages.value.toMutableList()
        current.add(msg)
        if (current.size > 200) current.removeAt(0)
        chatMessages.value = current
    }

    fun reset() {
        serverRunning.value = false
        screenshots.value = emptyList()
        chatMessages.value = emptyList()
        senders.value = emptyList()
        server = null
    }
}
