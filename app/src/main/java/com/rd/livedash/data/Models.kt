package com.rd.livedash.data

data class ScreenshotEntry(
    val id: String,
    val dataBase64: String,
    val title: String,
    val url: String,
    val timestamp: Long,
    val senderId: String,
    val senderName: String
)

data class ChatMessage(
    val id: String,
    val text: String,
    val timestamp: Long,
    val outgoing: Boolean,
    val senderName: String = "",
    val senderId: String = ""
)

data class SenderInfo(
    val id: String,
    val name: String,
    val connectedAt: Long
)
