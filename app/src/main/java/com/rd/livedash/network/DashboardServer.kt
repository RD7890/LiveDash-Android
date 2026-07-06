package com.rd.livedash.network

import android.util.Log
import com.rd.livedash.data.ChatMessage
import com.rd.livedash.data.ScreenshotEntry
import com.rd.livedash.data.SenderInfo
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DashboardServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private val viewers = ConcurrentHashMap<WebSocket, String>()   // conn -> viewerId
    private val senders = ConcurrentHashMap<WebSocket, SenderInfo>()

    var onScreenshot: ((ScreenshotEntry) -> Unit)? = null
    var onChat: ((ChatMessage) -> Unit)? = null
    var onSendersChanged: ((List<SenderInfo>) -> Unit)? = null

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val uri = handshake.resourceDescriptor ?: ""
        val role = parseParam(uri, "role") ?: "viewer"
        val name = parseParam(uri, "name") ?: "Unknown"
        if (role == "sender") {
            val info = SenderInfo(UUID.randomUUID().toString(), name, System.currentTimeMillis())
            senders[conn] = info
            broadcastSenderList()
            // send ack
            conn.send(JSONObject().put("type", "ack").put("id", info.id).toString())
        } else {
            viewers[conn] = UUID.randomUUID().toString()
            // send current sender list
            conn.send(buildSendersJson())
        }
        Log.d("DashboardServer", "Connected: role=$role name=$name")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        viewers.remove(conn)
        if (senders.remove(conn) != null) broadcastSenderList()
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "screenshot" -> {
                    val info = senders[conn] ?: return
                    val entry = ScreenshotEntry(
                        id = UUID.randomUUID().toString(),
                        dataBase64 = json.optString("data"),
                        title = json.optString("title"),
                        url = json.optString("url"),
                        timestamp = json.optLong("ts", System.currentTimeMillis()),
                        senderId = info.id,
                        senderName = info.name
                    )
                    onScreenshot?.invoke(entry)
                    broadcastToViewers(message)
                }
                "chat" -> {
                    val text = json.optString("text")
                    val isSender = senders.containsKey(conn)
                    val senderInfo = senders[conn]
                    val msg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = text,
                        timestamp = System.currentTimeMillis(),
                        outgoing = false,
                        senderName = if (isSender) senderInfo?.name ?: "Sender" else "Viewer"
                    )
                    onChat?.invoke(msg)
                    // relay to opposite side
                    if (isSender) broadcastToViewers(message)
                    else broadcastToSenders(message)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardServer", "Parse error", e)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("DashboardServer", "WS error", ex)
    }

    override fun onStart() {
        Log.d("DashboardServer", "Server started on port ${port}")
        connectionLostTimeout = 30
    }

    fun sendChatToSenders(text: String) {
        val json = JSONObject().put("type", "chat").put("text", text)
            .put("ts", System.currentTimeMillis()).toString()
        broadcastToSenders(json)
    }

    private fun broadcastToViewers(msg: String) {
        viewers.keys.filter { it.isOpen }.forEach { it.send(msg) }
    }

    private fun broadcastToSenders(msg: String) {
        senders.keys.filter { it.isOpen }.forEach { it.send(msg) }
    }

    private fun broadcastSenderList() {
        val json = buildSendersJson()
        viewers.keys.filter { it.isOpen }.forEach { it.send(json) }
        onSendersChanged?.invoke(senders.values.toList())
    }

    private fun buildSendersJson(): String {
        val arr = org.json.JSONArray()
        senders.values.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("ts", s.connectedAt))
        }
        return JSONObject().put("type", "senders").put("list", arr).toString()
    }

    private fun parseParam(uri: String, key: String): String? {
        val query = uri.substringAfter("?", "")
        return query.split("&").firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")?.ifBlank { null }
    }
}
