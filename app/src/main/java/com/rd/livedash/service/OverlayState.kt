package com.rd.livedash.service

import com.rd.livedash.data.ChatMessage
import com.rd.livedash.network.SenderClient
import kotlinx.coroutines.flow.MutableStateFlow

object OverlayState {
    val connected = MutableStateFlow(false)
    val capturing = MutableStateFlow(false)
    val chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    var client: SenderClient? = null

    fun addChat(msg: ChatMessage) {
        val current = chatMessages.value.toMutableList()
        current.add(msg)
        if (current.size > 100) current.removeAt(0)
        chatMessages.value = current
    }

    fun reset() {
        connected.value = false
        capturing.value = false
        chatMessages.value = emptyList()
        client = null
    }
}
