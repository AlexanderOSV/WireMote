package com.myremote.app.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    fun wake(macAddress: String, broadcastAddress: String = "255.255.255.255") {
        val mac = macAddress.replace(":", "-").split("-").map { it.toInt(16).toByte() }
        require(mac.size == 6) { "MAC address must contain six octets" }
        val packet = ByteArray(102)
        java.util.Arrays.fill(packet, 0, 6, 0xff.toByte())
        repeat(16) { index -> mac.forEachIndexed { offset, value -> packet[6 + index * 6 + offset] = value } }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(broadcastAddress), 9))
        }
    }
}
