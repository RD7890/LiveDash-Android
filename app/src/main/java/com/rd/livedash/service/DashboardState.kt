package com.rd.livedash.service

import com.rd.livedash.data.ChatMessage
import com.rd.livedash.data.ScreenshotEntry
import com.rd.livedash.data.SenderInfo
import com.rd.livedash.network.DashboardServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

data class VideoChunk(val data: ByteArray, val flags: Int, val ts: Long) {
    override fun equals(other: Any?) = false
    override fun hashCode() = System.identityHashCode(this)
}

object DashboardState {
    val serverRunning = MutableStateFlow(false)
    val screenshots = MutableStateFlow<List<ScreenshotEntry>>(emptyList())
    val chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val senders = MutableStateFlow<List<SenderInfo>>(emptyList())
    val latestFrames = MutableStateFlow<Map<String, ScreenshotEntry>>(emptyMap())
    val perSenderChat = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    var server: DashboardServer? = null

    private val videoStreams = ConcurrentHashMap<String, MutableSharedFlow<VideoChunk>>()

    fun getOrCreateVideoStream(senderId: String): SharedFlow<VideoChunk> =
        videoStreams.getOrPut(senderId) { MutableSharedFlow(replay = 30, extraBufferCapacity = 64) }

    fun emitVideoFrame(senderId: String, data: ByteArray, flags: Int) {
        videoStreams[senderId]?.tryEmit(VideoChunk(data, flags, System.currentTimeMillis()))
    }

    fun addScreenshot(entry: ScreenshotEntry) {
        val current = screenshots.value.toMutableList()
        current.add(0, entry)
        if (current.size > 40) current.removeAt(current.size - 1)
        screenshots.value = current
    }

    fun addFrame(entry: ScreenshotEntry) {
        val current = latestFrames.value.toMutableMap()
        current[entry.senderId] = entry
        latestFrames.value = current
    }

    fun addChat(msg: ChatMessage) {
        val current = chatMessages.value.toMutableList()
        current.add(msg)
        if (current.size > 200) current.removeAt(0)
        chatMessages.value = current
    }

    fun addPerSenderChat(senderId: String, msg: ChatMessage) {
        val current = perSenderChat.value.toMutableMap()
        val list = (current[senderId] ?: emptyList()).toMutableList()
        list.add(msg)
        if (list.size > 100) list.removeAt(0)
        current[senderId] = list
        perSenderChat.value = current
    }

    fun reset() {
        serverRunning.value = false
        screenshots.value = emptyList()
        chatMessages.value = emptyList()
        senders.value = emptyList()
        latestFrames.value = emptyMap()
        perSenderChat.value = emptyMap()
        videoStreams.clear()
        server = null
    }
}
