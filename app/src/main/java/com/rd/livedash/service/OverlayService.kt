package com.rd.livedash.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rd.livedash.MainActivity
import com.rd.livedash.data.ChatMessage
import com.rd.livedash.network.SenderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class OverlayService : LifecycleService() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_SERVER_IP = "serverIp"
        const val EXTRA_SERVER_PORT = "serverPort"
        const val EXTRA_SENDER_NAME = "senderName"
        private const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "livedash_overlay"
    }

    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var chatPanelView: View? = null
    private var chatPanelVisible = false
    private var chatMessagesContainer: LinearLayout? = null
    private var chatScrollView: ScrollView? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var encoderSurface: Surface? = null
    private var client: SenderClient? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiveDash:OverlayService")
        wakeLock?.acquire(10 * 60 * 60 * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { cleanup(); stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                val serverIp = intent.getStringExtra(EXTRA_SERVER_IP) ?: return START_NOT_STICKY
                val serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 8765)
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "Android Sender"
                setupClient(serverIp, serverPort, senderName)
                if (data != null) setupCapture(resultCode, data)
                showOverlay()
                observeConnection()
                observeChat()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        cleanup()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun observeConnection() {
        var wasConnected = false
        lifecycleScope.launch {
            OverlayState.connected.collect { connected ->
                if (connected) {
                    wasConnected = true
                } else if (wasConnected) {
                    withContext(Dispatchers.Main) {
                        cleanup()
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun observeChat() {
        lifecycleScope.launch {
            OverlayState.chatMessages.collect { messages ->
                withContext(Dispatchers.Main) {
                    refreshChatPanel(messages)
                }
            }
        }
    }

    private fun setupClient(ip: String, port: Int, name: String) {
        val c = SenderClient(
            serverIp = ip, port = port, senderName = name,
            onConnected = { OverlayState.connected.value = true },
            onDisconnected = { OverlayState.connected.value = false },
            onChatReceived = { text ->
                OverlayState.addChat(ChatMessage(UUID.randomUUID().toString(), text, System.currentTimeMillis(), false, "Dashboard"))
            }
        )
        c.connect()
        client = c
        OverlayState.client = c
    }

    private fun setupCapture(resultCode: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mgr.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        val sw = metrics.widthPixels
        val sh = metrics.heightPixels
        val dpi = metrics.densityDpi

        // Scale to max 1280px wide, keep aspect ratio, ensure even dimensions
        val targetW = (if (sw > 1280) 1280 else sw).let { if (it % 2 != 0) it - 1 else it }
        val targetH = (sh * targetW / sw).let { if (it % 2 != 0) it - 1 else it }

        // Android 14+: must register callback before createVirtualDisplay
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d("OverlayService", "MediaProjection stopped by system")
                OverlayState.connected.value = false
            }
        }, Handler(Looper.getMainLooper()))

        // H.264 encoder — screen renders directly into encoder's input Surface
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)   // 2 Mbps
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)   // keyframe every 2s
        }

        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // Surface-based encoder: input buffers are not used
            }
            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.d("OverlayService", "Encoder format changed: $format")
            }
            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e("OverlayService", "Encoder error", e)
            }
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                if (info.size > 0) {
                    try {
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null) {
                            val bytes = ByteArray(info.size)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.get(bytes)
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            client?.sendVideoFrame(b64, info.flags)
                        }
                    } catch (e: Exception) {
                        Log.e("OverlayService", "Frame send error", e)
                    }
                }
                codec.releaseOutputBuffer(index, false)
            }
        })

        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()

        videoEncoder = enc
        encoderSurface = surface

        virtualDisplay = mp.createVirtualDisplay(
            "LiveDashCapture", targetW, targetH, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )
        mediaProjection = mp
    }

    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val root = FrameLayout(this)
        overlayRoot = root

        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24; y = 80
        }

        val chatBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_dialog_email)
            setBackgroundColor(Color.parseColor("#CC6366F1"))
            setPadding(24, 24, 24, 24)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Chat"
            setOnClickListener { toggleChatPanel() }
        }
        root.addView(chatBtn, FrameLayout.LayoutParams(120, 120))
        wm.addView(root, params)
    }

    private fun toggleChatPanel() {
        if (chatPanelVisible) hideChatPanel() else showChatPanel()
    }

    private fun showChatPanel() {
        if (chatPanelVisible) return
        val wm = windowManager ?: return

        val metrics = resources.displayMetrics
        val panelWidth = (metrics.widthPixels * 0.88).toInt()
        val panelHeight = (metrics.heightPixels * 0.42).toInt()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F01E1E2E"))
        }

        val scrollView = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val msgContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 8)
        }
        scrollView.addView(msgContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 12)
            gravity = Gravity.CENTER_VERTICAL
        }
        val editText = EditText(this).apply {
            hint = "Message..."
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC2A2A3C"))
            setPadding(20, 14, 20, 14)
            textSize = 14f
            maxLines = 2
            isSingleLine = false
        }
        val sendBtn = Button(this).apply {
            text = "Send"
            setBackgroundColor(Color.parseColor("#CC6366F1"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(20, 8, 20, 8)
            setOnClickListener {
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    val msg = ChatMessage(UUID.randomUUID().toString(), text, System.currentTimeMillis(), true, "You")
                    OverlayState.addChat(msg)
                    client?.sendChat(text)
                    editText.text.clear()
                    refreshChatPanel(OverlayState.chatMessages.value)
                }
            }
        }
        inputRow.addView(editText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = 8 })
        panel.addView(inputRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        chatMessagesContainer = msgContainer
        chatScrollView = scrollView
        chatPanelView = panel

        val panelParams = LayoutParams(
            panelWidth, panelHeight,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 220
        }

        refreshChatPanel(OverlayState.chatMessages.value)
        wm.addView(panel, panelParams)
        chatPanelVisible = true

        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        Handler(Looper.getMainLooper()).postDelayed({
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun hideChatPanel() {
        try { windowManager?.removeView(chatPanelView) } catch (_: Exception) {}
        chatPanelVisible = false
        chatPanelView = null
        chatMessagesContainer = null
        chatScrollView = null
    }

    private fun refreshChatPanel(messages: List<ChatMessage>) {
        val container = chatMessagesContainer ?: return
        val sv = chatScrollView ?: return
        container.removeAllViews()
        messages.takeLast(25).forEach { msg ->
            val tv = TextView(this).apply {
                text = "${if (msg.outgoing) "You" else msg.senderName.ifEmpty { "Dashboard" }}: ${msg.text}"
                setTextColor(if (msg.outgoing) Color.parseColor("#A5B4FC") else Color.WHITE)
                textSize = 13f
                setPadding(0, 6, 0, 6)
            }
            container.addView(tv)
        }
        sv.post { sv.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun cleanup() {
        hideChatPanel()
        try { windowManager?.removeView(overlayRoot) } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { videoEncoder?.signalEndOfInputStream() } catch (_: Exception) {}
        try { videoEncoder?.stop() } catch (_: Exception) {}
        try { videoEncoder?.release() } catch (_: Exception) {}
        try { encoderSurface?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        videoEncoder = null
        encoderSurface = null
        OverlayState.reset()
    }

    override fun onDestroy() {
        cleanup()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LiveDash Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveDash Overlay Active")
            .setContentText("Streaming live — tap chat to message dashboard")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true)
            .build()
    }
}
