package com.penink.vpn.vpn.tunnel

import android.net.VpnService
import com.penink.vpn.data.VpnNode
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ThreadLocalRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * RFC 6455 WebSocket 客戶端，做為與家庭伺服器的隧道通道。
 * 底層 socket 會先透過 [VpnService.protect] 保護，避免流量又被自己的 TUN 攔截而迴圈。
 */
class TunnelWebSocket(
    private val vpnService: VpnService,
    private val node: VpnNode,
    private val onBinary: (ByteArray) -> Unit,
    private val onTerminated: () -> Unit
) {
    private val acceptAllCertificates = true

    @Volatile
    var isLive = false
        private set

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    fun connect() {
        require(node.cleanAddress.isNotBlank()) { "Address 不能為空" }
        val hostname = node.cleanAddress
        val ip = InetAddress.getByName(hostname).hostAddress ?: hostname

        val raw = Socket()
        raw.tcpNoDelay = true
        try {
            vpnService.protect(raw)
            raw.connect(InetSocketAddress(ip, node.port), 15_000)

            val stream = if (node.usesTls) {
                sslContext().socketFactory.createSocket(raw, hostname, node.port, true) as SSLSocket
            } else {
                raw
            }
            stream.tcpNoDelay = true
            socket = stream
            input = stream.getInputStream()
            output = stream.getOutputStream()

            doHandshake()
            isLive = true
            Thread(::readLoop, "penink-ws-reader").start()
        } finally {
            if (!isLive) runCatching { raw.close() }
        }
    }

    private fun doHandshake() {
        val out = requireNotNull(output)
        val `in` = requireNotNull(input)
        val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = Base64.getEncoder().encodeToString(keyBytes)
        val request = buildString {
            append("GET /penink HTTP/1.1\r\n")
            append("Host: ${node.cleanAddress}:${node.port}\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $key\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("Authorization: Bearer ${node.password}\r\n")
            append("\r\n")
        }
        out.write(request.toByteArray(Charsets.US_ASCII))
        out.flush()

        val response = StringBuilder()
        while (!response.contains("\r\n\r\n")) {
            val c = `in`.read()
            if (c == -1) throw IOException("握手失敗：連線被伺服器關閉")
            response.append(c.toChar())
            if (response.length > 16_384) throw IOException("握手回應過長")
        }
        if (!response.startsWith("HTTP/1.1 101")) {
            throw IOException("握手失敗（檢查位址 / 連接埠 / 密鑰）：${response.take(120)}")
        }
    }

    private fun readLoop() {
        try {
            val `in` = requireNotNull(input)
            while (isLive) {
                val b0 = `in`.read()
                if (b0 == -1) throw IOException("連線已結束")
                val b1 = `in`.read()
                if (b1 == -1) throw IOException("連線已結束")

                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                var length = (b1 and 0x7F).toLong()
                when (length) {
                    126L -> length = readUInt16(`in`).toLong()
                    127L -> length = readUInt64(`in`)
                }
                if (length < 0 || length > 100_000) throw IOException("非法 frame 長度: $length")

                val maskKey = if (masked) ByteArray(4).also { readFully(`in`, it) } else null
                val payload = ByteArray(length.toInt())
                readFully(`in`, payload)
                if (maskKey != null) {
                    for (i in payload.indices) {
                        payload[i] = (
                            ((payload[i].toInt() and 0xFF) xor (maskKey[i % 4].toInt() and 0xFF))
                            ).toByte()
                    }
                }

                when (opcode) {
                    0x2 -> onBinary(payload)
                    0x8 -> { terminate(); return }
                    0x9 -> sendFrame(0xA, payload)
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            if (isLive) terminate()
        }
    }

    private fun terminate() {
        isLive = false
        onTerminated()
    }

    @Synchronized
    fun sendBinary(payload: ByteArray) = sendFrame(0x2, payload)

    private fun sendFrame(opcode: Int, payload: ByteArray) {
        val out = output ?: return
        if (!isLive) return
        val length = payload.size
        val mask = ByteArray(4).also { ThreadLocalRandom.current().nextBytes(it) }

        val header: ByteArray = when {
            length < 126 -> byteArrayOf(
                (0x80 or opcode).toByte(),
                (0x80 or length).toByte()
            )
            length <= 0xFFFF -> byteArrayOf(
                (0x80 or opcode).toByte(),
                (0x80 or 126).toByte(),
                ((length shr 8) and 0xFF).toByte(),
                (length and 0xFF).toByte()
            )
            else -> {
                val h = ByteArray(10)
                h[0] = (0x80 or opcode).toByte()
                h[1] = (0x80 or 127).toByte()
                var l = length.toLong()
                for (i in 9 downTo 2) {
                    h[i] = (l and 0xFF).toByte()
                    l = l shr 8
                }
                h
            }
        }

        val masked = payload.copyOf()
        for (i in masked.indices) {
            masked[i] = (
                ((masked[i].toInt() and 0xFF) xor (mask[i % 4].toInt() and 0xFF))
                ).toByte()
        }

        out.write(header)
        out.write(mask)
        out.write(masked)
        out.flush()
    }

    fun close() {
        runCatching { sendFrame(0x8, byteArrayOf(0x03, 0xE8.toByte())) }
        isLive = false
        runCatching { socket?.close() }
    }

    private fun sslContext(): SSLContext {
        val ctx = SSLContext.getInstance("TLS")
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        ctx.init(
            null,
            if (acceptAllCertificates) arrayOf<TrustManager>(trustAll) else null,
            SecureRandom()
        )
        return ctx
    }

    private fun readFully(`in`: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = `in`.read(buffer, offset, buffer.size - offset)
            if (n == -1) throw IOException("串流提前結束")
            offset += n
        }
    }

    private fun readUInt16(`in`: InputStream): Int {
        val a = `in`.read()
        val b = `in`.read()
        if (a == -1 || b == -1) throw IOException("串流提前結束")
        return (a shl 8) or b
    }

    private fun readUInt64(`in`: InputStream): Long {
        var result = 0L
        for (i in 0 until 8) {
            val c = `in`.read()
            if (c == -1) throw IOException("串流提前結束")
            result = (result shl 8) or c.toLong()
        }
        return result
    }
}