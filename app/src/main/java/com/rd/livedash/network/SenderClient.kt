package com.rd.livedash.network

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class SenderClient(
    serverIp: String,
    port: Int,
    private val senderName: String,
    var onConnected: (() -> Unit)? = null,
    var onDisconnected: (() -> Unit)? = null,
    var onChatReceived: ((String) -> Unit)? = null,
    var onError: ((String) -> Unit)? = null
) : WebSocketClient(URI("ws://$serverIp:$port/?role=sender&name=${senderName.encodeUrl()}")) {

    override fun onOpen(handshake: ServerHandshake) {
        Log.d("SenderClient", "Connected to $uri")
        onConnected?.invoke()
    }

    override fun onMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "chat" -> onChatReceived?.invoke(json.optString("text"))
            }
        } catch (e: Exception) {
            Log.e("SenderClient", "Parse error", e)
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        Log.d("SenderClient", "Disconnected: $reason")
        onDisconnected?.invoke()
    }

    override fun onError(ex: Exception) {
        Log.e("SenderClient", "Error", ex)
        onError?.invoke(ex.message ?: "Unknown error")
    }

    fun sendScreenshot(base64Jpeg: String, title: String = "", url: String = "") {
        if (!isOpen) return
        val json = JSONObject()
            .put("type", "screenshot")
            .put("data", base64Jpeg)
            .put("title", title)
            .put("url", url)
            .put("ts", System.currentTimeMillis())
        send(json.toString())
    }

    fun sendFrame(base64Jpeg: String) {
        if (!isOpen) return
        val json = JSONObject()
            .put("type", "frame")
            .put("data", base64Jpeg)
            .put("ts", System.currentTimeMillis())
        send(json.toString())
    }

    fun sendChat(text: String) {
        if (!isOpen) return
        val json = JSONObject().put("type", "chat").put("text", text)
            .put("ts", System.currentTimeMillis())
        send(json.toString())
    }
}

private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")
