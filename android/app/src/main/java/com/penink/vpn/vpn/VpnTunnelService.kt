package com.penink.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.penink.vpn.MainActivity
import com.penink.vpn.R
import com.penink.vpn.data.NodeRepository
import com.penink.vpn.data.VpnNode
import com.penink.vpn.vpn.tunnel.TunnelWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 建立 TUN 介面，攔截裝置的 IP 封包，
 * 透過受保護的 WebSocket(TLS/443) 隧道送到家庭伺服器。
 */
class VpnTunnelService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.penink.vpn.action.CONNECT"
        const val ACTION_DISCONNECT = "com.penink.vpn.action.DISCONNECT"
        const val EXTRA_NODE_ID = "com.penink.vpn.extra.NODE_ID"

        private const val CHANNEL_ID = "penink_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TUN_ADDRESS = "10.27.0.2"
        private const val TUN_PREFIX = 24
        private const val MTU = 1380
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tunFd: ParcelFileDescriptor? = null
    private var tunInput: InputStream? = null
    private var tunOutput: OutputStream? = null
    private var webSocket: TunnelWebSocket? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> stopVpn()
            ACTION_CONNECT -> startVpn(intent.getLongExtra(EXTRA_NODE_ID, -1L))
            else -> stopVpn()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        VpnState.isRunning.value = false
        VpnState.status.value = "已斷線"
        runCatching { webSocket?.close() }
        runCatching { tunFd?.close() }
        scope.cancel()
        super.onDestroy()
    }

    private fun startVpn(nodeId: Long) {
        if (running.get()) return
        VpnState.status.value = "連線中…"
        startForegroundCompat()

        scope.launch {
            try {
                val node = withContext(Dispatchers.IO) {
                    val repository = NodeRepository(applicationContext)
                    if (nodeId >= 0) {
                        repository.getById(nodeId) ?: repository.getSelected()
                    } else {
                        repository.getSelected()
                    }
                } ?: throw IOException("找不到節點，請先在「節點」頁面新增並選擇")

                runTunnel(node)
            } catch (e: Exception) {
                running.set(false)
                VpnState.isRunning.value = false
                VpnState.status.value = "連線失敗：${e.message}"
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun runTunnel(node: VpnNode) = withContext(Dispatchers.IO) {
        val ws = TunnelWebSocket(
            vpnService = this@VpnTunnelService,
            node = node,
            onBinary = { packet -> runCatching { tunOutput?.write(packet) } },
            onTerminated = { stopVpn() }
        )
        ws.connect()

        val fd = Builder()
            .setSession("penink-vpn")
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, TUN_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .establish()
            ?: throw IOException("使用者未允許 VPN 權限")

        tunFd = fd
        tunInput = ParcelFileDescriptor.AutoCloseInputStream(fd)
        tunOutput = ParcelFileDescriptor.AutoCloseOutputStream(fd)
        webSocket = ws

        running.set(true)
        VpnState.isRunning.value = true
        VpnState.status.value = "已連線：${node.name}"
        updateNotification("已連線 ${node.name}")

        val buffer = ByteArray(MTU)
        try {
            while (running.get() && ws.isLive) {
                val n = tunInput?.read(buffer) ?: -1
                if (n < 0) break
                ws.sendBinary(buffer.copyOfRange(0, n))
            }
        } finally {
            stopVpn()
        }
    }

    @Synchronized
    private fun stopVpn() {
        if (!running.getAndSet(false)) return
        VpnState.isRunning.value = false
        VpnState.status.value = "已斷線"
        runCatching { webSocket?.close() }
        runCatching { tunFd?.close() }
        webSocket = null
        tunFd = null
        tunInput = null
        tunOutput = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN 隧道",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "penink VPN 的連線狀態" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = buildNotification("正在連線…", "penink VPN")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, "penink VPN"))
    }

    private fun buildNotification(text: String, title: String): Notification {
        val disconnectIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VpnTunnelService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "斷線", disconnectIntent)
            .build()
    }
}