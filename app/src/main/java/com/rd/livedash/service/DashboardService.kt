package com.rd.livedash.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import com.rd.livedash.MainActivity
import com.rd.livedash.R
import com.rd.livedash.network.DashboardServer
import java.util.UUID

class DashboardService : LifecycleService() {

    companion object {
        const val ACTION_STOP = "com.rd.livedash.STOP_SERVER"
        const val EXTRA_PORT = "port"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "livedash_server"
    }

    private var server: DashboardServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        val port = intent?.getIntExtra(EXTRA_PORT, 8765) ?: 8765
        startForeground(NOTIF_ID, buildNotification("Server running on :$port"))
        startServer(port)
        return START_STICKY
    }

    private fun startServer(port: Int) {
        server?.stop()
        val srv = DashboardServer(port)
        srv.onScreenshot = { entry -> DashboardState.addScreenshot(entry) }
        srv.onChat = { msg -> DashboardState.addChat(msg) }
        srv.onSendersChanged = { list -> DashboardState.senders.value = list }
        srv.start()
        server = srv
        DashboardState.server = srv
        DashboardState.serverRunning.value = true
    }

    private fun stopServer() {
        try { server?.stop(500) } catch (_: Exception) {}
        DashboardState.reset()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LiveDash Server", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, DashboardService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveDash")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true)
            .build()
    }
}
