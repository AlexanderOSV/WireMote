package com.myremote.app.network

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
)

object Discovery {
    private const val discoveryPort = 39393

    fun listen(context: Context, timeoutMs: Int = 3000): List<DiscoveredDevice> {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("WireMoteDiscovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        return try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(discoveryPort))
                socket.soTimeout = timeoutMs
                val probe = """{"service":"my-remote","version":1,"action":"discover"}"""
                val probeBytes = probe.toByteArray(Charsets.UTF_8)
                socket.send(
                    DatagramPacket(
                        probeBytes,
                        probeBytes.size,
                        InetSocketAddress("255.255.255.255", discoveryPort),
                    )
                )
                val devices = linkedMapOf<String, DiscoveredDevice>()
                val buffer = ByteArray(4096)
                while (true) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val json = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                        if (json.optString("service") != "my-remote" || json.optInt("version") != 1) continue
                        val host = json.optString("host").ifBlank { packet.address.hostAddress.orEmpty() }
                        val port = json.optInt("port", 39394)
                        val name = json.optString("name").ifBlank { host }
                        if (host.isNotBlank() && port in 1..65535) {
                            devices["$host:$port"] = DiscoveredDevice(name, host, port)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    } catch (_: Exception) {
                        continue
                    }
                }
                devices.values.toList()
            }
        } finally {
            if (lock.isHeld) lock.release()
        }
    }
}
